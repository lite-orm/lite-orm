package org.liteorm.types.postgresql;

import org.junit.jupiter.api.Test;
import org.liteorm.testsupport.database.DatabaseEngine;
import org.liteorm.testsupport.database.TestDatabase;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgreSqlTemporalExclusionEvidenceTest {

    @Test
    void timestampWithTimeZonePreservesAnInstantButNotAZoneId() throws Exception {
        TestDatabase database = TestDatabase.shared(DatabaseEngine.POSTGRESQL);
        DataSource dataSource = database.createDataSource();
        database.execute(dataSource, "CREATE TABLE postgresql_zone_evidence (value TIMESTAMPTZ)");
        ZonedDateTime original = ZonedDateTime.of(
            2026, 9, 6, 7, 8, 9, 123_456_000, ZoneId.of("Europe/Paris"));

        try (var connection = dataSource.getConnection();
             var insert = connection.prepareStatement("INSERT INTO postgresql_zone_evidence VALUES (?)")) {
            insert.setObject(1, original.toOffsetDateTime());
            assertEquals(1, insert.executeUpdate());
        }

        try (var connection = dataSource.getConnection();
             var query = connection.prepareStatement("SELECT value FROM postgresql_zone_evidence");
             var resultSet = query.executeQuery()) {
            resultSet.next();
            OffsetDateTime read = resultSet.getObject(1, OffsetDateTime.class);
            assertEquals(original.toInstant(), read.toInstant());
            assertNotEquals(original.getZone(), read.getOffset());
            assertThrows(SQLException.class, () -> resultSet.getObject(1, ZonedDateTime.class));
        }
    }
}
