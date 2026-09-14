package io.github.kervix.benchmark;

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
import io.github.kervix.JdbcAssembly;
import io.github.kervix.Kervix;

import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class TransactionBenchmark {

    @Benchmark
    public String directJdbc(TransactionState state) throws Exception {
        try (var connection = state.dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "SELECT name FROM benchmark_users WHERE id = ?")) {
                statement.setLong(1, 500L);
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    String value = rows.getString(1);
                    connection.commit();
                    return value;
                }
            }
        }
    }

    @Benchmark
    public String kervix(TransactionState state) {
        return state.assembly.transactionalExecutor().execute(() -> state.kervix.scalar(500L));
    }

    @Benchmark
    public String myBatis(TransactionState state) {
        try (var session = state.myBatis.openSession(false)) {
            String value = session.getMapper(MyBatisBenchmarkMapper.class).scalar(500L);
            session.commit();
            return value;
        }
    }

    @State(Scope.Thread)
    public static class TransactionState {
        DataSource dataSource;
        JdbcAssembly assembly;
        KervixBenchmarkMapper kervix;
        SqlSessionFactory myBatis;

        @Setup
        public void setup() throws Exception {
            dataSource = BenchmarkSupport.dataSource();
            assembly = Kervix.jdbc(dataSource).build();
            kervix = new KervixBenchmarkMapperImpl(assembly.sqlExecutor());
            myBatis = BenchmarkSupport.myBatis(dataSource);
        }
    }
}
