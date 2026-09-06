package org.liteorm.types.mysql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.liteorm.LiteOrm;
import org.liteorm.types.mysql.fixture.MySqlTypesMapper;
import org.liteorm.types.mysql.fixture.MySqlTypesMapperImpl;
import org.liteorm.testsupport.database.DatabaseEngine;
import org.liteorm.testsupport.database.TestDatabase;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlOffsetDateTimeMappingTest {

    private MySqlTypesMapper mapper;

    @BeforeEach
    void resetDatabase() throws Exception {
        TestDatabase database = TestDatabase.shared(DatabaseEngine.MYSQL);
        DataSource dataSource = database.createDataSource();
        database.execute(dataSource, """
            CREATE TABLE mysql_type_values (
                id BIGINT PRIMARY KEY,
                offset_date_time_value TIMESTAMP(6)
            )
            """);
        mapper = new MySqlTypesMapperImpl(
            LiteOrm.jdbc(dataSource).domain("mysql-offset-date-time-types").build().sqlExecutor());
    }

    @Test
    void preservesOffsetDateTimeInstantNullsAndScalarShapesToColumnPrecision() {
        OffsetDateTime expected = OffsetDateTime.of(
            2026, 9, 2, 8, 49, 12, 654_321_000, ZoneOffset.ofHours(8));

        assertEquals(1, mapper.insertOffsetDateTime(1L, expected));
        assertEquals(1, mapper.insertOffsetDateTime(2L, null));

        assertSameInstant(expected, mapper.findOffsetDateTime(1L));
        assertNull(mapper.findOffsetDateTime(2L));
        assertSameInstant(expected, mapper.findOffsetDateTimes().getFirst());
        assertNull(mapper.findOffsetDateTimes().get(1));
        assertSameInstant(expected, mapper.findOptionalOffsetDateTime(1L).orElseThrow());
        assertTrue(mapper.findOptionalOffsetDateTime(2L).isEmpty());
        assertTrue(mapper.findOptionalOffsetDateTime(99L).isEmpty());
    }

    private void assertSameInstant(OffsetDateTime expected, OffsetDateTime actual) {
        assertEquals(expected.toInstant(), actual.toInstant());
    }
}
