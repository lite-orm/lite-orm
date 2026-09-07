package org.liteorm.types.mysql;

import org.junit.jupiter.api.Test;
import org.liteorm.testsupport.database.DatabaseEngine;
import org.liteorm.testsupport.database.TestDatabase;

import javax.sql.DataSource;
import java.sql.JDBCType;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MySqlTemporalExclusionEvidenceTest {

    @Test
    void timeAndTimestampColumnsExposeNoOffsetOrZoneIdStorage() throws Exception {
        TestDatabase database = TestDatabase.shared(DatabaseEngine.MYSQL);
        DataSource dataSource = database.createDataSource();
        database.execute(dataSource, """
            CREATE TABLE mysql_temporal_exclusion_evidence (
                time_value TIME(6),
                timestamp_value TIMESTAMP(6)
            )
            """);
        OffsetTime offsetTime = OffsetTime.parse("07:08:09.123456+05:30");
        ZonedDateTime zonedDateTime = ZonedDateTime.of(
            2026, 9, 6, 7, 8, 9, 123_456_000, ZoneId.of("Europe/Paris"));

        try (var connection = dataSource.getConnection();
             var insert = connection.prepareStatement(
                 "INSERT INTO mysql_temporal_exclusion_evidence VALUES (?, ?)")) {
            insert.setObject(1, offsetTime.toLocalTime());
            insert.setObject(2, zonedDateTime.toLocalDateTime());
            assertEquals(1, insert.executeUpdate());
        }

        try (var connection = dataSource.getConnection();
             var query = connection.prepareStatement(
                 "SELECT time_value, timestamp_value FROM mysql_temporal_exclusion_evidence");
             var resultSet = query.executeQuery()) {
            resultSet.next();
            assertEquals(JDBCType.TIME.getVendorTypeNumber(), resultSet.getMetaData().getColumnType(1));
            assertEquals(JDBCType.TIMESTAMP.getVendorTypeNumber(), resultSet.getMetaData().getColumnType(2));
            assertEquals(offsetTime.toLocalTime(), resultSet.getObject(1, LocalTime.class));
            assertEquals(zonedDateTime.toLocalDateTime(), resultSet.getObject(2, LocalDateTime.class));
        }
    }
}
