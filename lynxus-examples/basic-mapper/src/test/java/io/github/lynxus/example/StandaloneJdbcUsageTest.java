package io.github.lynxus.example;

import org.junit.jupiter.api.Test;
import io.github.lynxus.JdbcAssembly;
import io.github.lynxus.Lynxus;
import io.github.lynxus.testsupport.database.DatabaseEngine;
import io.github.lynxus.testsupport.database.TestDatabase;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

abstract class StandaloneJdbcUsageTest {

    protected abstract DatabaseEngine databaseEngine();

    @Test
    void generatedMapperExecutesThroughStandaloneAssembly() throws SQLException {
        JdbcAssembly assembly = Lynxus.jdbc(dataSource()).domain("example").build();
        UserMapper mapper = new UserMapperImpl(assembly.sqlExecutor());

        User user = assembly.transactionalExecutor().execute(() -> {
            mapper.insert(1L, "Alice", "alice@example.com", 30);
            return mapper.findById(1L);
        });

        assertEquals(new User(1L, "Alice", "alice@example.com", 30), user);
    }

    @Test
    void callbackFailureRollsBackMapperWrites() throws SQLException {
        JdbcAssembly assembly = Lynxus.jdbc(dataSource()).domain("example").build();
        UserMapper mapper = new UserMapperImpl(assembly.sqlExecutor());

        assertThrows(IllegalStateException.class, () ->
            assembly.transactionalExecutor().execute(() -> {
                mapper.insert(1L, "Alice", "alice@example.com", 30);
                throw new IllegalStateException("rollback");
            }));

        assertNull(mapper.findById(1L));
    }

    private DataSource dataSource() throws SQLException {
        TestDatabase database = TestDatabase.shared(databaseEngine());
        DataSource dataSource = database.createDataSource();
        database.execute(dataSource, "CREATE TABLE users ("
                + "id BIGINT PRIMARY KEY, "
                + "name VARCHAR(100), "
                + "email VARCHAR(100), "
                + "age INTEGER)");
        return dataSource;
    }
}
