package org.liteorm.test;

import org.junit.jupiter.api.Test;
import org.liteorm.DefaultTransactionManager;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.SqlTask;
import org.liteorm.api.TransactionException;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionPlanContractTest {

    @Test
    void sqlTaskImplementsExecutionPlanMetadata() {
        SqlTask task = new SqlTask(
            "org.liteorm.test.UserMapper.findById",
            "SELECT * FROM users WHERE id = ?",
            new Object[]{1L},
            SqlTask.SqlType.SELECT,
            false,
            "org.liteorm.test.User",
            ExecutionPlan.SqlSource.ANNOTATION
        );

        assertEquals("org.liteorm.test.UserMapper.findById", task.getStatementId());
        assertEquals(ExecutionPlan.StatementType.SELECT, task.getStatementType());
        assertEquals(ExecutionPlan.SqlSource.ANNOTATION, task.getSourceType());
        assertEquals("org.liteorm.test.User", task.getResultType());
    }

    @Test
    void nestedLocalTransactionsAreRejected() throws Exception {
        Connection connection = connectionProxy(new AtomicBoolean(), new AtomicBoolean());

        DataSource dataSource = (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{DataSource.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getConnection" -> connection;
                default -> defaultValue(method.getReturnType());
            }
        );

        DefaultTransactionManager manager = new DefaultTransactionManager(new org.liteorm.SimpleConnectionManager(dataSource));
        var tx = manager.begin();
        try {
            TransactionException nested = assertThrows(TransactionException.class, manager::begin);
            assertEquals(TransactionException.Type.BEGIN_FAILED, nested.getType());
        } finally {
            manager.rollback(tx);
        }
    }

    @Test
    void rollbackUpdatesTransactionStateAndInvokesConnectionRollback() throws Exception {
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean commitCalled = new AtomicBoolean(false);
        Connection connection = connectionProxy(rollbackCalled, commitCalled);

        DataSource dataSource = (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{DataSource.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getConnection" -> connection;
                default -> defaultValue(method.getReturnType());
            }
        );

        DefaultTransactionManager manager = new DefaultTransactionManager(new org.liteorm.SimpleConnectionManager(dataSource));
        var tx = manager.begin();
        manager.rollback(tx);

        assertTrue(rollbackCalled.get());
        assertFalse(commitCalled.get());
        assertTrue(tx.isRolledBack());
        assertFalse(manager.isInTransaction());
    }

    private Connection connectionProxy(AtomicBoolean rollbackCalled, AtomicBoolean commitCalled) {
        return (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{Connection.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "setAutoCommit", "close" -> null;
                case "commit" -> {
                    commitCalled.set(true);
                    yield null;
                }
                case "rollback" -> {
                    rollbackCalled.set(true);
                    yield null;
                }
                case "getAutoCommit" -> false;
                case "isClosed" -> false;
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
