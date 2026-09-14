package io.github.kervix.testsupport.database;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDatabaseTest {

    @ParameterizedTest
    @EnumSource(DatabaseEngine.class)
    void createsIsolatedRealDatabase(DatabaseEngine engine) throws Exception {
        TestDatabase database = TestDatabase.shared(engine);
        DataSource first = database.createDataSource();
        DataSource second = database.createDataSource();

        database.execute(first,
            "CREATE TABLE fixture_check (id BIGINT PRIMARY KEY)",
            "INSERT INTO fixture_check (id) VALUES (1)");

        assertEquals(1L, count(first));
        assertThrows(SQLException.class, () -> count(second));
        try (var connection = first.getConnection()) {
            assertTrue(connection.isValid(5));
        }
    }

    private long count(DataSource dataSource) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) FROM fixture_check")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
