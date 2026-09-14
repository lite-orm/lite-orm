package io.github.kervix.test.architecture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyRuntimeRemovalTest {

    private static final String[] OBSOLETE_TYPES = {
        "io.github.kervix.DefaultSqlEngine",
        "io.github.kervix.StandaloneSqlEngine",
        "io.github.kervix.ExecutionContext",
        "io.github.kervix.JdbcConnectionProvider",
        "io.github.kervix.KervixConfig",
        "io.github.kervix.LocalTransactionCoordinator",
        "io.github.kervix.api.ConnectionProvider",
        "io.github.kervix.api.DataSourceKeyProvider",
        "io.github.kervix.api.DataSourceRoutingException",
        "io.github.kervix.api.DataSourceSelection",
        "io.github.kervix.api.ExecutionInvocation",
        "io.github.kervix.api.SqlExecutorRegistry",
        "io.github.kervix.api.TransactionContext",
        "io.github.kervix.api.TransactionCoordinator",
        "io.github.kervix.api.TransactionOperations",
        "io.github.kervix.annotation.ExecutorRef",
        "io.github.kervix.annotation.UseDataSource",
        "io.github.kervix.runtime.ConnectionProcessor",
        "io.github.kervix.runtime.ExecutionProcessor",
        "io.github.kervix.runtime.LoggingProcessor",
        "io.github.kervix.runtime.ParameterProcessor",
        "io.github.kervix.runtime.ResultProcessor",
        "io.github.kervix.runtime.SlowQueryMonitorProcessor",
        "io.github.kervix.runtime.SqlAuditProcessor",
        "io.github.kervix.runtime.SqlProcessor"
    };

    @Test
    void obsoleteRuntimeTypesAreAbsent() {
        for (String obsoleteType : OBSOLETE_TYPES) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(obsoleteType), obsoleteType);
        }
    }
}
