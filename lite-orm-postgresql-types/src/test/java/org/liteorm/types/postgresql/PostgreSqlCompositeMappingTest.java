package org.liteorm.types.postgresql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.liteorm.LiteOrm;
import org.liteorm.types.postgresql.fixture.PostgreSqlTypesBean;
import org.liteorm.types.postgresql.fixture.PostgreSqlTypesMapper;
import org.liteorm.types.postgresql.fixture.PostgreSqlTypesMapperImpl;
import org.liteorm.types.postgresql.fixture.PostgreSqlTypesRecord;
import org.liteorm.testsupport.database.DatabaseEngine;
import org.liteorm.testsupport.database.TestDatabase;

import javax.sql.DataSource;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PostgreSqlCompositeMappingTest {

    private PostgreSqlTypesMapper mapper;

    @BeforeEach
    void resetDatabase() throws Exception {
        TestDatabase database = TestDatabase.shared(DatabaseEngine.POSTGRESQL);
        DataSource dataSource = database.createDataSource();
        database.execute(dataSource, """
            CREATE TABLE postgresql_type_values (
                id BIGINT PRIMARY KEY,
                uuid_value UUID,
                local_time_value TIME(6),
                offset_date_time_value TIMESTAMP(6) WITH TIME ZONE
            )
            """);
        mapper = new PostgreSqlTypesMapperImpl(
            LiteOrm.jdbc(dataSource).domain("postgresql-composite-types").build().sqlExecutor());
    }

    @Test
    void preservesOfficialValuesInRecordAndJavaBeanResults() {
        UUID uuid = UUID.fromString("d9428888-122b-11e1-b85c-61cd3cbb3210");
        LocalTime localTime = LocalTime.of(6, 7, 8, 123_456_000);
        OffsetDateTime offsetDateTime = OffsetDateTime.of(
            2026, 9, 2, 6, 7, 8, 654_321_000, ZoneOffset.ofHours(-4));

        assertEquals(1, mapper.insertValues(1L, uuid, localTime, offsetDateTime));
        assertEquals(1, mapper.insertValues(2L, null, null, null));

        PostgreSqlTypesRecord record = mapper.findRecord(1L);
        assertEquals(uuid, record.uuid());
        assertEquals(localTime, record.localTime());
        assertEquals(offsetDateTime.toInstant(), record.offsetDateTime().toInstant());

        PostgreSqlTypesBean bean = mapper.findBean(1L);
        assertEquals(uuid, bean.getUuid());
        assertEquals(localTime, bean.getLocalTime());
        assertEquals(offsetDateTime.toInstant(), bean.getOffsetDateTime().toInstant());

        PostgreSqlTypesRecord nullRecord = mapper.findRecord(2L);
        assertNull(nullRecord.uuid());
        assertNull(nullRecord.localTime());
        assertNull(nullRecord.offsetDateTime());

        PostgreSqlTypesBean nullBean = mapper.findBean(2L);
        assertNull(nullBean.getUuid());
        assertNull(nullBean.getLocalTime());
        assertNull(nullBean.getOffsetDateTime());
    }
}
