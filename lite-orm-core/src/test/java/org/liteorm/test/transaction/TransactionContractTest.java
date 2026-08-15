package org.liteorm.test.transaction;

import org.junit.jupiter.api.Test;
import org.liteorm.api.LiteOrmException;
import org.liteorm.api.Transaction;
import org.liteorm.api.TransactionCallback;
import org.liteorm.api.TransactionException;
import org.liteorm.api.TransactionFactory;
import org.liteorm.api.TransactionalExecutor;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionContractTest {

    @Test
    void transactionOwnsConnectionAndBoundaryOperations() {
        Transaction transaction = new Transaction() {
            @Override public Connection getConnection() { return null; }
            @Override public void commit() { }
            @Override public void rollback() { }
            @Override public void close() { }
        };

        assertTrue(transaction instanceof AutoCloseable);
        assertNull(transaction.getConnection());
        assertNull(transaction.getTimeoutSeconds());
    }

    @Test
    void factoryAndCallbackExposeOnlyTransactionRoles() {
        Transaction transaction = transaction();
        TransactionFactory factory = () -> transaction;
        TransactionCallback<String> callback = current -> current == transaction ? "joined" : "wrong";
        TransactionalExecutor executor = new TransactionalExecutor() {
            @Override
            public <T> T execute(TransactionCallback<T> work) {
                return work.execute(factory.openTransaction());
            }
        };

        assertSame(transaction, factory.openTransaction());
        assertEquals("joined", executor.execute(callback));
    }

    @Test
    void transactionFailuresAreUncheckedLiteOrmExceptions() {
        assertTrue(LiteOrmException.class.isAssignableFrom(TransactionException.class));
        assertTrue(RuntimeException.class.isAssignableFrom(TransactionException.class));
    }

    private Transaction transaction() {
        return new Transaction() {
            @Override public Connection getConnection() { return null; }
            @Override public void commit() { }
            @Override public void rollback() { }
            @Override public void close() { }
        };
    }
}
