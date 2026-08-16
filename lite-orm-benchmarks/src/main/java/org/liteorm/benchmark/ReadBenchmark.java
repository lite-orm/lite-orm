package org.liteorm.benchmark;

import org.apache.ibatis.session.SqlSessionFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.liteorm.JdbcAssembly;
import org.liteorm.LiteOrm;
import org.liteorm.api.ExecutionInterceptor;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class ReadBenchmark {

    @Benchmark
    public String directScalar(ReadState state) throws SQLException {
        try (var connection = state.dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT name FROM benchmark_users WHERE id = ?")) {
            statement.setLong(1, 500L);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }

    @Benchmark public String liteOrmScalar(ReadState state) { return state.liteOrm.scalar(500L); }

    @Benchmark
    public String myBatisScalar(ReadState state) {
        try (var session = state.myBatis.openSession()) {
            return session.getMapper(MyBatisBenchmarkMapper.class).scalar(500L);
        }
    }

    @Benchmark public BenchmarkRecord liteOrmRecord(ReadState state) { return state.liteOrm.record(500L); }
    @Benchmark public BenchmarkBean liteOrmBean(ReadState state) { return state.liteOrm.bean(500L); }

    @Benchmark
    public BenchmarkRecord myBatisRecord(ReadState state) {
        try (var session = state.myBatis.openSession()) {
            return session.getMapper(MyBatisBenchmarkMapper.class).record(500L);
        }
    }

    @Benchmark
    public BenchmarkBean myBatisBean(ReadState state) {
        try (var session = state.myBatis.openSession()) {
            return session.getMapper(MyBatisBenchmarkMapper.class).bean(500L);
        }
    }

    @Benchmark
    public BenchmarkRecord directRecord(ReadState state) throws SQLException {
        try (var connection = state.dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "SELECT id, name, age FROM benchmark_users WHERE id = ?")) {
            statement.setLong(1, 500L);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return new BenchmarkRecord(rows.getLong(1), rows.getString(2), rows.getInt(3));
            }
        }
    }

    @Benchmark
    public BenchmarkBean directBean(ReadState state) throws SQLException {
        try (var connection = state.dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "SELECT id, name, age FROM benchmark_users WHERE id = ?")) {
            statement.setLong(1, 500L);
            try (var rows = statement.executeQuery()) {
                rows.next();
                BenchmarkBean bean = new BenchmarkBean();
                bean.setId(rows.getLong(1));
                bean.setName(rows.getString(2));
                bean.setAge(rows.getInt(3));
                return bean;
            }
        }
    }

    @Benchmark
    public int liteOrmDynamic(ReadState state) {
        return state.liteOrm.dynamic("user-520", 30).size();
    }

    @Benchmark
    public int myBatisDynamic(ReadState state) {
        try (var session = state.myBatis.openSession()) {
            return session.getMapper(MyBatisBenchmarkMapper.class).dynamic("user-520", 30).size();
        }
    }

    @Benchmark
    public int directDynamic(ReadState state) throws SQLException {
        try (var connection = state.dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "SELECT id, name, age FROM benchmark_users WHERE name = ? AND age >= ?")) {
            statement.setString(1, "user-520");
            statement.setInt(2, 30);
            try (var rows = statement.executeQuery()) {
                int count = 0;
                while (rows.next()) {
                    rows.getLong(1);
                    rows.getString(2);
                    rows.getInt(3);
                    count++;
                }
                return count;
            }
        }
    }

    @Benchmark
    public long liteOrmCursor(ReadState state) {
        return state.liteOrm.scan(991L, cursor -> {
            long sum = 0;
            while (cursor.next()) {
                sum += cursor.current().id();
            }
            return sum;
        });
    }

    @Benchmark
    public long myBatisCursor(ReadState state) throws Exception {
        try (var session = state.myBatis.openSession();
             var cursor = session.getMapper(MyBatisBenchmarkMapper.class).scan(991L)) {
            long sum = 0;
            for (BenchmarkRecord row : cursor) {
                sum += row.id();
            }
            return sum;
        }
    }

    @Benchmark
    public long directCursor(ReadState state) throws SQLException {
        try (var connection = state.dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "SELECT id, name, age FROM benchmark_users WHERE id >= ?")) {
            statement.setLong(1, 991L);
            try (var rows = statement.executeQuery()) {
                long sum = 0;
                while (rows.next()) {
                    sum += rows.getLong(1);
                    rows.getString(2);
                    rows.getInt(3);
                }
                return sum;
            }
        }
    }

    @Benchmark
    public BenchmarkRecord liteOrmNoopInterceptor(ReadState state, Blackhole blackhole) {
        BenchmarkRecord row = state.liteOrmObserved.record(500L);
        blackhole.consume(row);
        return row;
    }

    @State(Scope.Benchmark)
    public static class ReadState {
        DataSource dataSource;
        LiteOrmBenchmarkMapper liteOrm;
        LiteOrmBenchmarkMapper liteOrmObserved;
        SqlSessionFactory myBatis;

        @Setup
        public void setup() throws SQLException {
            dataSource = BenchmarkSupport.dataSource();
            JdbcAssembly assembly = LiteOrm.jdbc(dataSource).build();
            JdbcAssembly observed = LiteOrm.jdbc(dataSource)
                .interceptors(java.util.List.of(new ExecutionInterceptor() { }))
                .build();
            liteOrm = new LiteOrmBenchmarkMapperImpl(assembly.sqlExecutor());
            liteOrmObserved = new LiteOrmBenchmarkMapperImpl(observed.sqlExecutor());
            myBatis = BenchmarkSupport.myBatis(dataSource);
        }
    }
}
