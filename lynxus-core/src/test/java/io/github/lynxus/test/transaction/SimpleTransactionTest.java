package io.github.lynxus.test.transaction;

import org.junit.jupiter.api.Test;
import io.github.lynxus.api.ConnectionHandle;
import io.github.lynxus.api.TransactionException;
import io.github.lynxus.transaction.SimpleConnectionHandleFactory;
import io.github.lynxus.transaction.SimpleTransactionalExecutor;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleTransactionTest {

    @Test
    void successfulBoundaryLazilyCommitsRestoresAndCloses() {
        TrackingDataSource dataSource = new TrackingDataSource();
        SimpleConnectionHandleFactory factory = new SimpleConnectionHandleFactory(dataSource);
        SimpleTransactionalExecutor transactions = new SimpleTransactionalExecutor(factory);

        String result = transactions.execute(() -> {
            assertEquals(List.of(), dataSource.events);
            acquire(factory);
            return "done";
        });

        assertEquals("done", result);
        assertEquals(List.of(
            "getConnection:1",
            "1.setAutoCommit:false",
            "1.commit",
            "1.setAutoCommit:true",
            "1.close"
        ), dataSource.events);
    }

    @Test
    void callbackFailureRollsBackRestoresAndCloses() {
        TrackingDataSource dataSource = new TrackingDataSource();
        SimpleConnectionHandleFactory factory = new SimpleConnectionHandleFactory(dataSource);
        SimpleTransactionalExecutor transactions = new SimpleTransactionalExecutor(factory);
        IllegalStateException expected = new IllegalStateException("boom");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
            transactions.execute(() -> {
                acquire(factory);
                throw expected;
            })
        );

        assertSame(expected, failure);
        assertEquals(List.of(
            "getConnection:1",
            "1.setAutoCommit:false",
            "1.rollback",
            "1.setAutoCommit:true",
            "1.close"
        ), dataSource.events);
    }

    @Test
    void commitFailureRollsBackAndStillReleasesTheConnection() {
        TrackingDataSource dataSource = new TrackingDataSource();
        dataSource.commitFailure = new SQLException("commit failed");
        SimpleConnectionHandleFactory factory = new SimpleConnectionHandleFactory(dataSource);
        SimpleTransactionalExecutor transactions = new SimpleTransactionalExecutor(factory);

        RuntimeException failure = assertThrows(RuntimeException.class, () ->
            transactions.execute(() -> {
                acquire(factory);
                return null;
            })
        );

        assertTrue(failure.getMessage().contains("commit failed"));
        assertEquals(List.of(
            "getConnection:1",
            "1.setAutoCommit:false",
            "1.commit",
            "1.rollback",
            "1.setAutoCommit:true",
            "1.close"
        ), dataSource.events);
    }

    @Test
    void beginFailureAfterConnectionAcquisitionClosesTheConnection() {
        TrackingDataSource dataSource = new TrackingDataSource();
        dataSource.disableAutoCommitFailure = new SQLException("cannot disable auto-commit");
        SimpleConnectionHandleFactory factory = new SimpleConnectionHandleFactory(dataSource);
        SimpleTransactionalExecutor transactions = new SimpleTransactionalExecutor(factory);

        RuntimeException failure = assertThrows(RuntimeException.class, () ->
            transactions.execute(() -> {
                acquire(factory);
                return null;
            })
        );

        assertTrue(failure.getMessage().contains("cannot disable auto-commit"));
        assertEquals(List.of("getConnection:1", "1.setAutoCommit:false", "1.close"), dataSource.events);
    }

    @Test
    void mapperExecutionHandlesJoinTheBoundTransaction() {
        TrackingDataSource dataSource = new TrackingDataSource();
        SimpleConnectionHandleFactory factory = new SimpleConnectionHandleFactory(dataSource);
        SimpleTransactionalExecutor transactions = new SimpleTransactionalExecutor(factory);

        transactions.execute(() -> {
            Connection connection;
            try (ConnectionHandle root = factory.openHandle()) {
                connection = root.connection();
            }
            try (ConnectionHandle firstCall = factory.openHandle();
                 ConnectionHandle secondCall = factory.openHandle()) {
                assertSame(connection, firstCall.connection());
                assertSame(connection, secondCall.connection());
            }
            assertEquals(1, dataSource.connectionCount);
            return null;
        });

        assertEquals(1, dataSource.connectionCount);
        assertEquals(1, dataSource.closeCount);
    }

    @Test
    void nestedCallbackSuccessCommitsTheRootOnce() {
        TrackingDataSource dataSource = new TrackingDataSource();
        SimpleConnectionHandleFactory factory = new SimpleConnectionHandleFactory(dataSource);
        SimpleTransactionalExecutor transactions = new SimpleTransactionalExecutor(factory);

        String result = transactions.execute(() -> {
            acquire(factory);
            return transactions.execute(() -> "done");
        });

        assertEquals("done", result);
        assertEquals(List.of(
            "getConnection:1",
            "1.setAutoCommit:false",
            "1.commit",
            "1.setAutoCommit:true",
            "1.close"
        ), dataSource.events);
    }

    @Test
    void caughtNestedFailureMarksTheRootRollbackOnly() {
        TrackingDataSource dataSource = new TrackingDataSource();
        SimpleConnectionHandleFactory factory = new SimpleConnectionHandleFactory(dataSource);
        SimpleTransactionalExecutor transactions = new SimpleTransactionalExecutor(factory);

        TransactionException failure = assertThrows(TransactionException.class, () ->
            transactions.execute(() -> {
                acquire(factory);
                try {
                    transactions.execute(() -> {
                        throw new IllegalStateException("nested failed");
                    });
                } catch (IllegalStateException expected) {
                    assertEquals("nested failed", expected.getMessage());
                }
                return null;
            })
        );

        assertEquals(TransactionException.Type.ROLLBACK_ONLY, failure.getType());
        assertEquals(List.of(
            "getConnection:1",
            "1.setAutoCommit:false",
            "1.rollback",
            "1.setAutoCommit:true",
            "1.close"
        ), dataSource.events);
    }

    @Test
    void executionOutsideBoundaryUsesIndependentAutoCommitHandle() {
        TrackingDataSource dataSource = new TrackingDataSource();
        SimpleConnectionHandleFactory factory = new SimpleConnectionHandleFactory(dataSource);

        try (ConnectionHandle connectionHandle = factory.openHandle()) {
            connectionHandle.connection();
        }

        assertEquals(List.of("getConnection:1", "1.close"), dataSource.events);
    }

    @Test
    void cleanupFailuresAreSuppressedOnTheCallbackFailure() {
        TrackingDataSource dataSource = new TrackingDataSource();
        dataSource.rollbackFailure = new SQLException("rollback failed");
        dataSource.closeFailure = new SQLException("close failed");
        SimpleConnectionHandleFactory factory = new SimpleConnectionHandleFactory(dataSource);
        SimpleTransactionalExecutor transactions = new SimpleTransactionalExecutor(factory);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
            transactions.execute(() -> {
                acquire(factory);
                throw new IllegalArgumentException("callback failed");
            })
        );

        assertEquals("callback failed", failure.getMessage());
        assertEquals(2, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("rollback failed"));
        assertTrue(failure.getSuppressed()[1].getMessage().contains("close failed"));
    }

    private void acquire(SimpleConnectionHandleFactory factory) {
        try (ConnectionHandle connectionHandle = factory.openHandle()) {
            connectionHandle.connection();
        }
    }

    static final class TrackingDataSource implements DataSource {
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger connectionCounter = new AtomicInteger();
        final AtomicInteger closeCounter = new AtomicInteger();
        int connectionCount;
        int closeCount;
        SQLException commitFailure;
        SQLException disableAutoCommitFailure;
        SQLException rollbackFailure;
        SQLException closeFailure;

        @Override
        public synchronized Connection getConnection() {
            int id = connectionCounter.incrementAndGet();
            connectionCount = connectionCounter.get();
            events.add("getConnection:" + id);
            boolean[] autoCommit = {true};
            return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAutoCommit" -> autoCommit[0];
                    case "setAutoCommit" -> {
                        events.add(id + ".setAutoCommit:" + args[0]);
                        if (!(boolean) args[0] && disableAutoCommitFailure != null) {
                            throw disableAutoCommitFailure;
                        }
                        autoCommit[0] = (boolean) args[0];
                        yield null;
                    }
                    case "commit" -> {
                        events.add(id + ".commit");
                        if (commitFailure != null) throw commitFailure;
                        yield null;
                    }
                    case "rollback" -> {
                        events.add(id + ".rollback");
                        if (rollbackFailure != null) throw rollbackFailure;
                        yield null;
                    }
                    case "close" -> {
                        events.add(id + ".close");
                        closeCount = closeCounter.incrementAndGet();
                        if (closeFailure != null) throw closeFailure;
                        yield null;
                    }
                    case "isClosed" -> false;
                    default -> primitiveDefault(method.getReturnType());
                }
            );
        }

        @Override public Connection getConnection(String username, String password) { return getConnection(); }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
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
