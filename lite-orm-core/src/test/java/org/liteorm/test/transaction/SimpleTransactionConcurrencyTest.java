package org.liteorm.test.transaction;

import org.junit.jupiter.api.Test;
import org.liteorm.api.Transaction;
import org.liteorm.transaction.SimpleTransactionFactory;
import org.liteorm.transaction.SimpleTransactionalExecutor;

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
        SimpleTransactionFactory factory = new SimpleTransactionFactory(dataSource);
        SimpleTransactionalExecutor transactions = new SimpleTransactionalExecutor(factory);
        Set<Connection> threadConnections = ConcurrentHashMap.newKeySet();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int index = 0; index < 2; index++) {
                executor.submit(() -> transactions.execute(root -> {
                    Connection connection = root.getConnection();
                    threadConnections.add(connection);
                    transactions.execute(nested -> {
                        assertSame(connection, nested.getConnection());
                        try (Transaction mapperCall = factory.openTransaction()) {
                            assertSame(connection, mapperCall.getConnection());
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
