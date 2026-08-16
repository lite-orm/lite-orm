package org.liteorm.test.architecture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyRuntimeRemovalTest {

    private static final String[] OBSOLETE_TYPES = {
        "org.liteorm.DefaultSqlEngine",
        "org.liteorm.StandaloneSqlEngine",
        "org.liteorm.ExecutionContext",
        "org.liteorm.JdbcConnectionProvider",
        "org.liteorm.LiteOrmConfig",
        "org.liteorm.LocalTransactionCoordinator",
        "org.liteorm.api.ConnectionProvider",
        "org.liteorm.api.DataSourceKeyProvider",
        "org.liteorm.api.DataSourceRoutingException",
        "org.liteorm.api.DataSourceSelection",
        "org.liteorm.api.ExecutionInvocation",
        "org.liteorm.api.SqlExecutorRegistry",
        "org.liteorm.api.TransactionContext",
        "org.liteorm.api.TransactionCoordinator",
        "org.liteorm.api.TransactionOperations",
        "org.liteorm.annotation.ExecutorRef",
        "org.liteorm.annotation.UseDataSource",
        "org.liteorm.runtime.ConnectionProcessor",
        "org.liteorm.runtime.ExecutionProcessor",
        "org.liteorm.runtime.LoggingProcessor",
        "org.liteorm.runtime.ParameterProcessor",
        "org.liteorm.runtime.ResultProcessor",
        "org.liteorm.runtime.SlowQueryMonitorProcessor",
        "org.liteorm.runtime.SqlAuditProcessor",
        "org.liteorm.runtime.SqlProcessor"
    };

    @Test
    void obsoleteRuntimeTypesAreAbsent() {
        for (String obsoleteType : OBSOLETE_TYPES) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(obsoleteType), obsoleteType);
        }
    }
}
