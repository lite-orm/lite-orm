package io.github.lynxus.benchmark;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import io.github.lynxus.Lynxus;

import javax.sql.DataSource;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class WriteBenchmark {

    @Benchmark
    public int directBatch(WriteState state) throws Exception {
        try (var connection = state.dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "INSERT INTO benchmark_batch (id, name) VALUES (?, ?)")) {
            for (BenchmarkRecord row : state.batchRows) {
                statement.setLong(1, row.id());
                statement.setString(2, row.name());
                statement.addBatch();
            }
            int total = 0;
            for (int count : statement.executeBatch()) {
                total += count;
            }
            return total;
        }
    }

    @Benchmark
    public int lynxusBatch(WriteState state) {
        int total = 0;
        for (int count : state.lynxus.insertBatch(state.batchRows)) {
            total += count;
        }
        return total;
    }

    @Benchmark
    public int myBatisBatch(WriteState state) {
        try (var session = state.myBatis.openSession(ExecutorType.BATCH, false)) {
            MyBatisBenchmarkMapper mapper = session.getMapper(MyBatisBenchmarkMapper.class);
            for (BenchmarkRecord row : state.batchRows) {
                mapper.insertBatchRow(row);
            }
            int total = session.flushStatements().stream()
                .flatMapToInt(result -> java.util.Arrays.stream(result.getUpdateCounts()))
                .sum();
            session.commit();
            return total;
        }
    }

    @Benchmark
    public long directGeneratedKey(WriteState state) throws Exception {
        try (var connection = state.dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "INSERT INTO benchmark_generated (name) VALUES (?)", new String[]{"id"})) {
            statement.setString(1, "generated");
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    @Benchmark public long lynxusGeneratedKey(WriteState state) { return state.lynxus.insertGenerated("generated"); }

    @Benchmark
    public long myBatisGeneratedKey(WriteState state) {
        MyBatisBenchmarkMapper.GeneratedRow row = new MyBatisBenchmarkMapper.GeneratedRow("generated");
        try (var session = state.myBatis.openSession(false)) {
            session.getMapper(MyBatisBenchmarkMapper.class).insertGenerated(row);
            session.commit();
            return row.getId();
        }
    }

    @State(Scope.Thread)
    public static class WriteState {
        DataSource dataSource;
        LynxusBenchmarkMapper lynxus;
        SqlSessionFactory myBatis;
        List<BenchmarkRecord> batchRows;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            dataSource = BenchmarkSupport.dataSource();
            lynxus = new LynxusBenchmarkMapperImpl(Lynxus.jdbc(dataSource).build().sqlExecutor());
            myBatis = BenchmarkSupport.myBatis(dataSource);
            batchRows = BenchmarkSupport.batchRows();
        }

        @Setup(Level.Invocation)
        public void clearTables() throws Exception {
            BenchmarkSupport.clear(dataSource, "benchmark_batch");
            BenchmarkSupport.clear(dataSource, "benchmark_generated");
        }
    }
}
