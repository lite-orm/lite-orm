package org.liteorm.test;

import org.junit.jupiter.api.Test;
import org.liteorm.JdbcAssembly;
import org.liteorm.LiteOrm;
import org.liteorm.api.ConfigurationException;
import org.liteorm.api.ExecutionInterceptor;
import org.liteorm.api.ExecutionOutcome;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.SqlExecutionException;
import org.liteorm.api.SqlResult;
import org.liteorm.api.TransactionException;
import org.liteorm.api.TransactionFactory;
import org.liteorm.transaction.SimpleTransactionDomainGuard;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiteOrmAssemblyTest {

    @Test
    void assemblesExplicitExecutorRolesWithOrderedInterceptors() {
        TrackingDataSource dataSource = new TrackingDataSource();
        List<String> events = new ArrayList<>();
        ExecutionInterceptor first = interceptor("first", events);
        ExecutionInterceptor second = interceptor("second", events);

        JdbcAssembly assembly = LiteOrm.jdbc(dataSource)
            .interceptors(List.of(first, second))
            .build();

        SqlResult result = assembly.sqlExecutor().execute(selectPlan("test.Mapper.find"));

        assertEquals(7L, result.getQueryResults().getFirst()[0]);
        assertEquals(List.of(
            "first.before", "second.before",
            "second.success", "first.success"
        ), events);
        assertInstanceOf(org.liteorm.jdbc.JdbcSqlExecutor.class, assembly.sqlExecutor());
        assertInstanceOf(
            org.liteorm.transaction.SimpleTransactionalExecutor.class,
            assembly.transactionalExecutor()
        );
    }

    @Test
    void executorJoinsTransactionFromTheSameAssembly() {
        TrackingDataSource dataSource = new TrackingDataSource();
        JdbcAssembly assembly = LiteOrm.jdbc(dataSource).build();

        assembly.transactionalExecutor().execute(transaction -> {
            assembly.sqlExecutor().execute(selectPlan("test.Mapper.first"));
            assembly.sqlExecutor().execute(selectPlan("test.Mapper.second"));
            return null;
        });

        assertEquals(1, dataSource.connectionCount.get());
        assertEquals(1, dataSource.commitCount.get());
        assertEquals(1, dataSource.closeCount.get());
    }

    @Test
    void sharedGuardRejectsCrossDomainExecutionBeforeSecondConnection() {
        TrackingDataSource usersDataSource = new TrackingDataSource();
        TrackingDataSource ordersDataSource = new TrackingDataSource();
        SimpleTransactionDomainGuard guard = new SimpleTransactionDomainGuard();
        JdbcAssembly users = LiteOrm.jdbc(usersDataSource)
            .domain("users")
            .domainGuard(guard)
            .build();
        JdbcAssembly orders = LiteOrm.jdbc(ordersDataSource)
            .domain("orders")
            .domainGuard(guard)
            .build();

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            users.transactionalExecutor().execute(transaction -> {
                users.sqlExecutor().execute(selectPlan("users.Mapper.find"));
                orders.sqlExecutor().execute(selectPlan("orders.Mapper.find"));
                return null;
            })
        );

        TransactionException cause = assertInstanceOf(TransactionException.class, failure.getCause());
        assertEquals(TransactionException.Type.DOMAIN_MISMATCH, cause.getType());
        assertEquals(1, usersDataSource.connectionCount.get());
        assertEquals(0, ordersDataSource.connectionCount.get());
    }

    @Test
    void rejectsInvalidAssemblyConfiguration() {
        TrackingDataSource dataSource = new TrackingDataSource();
        ExecutionInterceptor interceptor = new ExecutionInterceptor() {
        };

        assertConfigurationFailure("dataSource", () -> LiteOrm.jdbc(null));
        assertConfigurationFailure("domain", () -> LiteOrm.jdbc(dataSource).domain(" "));
        assertConfigurationFailure("interceptors", () ->
            LiteOrm.jdbc(dataSource).interceptors(null));
        assertConfigurationFailure("interceptors[1]", () ->
            LiteOrm.jdbc(dataSource).interceptors(Arrays.asList(interceptor, null)));
        assertConfigurationFailure("duplicate", () ->
            LiteOrm.jdbc(dataSource).interceptors(List.of(interceptor, interceptor)));
    }

    @Test
    void assemblesSqlExecutorFromHostTransactionFactory() {
        TransactionFactory transactionFactory = () -> null;

        assertInstanceOf(
            org.liteorm.jdbc.JdbcSqlExecutor.class,
            JdbcAssembly.sqlExecutor(transactionFactory, List.of())
        );
    }

    private void assertConfigurationFailure(String messagePart, Runnable action) {
        ConfigurationException failure = assertThrows(ConfigurationException.class, action::run);
        assertTrue(failure.getMessage().contains(messagePart), failure.getMessage());
    }

    private ExecutionPlan selectPlan(String statementId) {
        return new ExecutionPlan(
            statementId,
            "SELECT 7",
            new Object[0],
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.GENERATED
        );
    }

    private ExecutionInterceptor interceptor(String name, List<String> events) {
        return new ExecutionInterceptor() {
            @Override
            public void beforeExecution(ExecutionPlan plan) {
                events.add(name + ".before");
            }

            @Override
            public void afterSuccess(ExecutionOutcome outcome) {
                events.add(name + ".success");
            }
        };
    }

    private static final class TrackingDataSource implements DataSource {

        private final AtomicInteger connectionCount = new AtomicInteger();
        private final AtomicInteger commitCount = new AtomicInteger();
        private final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public Connection getConnection() {
            connectionCount.incrementAndGet();
            boolean[] autoCommit = {true};
            return proxy(Connection.class, (method, arguments) -> switch (method) {
                case "getAutoCommit" -> autoCommit[0];
                case "setAutoCommit" -> {
                    autoCommit[0] = (boolean) arguments[0];
                    yield null;
                }
                case "prepareStatement" -> statement();
                case "commit" -> {
                    commitCount.incrementAndGet();
                    yield null;
                }
                case "rollback" -> null;
                case "close" -> {
                    closeCount.incrementAndGet();
                    yield null;
                }
                case "isClosed" -> false;
                default -> null;
            });
        }

        private PreparedStatement statement() {
            return proxy(PreparedStatement.class, (method, arguments) -> switch (method) {
                case "executeQuery" -> resultSet();
                case "close" -> null;
                default -> null;
            });
        }

        private ResultSet resultSet() {
            int[] cursor = {-1};
            return proxy(ResultSet.class, (method, arguments) -> switch (method) {
                case "next" -> ++cursor[0] == 0;
                case "getObject" -> 7L;
                case "getMetaData" -> proxy(java.sql.ResultSetMetaData.class,
                    (metadataMethod, metadataArguments) ->
                        metadataMethod.equals("getColumnCount") ? 1 : null);
                case "close" -> null;
                default -> null;
            });
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
        @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
            LiteOrmAssemblyTest.class.getClassLoader(),
            new Class<?>[]{type},
            (proxy, method, arguments) -> invocation.invoke(
                method.getName(), arguments == null ? new Object[0] : arguments)
        );
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] arguments) throws Throwable;
    }
}
