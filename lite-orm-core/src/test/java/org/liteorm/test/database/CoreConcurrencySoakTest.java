package org.liteorm.test.database;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.liteorm.JdbcAssembly;
import org.liteorm.LiteOrm;
import org.liteorm.test.User;
import org.liteorm.test.UserMapper;
import org.liteorm.test.UserMapperImpl;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreConcurrencySoakTest {

    private static final Duration SOAK_DURATION = Duration.ofMillis(750);
    private static final int WORKERS_PER_DATA_SOURCE = 3;

    @Test
    void generatedMappersKeepConnectionsTransactionsAndRowsInsideTheirDataSource() throws Exception {
        TrackingDataSource usersDataSource = dataSource();
        TrackingDataSource archiveDataSource = dataSource();
        JdbcAssembly usersAssembly = LiteOrm.jdbc(usersDataSource).domain("users").build();
        JdbcAssembly archiveAssembly = LiteOrm.jdbc(archiveDataSource).domain("archive").build();
        UserMapper users = new UserMapperImpl(usersAssembly.sqlExecutor());
        UserMapper archive = new UserMapperImpl(archiveAssembly.sqlExecutor());
        AtomicLong sequence = new AtomicLong();
        AtomicInteger usersOperations = new AtomicInteger();
        AtomicInteger archiveOperations = new AtomicInteger();
        int workerCount = WORKERS_PER_DATA_SOURCE * 2;
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicLong deadline = new AtomicLong();

        try (var executor = Executors.newFixedThreadPool(workerCount)) {
            List<Future<?>> futures = new ArrayList<>(workerCount);
            for (int worker = 0; worker < workerCount; worker++) {
                int workerIndex = worker;
                futures.add(executor.submit(() -> {
                    boolean usersDomain = workerIndex % 2 == 0;
                    JdbcAssembly assembly = usersDomain ? usersAssembly : archiveAssembly;
                    UserMapper mapper = usersDomain ? users : archive;
                    AtomicInteger operations = usersDomain ? usersOperations : archiveOperations;
                    String prefix = usersDomain ? "users" : "archive";
                    ready.countDown();
                    await(start);

                    do {
                        long value = sequence.incrementAndGet();
                        String name = prefix + '-' + workerIndex + '-' + value;
                        assembly.transactionalExecutor().execute(() -> {
                            assertEquals(1, mapper.insert(name, name + "@example.com", workerIndex));
                            List<User> rows = mapper.findByName(name);
                            assertEquals(1, rows.size());
                            assertEquals(name, rows.getFirst().name());
                            return null;
                        });
                        operations.incrementAndGet();
                    } while (System.nanoTime() < deadline.get());

                    assertTrue(mapper.findByName("__missing__").isEmpty());
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            deadline.set(System.nanoTime() + SOAK_DURATION.toNanos());
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }

        assertDataSourceState(usersDataSource, usersOperations.get());
        assertDataSourceState(archiveDataSource, archiveOperations.get());
        assertRows(users.findAll(), "users", usersOperations.get());
        assertRows(archive.findAll(), "archive", archiveOperations.get());
        assertEquals(0, usersDataSource.activeConnections());
        assertEquals(0, archiveDataSource.activeConnections());
        assertEquals(usersDataSource.openedConnections(), usersDataSource.closedConnections());
        assertEquals(archiveDataSource.openedConnections(), archiveDataSource.closedConnections());
        assertTrue(usersDataSource.threadViolations().isEmpty());
        assertTrue(archiveDataSource.threadViolations().isEmpty());
    }

    private void assertDataSourceState(TrackingDataSource dataSource, int transactionCount) {
        assertTrue(transactionCount > 0);
        assertEquals(transactionCount + WORKERS_PER_DATA_SOURCE, dataSource.openedConnections());
        assertEquals(dataSource.openedConnections(), dataSource.closedConnections());
        assertEquals(0, dataSource.activeConnections());
        assertTrue(dataSource.threadViolations().isEmpty());
    }

    private void assertRows(List<User> rows, String prefix, int expectedCount) {
        assertEquals(expectedCount, rows.size());
        assertEquals(expectedCount, rows.stream().map(User::name).distinct().count());
        assertTrue(rows.stream().allMatch(row -> row.name().startsWith(prefix + '-')));
    }

    private TrackingDataSource dataSource() throws SQLException {
        JdbcDataSource delegate = new JdbcDataSource();
        delegate.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (var connection = delegate.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users ("
                + "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
                + "name VARCHAR(100), email VARCHAR(100), age INTEGER)");
        }
        return new TrackingDataSource(delegate);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for soak start");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static final class TrackingDataSource implements DataSource {

        private final DataSource delegate;
        private final AtomicInteger openedConnections = new AtomicInteger();
        private final AtomicInteger closedConnections = new AtomicInteger();
        private final Set<Connection> activeConnections = ConcurrentHashMap.newKeySet();
        private final Set<String> threadViolations = ConcurrentHashMap.newKeySet();

        private TrackingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return track(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return track(delegate.getConnection(username, password));
        }

        private Connection track(Connection connection) {
            Thread owner = Thread.currentThread();
            AtomicBoolean closed = new AtomicBoolean();
            openedConnections.incrementAndGet();
            activeConnections.add(connection);
            return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "TrackedConnection[" + connection + ']';
                            default -> throw new IllegalStateException(method.getName());
                        };
                    }
                    if (Thread.currentThread() != owner) {
                        threadViolations.add(owner.getName() + "->" + Thread.currentThread().getName());
                        throw new SQLException("Connection used by a thread other than its owner");
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    } finally {
                        if (method.getName().equals("close") && closed.compareAndSet(false, true)) {
                            activeConnections.remove(connection);
                            closedConnections.incrementAndGet();
                        }
                    }
                });
        }

        int openedConnections() {
            return openedConnections.get();
        }

        int closedConnections() {
            return closedConnections.get();
        }

        int activeConnections() {
            return activeConnections.size();
        }

        Set<String> threadViolations() {
            return Set.copyOf(threadViolations);
        }

        @Override public PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
        @Override public void setLogWriter(PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
        @Override public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }
        @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return delegate.getParentLogger(); }
        @Override public <T> T unwrap(Class<T> type) throws SQLException { return delegate.unwrap(type); }
        @Override public boolean isWrapperFor(Class<?> type) throws SQLException { return delegate.isWrapperFor(type); }
    }
}
