package io.github.lynxus.test.transaction;

import org.junit.jupiter.api.Test;
import io.github.lynxus.api.ConnectionHandle;
import io.github.lynxus.api.ConnectionHandleFactory;
import io.github.lynxus.api.LynxusException;
import io.github.lynxus.api.TransactionCallback;
import io.github.lynxus.api.TransactionException;
import io.github.lynxus.api.TransactionalExecutor;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionContractTest {

    @Test
    void connectionHandleExposesOnlyConnectionParticipation() {
        Connection connection = null;
        ConnectionHandle handle = new ConnectionHandle() {
            @Override public Connection connection() { return connection; }
            @Override public void close() { }
        };

        Set<String> declaredMethods = Arrays.stream(ConnectionHandle.class.getDeclaredMethods())
            .map(Method::getName)
            .collect(Collectors.toSet());

        assertTrue(handle instanceof AutoCloseable);
        assertSame(connection, handle.connection());
        assertEquals(Set.of("connection", "close"), declaredMethods);
    }

    @Test
    void factoryAndCallbackKeepCompletionInsideTransactionalExecutor() {
        ConnectionHandle handle = new ConnectionHandle() {
            @Override public Connection connection() { return null; }
            @Override public void close() { }
        };
        ConnectionHandleFactory factory = () -> handle;
        TransactionCallback<String> callback = () -> "joined";
        TransactionalExecutor executor = new TransactionalExecutor() {
            @Override
            public <T> T execute(TransactionCallback<T> work) {
                return work.execute();
            }
        };

        assertSame(handle, factory.openHandle());
        assertEquals("joined", executor.execute(callback));
        assertEquals(0, TransactionCallback.class.getDeclaredMethods()[0].getParameterCount());
    }

    @Test
    void transactionFailuresAreUncheckedLynxusExceptions() {
        assertTrue(LynxusException.class.isAssignableFrom(TransactionException.class));
        assertTrue(RuntimeException.class.isAssignableFrom(TransactionException.class));
    }
}
