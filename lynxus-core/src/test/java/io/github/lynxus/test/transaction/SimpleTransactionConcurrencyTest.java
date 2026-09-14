package io.github.lynxus.test.transaction;

import org.junit.jupiter.api.Test;
import io.github.lynxus.api.ConnectionHandle;
import io.github.lynxus.transaction.SimpleConnectionHandleFactory;
import io.github.lynxus.transaction.SimpleTransactionalExecutor;

import java.sql.Connection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleTransactionConcurrencyTest {

    @Test
    void nestedAndConcurrentCallbacksKeepIndependentThreadBindings() throws Exception {
        SimpleTransactionTest.TrackingDataSource dataSource = new SimpleTransactionTest.TrackingDataSource();
        SimpleConnectionHandleFactory factory = new SimpleConnectionHandleFactory(dataSource);
        SimpleTransactionalExecutor transactions = new SimpleTransactionalExecutor(factory);
        Set<Connection> threadConnections = ConcurrentHashMap.newKeySet();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int index = 0; index < 2; index++) {
                executor.submit(() -> transactions.execute(() -> {
                    Connection connection;
                    try (ConnectionHandle root = factory.openHandle()) {
                        connection = root.connection();
                    }
                    threadConnections.add(connection);
                    transactions.execute(() -> {
                        try (ConnectionHandle nested = factory.openHandle()) {
                            assertSame(connection, nested.connection());
                        }
                        try (ConnectionHandle mapperCall = factory.openHandle()) {
                            assertSame(connection, mapperCall.connection());
                        }
                        return null;
                    });
                    ready.countDown();
                    try {
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(interrupted);
                    }
                    return null;
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            release.countDown();
        }

        assertEquals(2, threadConnections.size());
        assertEquals(2, dataSource.connectionCount);
        assertEquals(2, dataSource.closeCount);
    }
}
