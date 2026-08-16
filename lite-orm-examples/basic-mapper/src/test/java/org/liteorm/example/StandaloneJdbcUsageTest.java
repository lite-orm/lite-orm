package org.liteorm.example;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.liteorm.JdbcAssembly;
import org.liteorm.LiteOrm;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StandaloneJdbcUsageTest {

    @Test
    void generatedMapperExecutesThroughStandaloneAssembly() throws SQLException {
        JdbcAssembly assembly = LiteOrm.jdbc(dataSource()).domain("example").build();
        UserMapper mapper = new UserMapperImpl(assembly.sqlExecutor());

        User user = assembly.transactionalExecutor().execute(transaction -> {
            mapper.insert(1L, "Alice", "alice@example.com", 30);
            return mapper.findById(1L);
        });

        assertEquals(new User(1L, "Alice", "alice@example.com", 30), user);
    }

    @Test
    void callbackFailureRollsBackMapperWrites() throws SQLException {
        JdbcAssembly assembly = LiteOrm.jdbc(dataSource()).domain("example").build();
        UserMapper mapper = new UserMapperImpl(assembly.sqlExecutor());

        assertThrows(IllegalStateException.class, () ->
            assembly.transactionalExecutor().execute(transaction -> {
                mapper.insert(1L, "Alice", "alice@example.com", 30);
                throw new IllegalStateException("rollback");
            }));

        assertNull(mapper.findById(1L));
    }

    private DataSource dataSource() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users ("
                + "id BIGINT PRIMARY KEY, "
                + "name VARCHAR(100), "
                + "email VARCHAR(100), "
                + "age INTEGER)");
        }
        return dataSource;
    }
}
