package org.liteorm.example;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.liteorm.JdbcConnectionProvider;
import org.liteorm.StandaloneSqlEngine;
import org.liteorm.api.TransactionContext;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalTransactionIntegrationTest {

    private StandaloneSqlEngine sqlEngine;
    private UserMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:liteorm-local-transaction;DB_CLOSE_DELAY=-1");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(100), email VARCHAR(200), age INT)");
        }

        JdbcConnectionProvider connectionProvider = new JdbcConnectionProvider(dataSource);
        sqlEngine = new StandaloneSqlEngine(connectionProvider);
        mapper = new UserMapperImpl(sqlEngine);
    }

    @Test
    void rollbackRemovesAllWritesPerformedByGeneratedMapper() {
        TransactionContext transaction = begin();

        assertEquals(1, mapper.insert(1L, "Alice", "alice@example.com", 30));
        rollback(transaction);

        assertNull(mapper.findById(1L));
    }

    @Test
    void commitPersistsAllWritesPerformedByGeneratedMapper() {
        TransactionContext transaction = begin();

        assertEquals(1, mapper.insert(1L, "Alice", "alice@example.com", 30));
        assertEquals(1, mapper.insert(2L, "Bob", "bob@example.com", 28));
        commit(transaction);

        assertEquals(new User(1L, "Alice", "alice@example.com", 30), mapper.findById(1L));
        assertEquals(new User(2L, "Bob", "bob@example.com", 28), mapper.findById(2L));
    }

    @Test
    void rollbackRemovesEveryWriteFromGeneratedJdbcBatch() {
        TransactionContext transaction = begin();

        assertEquals(2, mapper.insertBatch(List.of(
            new User(10L, "Dora", "dora@example.com", 25),
            new User(11L, "Evan", "evan@example.com", 27)
        )).length);
        rollback(transaction);

        assertNull(mapper.findById(10L));
        assertNull(mapper.findById(11L));
    }

    @Test
    void nestedManualTransactionsAreRejected() {
        TransactionContext transaction = begin();
        try {
            assertThrows(Exception.class, sqlEngine::begin);
        } finally {
            rollback(transaction);
        }
    }

    @Test
    void mapperCallsReuseOnePhysicalConnectionInsideLocalTransaction() {
        JdbcDataSource delegate = new JdbcDataSource();
        delegate.setURL("jdbc:h2:mem:liteorm-local-transaction-reuse;DB_CLOSE_DELAY=-1");
        AtomicInteger acquiredConnections = new AtomicInteger();
        DataSource dataSource = (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{DataSource.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getConnection")) {
                    acquiredConnections.incrementAndGet();
                }
                return method.invoke(delegate, args);
            }
        );

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(100), email VARCHAR(200), age INT)");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        acquiredConnections.set(0);

        StandaloneSqlEngine engine = new StandaloneSqlEngine(new JdbcConnectionProvider(dataSource));
        UserMapper transactionMapper = new UserMapperImpl(engine);
        TransactionContext transaction;
        try {
            transaction = engine.begin();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        try {
            assertEquals(1, transactionMapper.insert(1L, "Alice", "alice@example.com", 30));
            assertEquals(1, transactionMapper.insert(2L, "Bob", "bob@example.com", 28));
            assertEquals(1, acquiredConnections.get());
        } finally {
            try {
                engine.rollback(transaction);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }
    }

    private TransactionContext begin() {
        try {
            return sqlEngine.begin();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void commit(TransactionContext transaction) {
        try {
            sqlEngine.commit(transaction);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void rollback(TransactionContext transaction) {
        try {
            sqlEngine.rollback(transaction);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
