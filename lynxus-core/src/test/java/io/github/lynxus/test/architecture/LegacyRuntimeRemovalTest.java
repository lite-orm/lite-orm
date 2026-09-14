package io.github.lynxus.test.architecture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyRuntimeRemovalTest {

    private static final String[] OBSOLETE_TYPES = {
        "io.github.lynxus.DefaultSqlEngine",
        "io.github.lynxus.StandaloneSqlEngine",
        "io.github.lynxus.ExecutionContext",
        "io.github.lynxus.JdbcConnectionProvider",
        "io.github.lynxus.LynxusConfig",
        "io.github.lynxus.LocalTransactionCoordinator",
        "io.github.lynxus.api.ConnectionProvider",
        "io.github.lynxus.api.DataSourceKeyProvider",
        "io.github.lynxus.api.DataSourceRoutingException",
        "io.github.lynxus.api.DataSourceSelection",
        "io.github.lynxus.api.ExecutionInvocation",
        "io.github.lynxus.api.SqlExecutorRegistry",
        "io.github.lynxus.api.TransactionContext",
        "io.github.lynxus.api.TransactionCoordinator",
        "io.github.lynxus.api.TransactionOperations",
        "io.github.lynxus.annotation.ExecutorRef",
        "io.github.lynxus.annotation.UseDataSource",
        "io.github.lynxus.runtime.ConnectionProcessor",
        "io.github.lynxus.runtime.ExecutionProcessor",
        "io.github.lynxus.runtime.LoggingProcessor",
        "io.github.lynxus.runtime.ParameterProcessor",
        "io.github.lynxus.runtime.ResultProcessor",
        "io.github.lynxus.runtime.SlowQueryMonitorProcessor",
        "io.github.lynxus.runtime.SqlAuditProcessor",
        "io.github.lynxus.runtime.SqlProcessor"
    };

    @Test
    void obsoleteRuntimeTypesAreAbsent() {
        for (String obsoleteType : OBSOLETE_TYPES) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(obsoleteType), obsoleteType);
        }
    }
}
