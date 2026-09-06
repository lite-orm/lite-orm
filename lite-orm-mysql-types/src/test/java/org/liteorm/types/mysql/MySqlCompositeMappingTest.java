package org.liteorm.types.mysql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.liteorm.LiteOrm;
import org.liteorm.types.mysql.fixture.MySqlTypesBean;
import org.liteorm.types.mysql.fixture.MySqlTypesMapper;
import org.liteorm.types.mysql.fixture.MySqlTypesMapperImpl;
import org.liteorm.types.mysql.fixture.MySqlTypesRecord;
import org.liteorm.testsupport.database.DatabaseEngine;
import org.liteorm.testsupport.database.TestDatabase;

import javax.sql.DataSource;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MySqlCompositeMappingTest {

    private MySqlTypesMapper mapper;

    @BeforeEach
    void resetDatabase() throws Exception {
        TestDatabase database = TestDatabase.shared(DatabaseEngine.MYSQL);
        DataSource dataSource = database.createDataSource();
        database.execute(dataSource, """
            CREATE TABLE mysql_type_values (
                id BIGINT PRIMARY KEY,
                uuid_value CHAR(36),
                local_time_value TIME(6),
                offset_date_time_value TIMESTAMP(6)
            )
            """);
        mapper = new MySqlTypesMapperImpl(
            LiteOrm.jdbc(dataSource).domain("mysql-composite-types").build().sqlExecutor());
    }

    @Test
    void preservesOfficialValuesInRecordAndJavaBeanResults() {
        UUID uuid = UUID.fromString("d9428888-122b-11e1-b85c-61cd3cbb3210");
        LocalTime localTime = LocalTime.of(6, 7, 8, 123_456_000);
        OffsetDateTime offsetDateTime = OffsetDateTime.of(
            2026, 9, 2, 6, 7, 8, 654_321_000, ZoneOffset.ofHours(-4));

        assertEquals(1, mapper.insertValues(1L, uuid, localTime, offsetDateTime));
        assertEquals(1, mapper.insertValues(2L, null, null, null));

        MySqlTypesRecord record = mapper.findRecord(1L);
        assertEquals(uuid, record.uuid());
        assertEquals(localTime, record.localTime());
        assertEquals(offsetDateTime.toInstant(), record.offsetDateTime().toInstant());

        MySqlTypesBean bean = mapper.findBean(1L);
        assertEquals(uuid, bean.getUuid());
        assertEquals(localTime, bean.getLocalTime());
        assertEquals(offsetDateTime.toInstant(), bean.getOffsetDateTime().toInstant());

        MySqlTypesRecord nullRecord = mapper.findRecord(2L);
        assertNull(nullRecord.uuid());
        assertNull(nullRecord.localTime());
        assertNull(nullRecord.offsetDateTime());

        MySqlTypesBean nullBean = mapper.findBean(2L);
        assertNull(nullBean.getUuid());
        assertNull(nullBean.getLocalTime());
        assertNull(nullBean.getOffsetDateTime());
    }
}
