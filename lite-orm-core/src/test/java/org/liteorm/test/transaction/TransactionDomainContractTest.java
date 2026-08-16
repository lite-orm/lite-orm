package org.liteorm.test.transaction;

import org.junit.jupiter.api.Test;
import org.liteorm.api.TransactionDomain;
import org.liteorm.api.TransactionDomainGuard;
import org.liteorm.api.TransactionException;
import org.liteorm.transaction.SimpleTransactionDomainGuard;
import org.liteorm.transaction.SimpleConnectionHandleFactory;
import org.liteorm.transaction.SimpleTransactionalExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionDomainContractTest {

    @Test
    void domainRequiresOneNonblankIdentity() {
        assertEquals("users", new TransactionDomain("users").key());
        assertThrows(IllegalArgumentException.class, () -> new TransactionDomain(" "));
    }

    @Test
    void sharedGuardRejectsAnotherDomainBeforeConnectionAcquisition() {
        SimpleTransactionTest.TrackingDataSource users = new SimpleTransactionTest.TrackingDataSource();
        SimpleTransactionTest.TrackingDataSource orders = new SimpleTransactionTest.TrackingDataSource();
        SimpleTransactionDomainGuard guard = new SimpleTransactionDomainGuard();
        SimpleConnectionHandleFactory usersFactory = factory(users, "users", guard);
        SimpleConnectionHandleFactory ordersFactory = factory(orders, "orders", guard);
        SimpleTransactionalExecutor usersTransactions = new SimpleTransactionalExecutor(usersFactory);

        TransactionException failure = assertThrows(TransactionException.class, () ->
            usersTransactions.execute(() -> {
                try (var handle = usersFactory.openHandle()) {
                    handle.connection();
                }
                ordersFactory.openHandle();
                return null;
            })
        );

        assertEquals(TransactionException.Type.DOMAIN_MISMATCH, failure.getType());
        assertEquals(1, users.connectionCount);
        assertEquals(0, orders.connectionCount);
    }

    @Test
    void independentGuardsDoNotShareThreadState() {
        SimpleTransactionTest.TrackingDataSource users = new SimpleTransactionTest.TrackingDataSource();
        SimpleTransactionTest.TrackingDataSource orders = new SimpleTransactionTest.TrackingDataSource();
        SimpleConnectionHandleFactory usersFactory = factory(users, "users", new SimpleTransactionDomainGuard());
        SimpleConnectionHandleFactory ordersFactory = factory(orders, "orders", new SimpleTransactionDomainGuard());

        new SimpleTransactionalExecutor(usersFactory).execute(() -> {
            new SimpleTransactionalExecutor(ordersFactory).execute(() -> {
                try (var handle = ordersFactory.openHandle()) {
                    handle.connection();
                }
                return null;
            });
            return null;
        });

        assertEquals(0, users.connectionCount);
        assertEquals(1, orders.connectionCount);
    }

    @Test
    void sharedGuardKeepsDifferentThreadDomainsIndependent() throws Exception {
        SimpleTransactionTest.TrackingDataSource users = new SimpleTransactionTest.TrackingDataSource();
        SimpleTransactionTest.TrackingDataSource orders = new SimpleTransactionTest.TrackingDataSource();
        SimpleTransactionDomainGuard guard = new SimpleTransactionDomainGuard();
        SimpleConnectionHandleFactory usersFactory = factory(users, "users", guard);
        SimpleConnectionHandleFactory ordersFactory = factory(orders, "orders", guard);
        SimpleTransactionalExecutor usersTransactions = new SimpleTransactionalExecutor(usersFactory);
        SimpleTransactionalExecutor ordersTransactions = new SimpleTransactionalExecutor(ordersFactory);
        CountDownLatch active = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            executor.submit(() -> usersTransactions.execute(() ->
                hold(usersFactory, active, release)));
            executor.submit(() -> ordersTransactions.execute(() ->
                hold(ordersFactory, active, release)));

            assertTrue(active.await(5, TimeUnit.SECONDS));
            release.countDown();
        }

        assertEquals(1, users.connectionCount);
        assertEquals(1, orders.connectionCount);
    }

    @Test
    void guardContractOnlyValidatesASelectedDomain() {
        TransactionDomainGuard guard = domain -> {
            if (!domain.key().equals("users")) {
                throw new IllegalStateException("wrong domain");
            }
        };

        guard.verify(new TransactionDomain("users"));
        assertEquals(1, TransactionDomainGuard.class.getDeclaredMethods().length);
    }

    private SimpleConnectionHandleFactory factory(
            SimpleTransactionTest.TrackingDataSource dataSource,
            String key,
            SimpleTransactionDomainGuard guard) {
        return new SimpleConnectionHandleFactory(dataSource, new TransactionDomain(key), guard);
    }

    private Void hold(
            SimpleConnectionHandleFactory factory,
            CountDownLatch active,
            CountDownLatch release) {
        try (var handle = factory.openHandle()) {
            handle.connection();
        }
        active.countDown();
        try {
            assertTrue(release.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(interrupted);
        }
        return null;
    }
}
