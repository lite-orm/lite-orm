package org.liteorm.test;

import org.junit.jupiter.api.Test;
import org.liteorm.LocalTransactionCoordinator;
import org.liteorm.api.ConnectionProvider;
import org.liteorm.api.TransactionContext;
import org.liteorm.api.TransactionException;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LocalTransactionCoordinatorTest {

    @Test
    void commitFailureRemainsPrimaryWhenRollbackAlsoFails() throws Exception {
        SQLException commitFailure = new SQLException("commit failed");
        SQLException rollbackFailure = new SQLException("rollback failed");
        RecordingProvider provider = provider(connection(commitFailure, rollbackFailure, null));
        LocalTransactionCoordinator coordinator = new LocalTransactionCoordinator(provider);
        TransactionContext transaction = coordinator.begin();

        TransactionException failure = assertThrows(TransactionException.class, () -> coordinator.commit(transaction));

        assertEquals(TransactionException.Type.COMMIT_FAILED, failure.getType());
        assertSame(commitFailure, failure.getCause());
        assertArrayEquals(new Throwable[]{rollbackFailure}, failure.getSuppressed());
        assertTrue(provider.released.get());
        assertFalse(coordinator.isActive());
    }

    @Test
    void rollbackFailureRemainsPrimaryWhenAutoCommitRestoreAlsoFails() throws Exception {
        SQLException rollbackFailure = new SQLException("rollback failed");
        SQLException restoreFailure = new SQLException("restore failed");
        RecordingProvider provider = provider(connection(null, rollbackFailure, restoreFailure));
        LocalTransactionCoordinator coordinator = new LocalTransactionCoordinator(provider);
        TransactionContext transaction = coordinator.begin();

        TransactionException failure = assertThrows(TransactionException.class, () -> coordinator.rollback(transaction));

        assertEquals(TransactionException.Type.ROLLBACK_FAILED, failure.getType());
        assertSame(rollbackFailure, failure.getCause());
        assertArrayEquals(new Throwable[]{restoreFailure}, failure.getSuppressed());
        assertTrue(provider.released.get());
        assertFalse(coordinator.isActive());
    }

    @Test
    void releaseFailureIsSuppressedOntoCommitFailure() throws Exception {
        SQLException commitFailure = new SQLException("commit failed");
        RuntimeException releaseFailure = new RuntimeException("release failed");
        RecordingProvider provider = provider(connection(commitFailure, null, null));
        provider.releaseFailure = releaseFailure;
        LocalTransactionCoordinator coordinator = new LocalTransactionCoordinator(provider);
        TransactionContext transaction = coordinator.begin();

        TransactionException failure = assertThrows(TransactionException.class, () -> coordinator.commit(transaction));

        assertEquals(TransactionException.Type.COMMIT_FAILED, failure.getType());
        assertSame(commitFailure, failure.getCause());
        assertArrayEquals(new Throwable[]{releaseFailure}, failure.getSuppressed());
        assertFalse(coordinator.isActive());
    }

    @Test
    void autoCommitRestoreFailureAfterSuccessfulCommitIsReportedAsCleanupFailure() throws Exception {
        SQLException restoreFailure = new SQLException("restore failed");
        RecordingProvider provider = provider(connection(null, null, restoreFailure));
        LocalTransactionCoordinator coordinator = new LocalTransactionCoordinator(provider);
        TransactionContext transaction = coordinator.begin();

        TransactionException failure = assertThrows(TransactionException.class, () -> coordinator.commit(transaction));

        assertEquals(TransactionException.Type.CLEANUP_FAILED, failure.getType());
        assertSame(restoreFailure, failure.getCause());
        assertTrue(transaction.isCommitted());
        assertTrue(provider.released.get());
        assertFalse(coordinator.isActive());
    }

    @Test
    void releaseFailureAfterSuccessfulRollbackIsReportedAsCleanupFailure() throws Exception {
        RuntimeException releaseFailure = new RuntimeException("release failed");
        RecordingProvider provider = provider(connection(null, null, null));
        provider.releaseFailure = releaseFailure;
        LocalTransactionCoordinator coordinator = new LocalTransactionCoordinator(provider);
        TransactionContext transaction = coordinator.begin();

        TransactionException failure = assertThrows(TransactionException.class, () -> coordinator.rollback(transaction));

        assertEquals(TransactionException.Type.CLEANUP_FAILED, failure.getType());
        assertSame(releaseFailure, failure.getCause());
        assertTrue(transaction.isRolledBack());
        assertFalse(coordinator.isActive());
    }

    private RecordingProvider provider(Connection connection) {
        return new RecordingProvider(connection);
    }

    private Connection connection(SQLException commitFailure, SQLException rollbackFailure, SQLException restoreFailure) {
        AtomicBoolean transactionStarted = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{Connection.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "setAutoCommit" -> {
                    boolean autoCommit = (boolean) args[0];
                    if (!autoCommit) {
                        transactionStarted.set(true);
                    } else if (restoreFailure != null && transactionStarted.get()) {
                        throw restoreFailure;
                    }
                    yield null;
                }
                case "commit" -> {
                    if (commitFailure != null) throw commitFailure;
                    yield null;
                }
                case "rollback" -> {
                    if (rollbackFailure != null) throw rollbackFailure;
                    yield null;
                }
                case "isClosed" -> false;
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) return null;
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == double.class) return 0D;
        if (returnType == float.class) return 0F;
        if (returnType == short.class) return (short) 0;
        if (returnType == byte.class) return (byte) 0;
        if (returnType == char.class) return '\0';
        return null;
    }

    private static final class RecordingProvider implements ConnectionProvider {
        private final Connection connection;
        private final AtomicBoolean released = new AtomicBoolean();
        private RuntimeException releaseFailure;

        private RecordingProvider(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Connection acquire() {
            return connection;
        }

        @Override
        public void release(Connection connection) {
            released.set(true);
            if (releaseFailure != null) throw releaseFailure;
        }
    }
}
