package org.liteorm.types.postgresql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.liteorm.LiteOrm;
import org.liteorm.testsupport.database.DatabaseEngine;
import org.liteorm.testsupport.database.TestDatabase;
import org.liteorm.types.postgresql.fixture.PostgreSqlTypesMapper;
import org.liteorm.types.postgresql.fixture.PostgreSqlTypesMapperImpl;

import javax.sql.DataSource;
import java.time.OffsetTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PostgreSqlOffsetTimeMappingTest {

    private PostgreSqlTypesMapper mapper;

    @BeforeEach
    void resetDatabase() throws Exception {
        TestDatabase database = TestDatabase.shared(DatabaseEngine.POSTGRESQL);
        DataSource dataSource = database.createDataSource();
        database.execute(dataSource, """
            CREATE TABLE postgresql_offset_time_values (
                id BIGINT PRIMARY KEY,
                value TIME(6) WITH TIME ZONE
            )
            """);
        mapper = new PostgreSqlTypesMapperImpl(
            LiteOrm.jdbc(dataSource).domain("postgresql-offset-time").build().sqlExecutor());
    }

    @Test
    void preservesLocalTimeAndOffsetThroughPostgreSqlTimeWithTimeZone() {
        OffsetTime expected = OffsetTime.of(7, 8, 9, 123_456_000, ZoneOffset.ofHoursMinutes(5, 30));

        assertEquals(1, mapper.insertOffsetTime(1L, expected));
        assertEquals(1, mapper.insertOffsetTime(2L, null));

        assertEquals(expected, mapper.findOffsetTime(1L));
        assertNull(mapper.findOffsetTime(2L));
    }
}
