package org.liteorm.spring.boot;

import org.junit.jupiter.api.Test;
import org.liteorm.api.ConnectionHandle;
import org.liteorm.api.TransactionException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpringTransactionTest {

    @Test
    void reusesThreadBoundConnectionWithoutClosingItEarly() {
        TrackingDataSource dataSource = new TrackingDataSource();
        SpringConnectionHandleFactory factory = new SpringConnectionHandleFactory(dataSource);
        TransactionTemplate transactions = new TransactionTemplate(
            new DataSourceTransactionManager(dataSource));

        transactions.executeWithoutResult(status -> {
            Connection springConnection = DataSourceUtils.getConnection(dataSource);
            try (ConnectionHandle connectionHandle = factory.openHandle()) {
                assertSame(springConnection, connectionHandle.connection());
                assertSame(springConnection, connectionHandle.connection());
            }
            assertEquals(0, dataSource.closeCount.get());
        });

        assertEquals(1, dataSource.connectionCount.get());
        assertEquals(1, dataSource.commitCount.get());
        assertEquals(1, dataSource.closeCount.get());
    }

    @Test
    void leavesTransactionCompletionToSpring() {
        TrackingDataSource dataSource = new TrackingDataSource();
        SpringConnectionHandleFactory factory = new SpringConnectionHandleFactory(dataSource);
        TransactionTemplate transactions = new TransactionTemplate(
            new DataSourceTransactionManager(dataSource));

        transactions.executeWithoutResult(status -> {
            try (ConnectionHandle connectionHandle = factory.openHandle()) {
                connectionHandle.connection();
                assertEquals(0, dataSource.commitCount.get());
                assertEquals(0, dataSource.rollbackCount.get());
            }
        });

        assertEquals(1, dataSource.commitCount.get());
        assertEquals(0, dataSource.rollbackCount.get());
    }

    @Test
    void releasesConnectionOutsideSpringManagedTransaction() {
        TrackingDataSource dataSource = new TrackingDataSource();
        SpringConnectionHandleFactory factory = new SpringConnectionHandleFactory(dataSource);

        try (ConnectionHandle connectionHandle = factory.openHandle()) {
            connectionHandle.connection();
        }

        assertEquals(1, dataSource.connectionCount.get());
        assertEquals(0, dataSource.commitCount.get());
        assertEquals(0, dataSource.rollbackCount.get());
        assertEquals(1, dataSource.closeCount.get());
    }

    @Test
    void rejectsDataSourceThatIsNotBoundToTheActiveSpringManagedTransaction() {
        TrackingDataSource transactionDataSource = new TrackingDataSource();
        TrackingDataSource otherDataSource = new TrackingDataSource();
        SpringConnectionHandleFactory otherFactory = new SpringConnectionHandleFactory(otherDataSource);
        TransactionTemplate transactions = new TransactionTemplate(
            new DataSourceTransactionManager(transactionDataSource));

        transactions.executeWithoutResult(status -> {
            TransactionException failure = assertThrows(TransactionException.class, () -> {
                try (ConnectionHandle connectionHandle = otherFactory.openHandle()) {
                    connectionHandle.connection();
                }
            });

            assertEquals(TransactionException.Type.DOMAIN_MISMATCH, failure.getType());
            assertEquals(0, otherDataSource.connectionCount.get());
        });
    }

    private static final class TrackingDataSource implements DataSource {

        private final AtomicInteger connectionCount = new AtomicInteger();
        private final AtomicInteger commitCount = new AtomicInteger();
        private final AtomicInteger rollbackCount = new AtomicInteger();
        private final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public Connection getConnection() {
            connectionCount.incrementAndGet();
            boolean[] autoCommit = {true};
            return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getAutoCommit" -> autoCommit[0];
                    case "setAutoCommit" -> {
                        autoCommit[0] = (boolean) arguments[0];
                        yield null;
                    }
                    case "commit" -> {
                        commitCount.incrementAndGet();
                        yield null;
                    }
                    case "rollback" -> {
                        rollbackCount.incrementAndGet();
                        yield null;
                    }
                    case "close" -> {
                        closeCount.incrementAndGet();
                        yield null;
                    }
                    case "isClosed", "isReadOnly" -> false;
                    case "getTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
                    case "unwrap" -> throw new UnsupportedOperationException();
                    case "isWrapperFor" -> false;
                    default -> primitiveDefault(method.getReturnType());
                }
            );
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

        private Object primitiveDefault(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == char.class) return '\0';
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0F;
            if (type == double.class) return 0D;
            return null;
        }
    }
}
