package org.liteorm.types.postgresql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.liteorm.LiteOrm;
import org.liteorm.types.postgresql.fixture.PostgreSqlTypesMapper;
import org.liteorm.types.postgresql.fixture.PostgreSqlTypesMapperImpl;
import org.liteorm.testsupport.database.DatabaseEngine;
import org.liteorm.testsupport.database.TestDatabase;

import javax.sql.DataSource;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgreSqlLocalTimeMappingTest {

    private PostgreSqlTypesMapper mapper;

    @BeforeEach
    void resetDatabase() throws Exception {
        TestDatabase database = TestDatabase.shared(DatabaseEngine.POSTGRESQL);
        DataSource dataSource = database.createDataSource();
        database.execute(dataSource, """
            CREATE TABLE postgresql_type_values (
                id BIGINT PRIMARY KEY,
                local_time_value TIME(6)
            )
            """);
        mapper = new PostgreSqlTypesMapperImpl(
            LiteOrm.jdbc(dataSource).domain("postgresql-local-time-types").build().sqlExecutor());
    }

    @Test
    void preservesLocalTimeParametersNullsAndScalarShapesToColumnPrecision() {
        LocalTime expected = LocalTime.of(12, 34, 56, 123_456_000);

        assertEquals(1, mapper.insertLocalTime(1L, expected));
        assertEquals(1, mapper.insertLocalTime(2L, null));

        assertEquals(expected, mapper.findLocalTime(1L));
        assertNull(mapper.findLocalTime(2L));
        var values = mapper.findLocalTimes();
        assertEquals(2, values.size());
        assertEquals(expected, values.get(0));
        assertNull(values.get(1));
        assertEquals(expected, mapper.findOptionalLocalTime(1L).orElseThrow());
        assertTrue(mapper.findOptionalLocalTime(2L).isEmpty());
        assertTrue(mapper.findOptionalLocalTime(99L).isEmpty());
    }
}
