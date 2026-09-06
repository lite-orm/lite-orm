package org.liteorm.types.mysql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.liteorm.LiteOrm;
import org.liteorm.api.JdbcTypeMappingsMetadata;
import org.liteorm.types.mysql.fixture.MySqlTypesMapper;
import org.liteorm.types.mysql.fixture.MySqlTypesMapperImpl;
import org.liteorm.testsupport.database.DatabaseEngine;
import org.liteorm.testsupport.database.TestDatabase;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlUuidMappingTest {

    private MySqlTypesMapper mapper;

    @BeforeEach
    void resetDatabase() throws Exception {
        TestDatabase database = TestDatabase.shared(DatabaseEngine.MYSQL);
        DataSource dataSource = database.createDataSource();
        database.execute(dataSource, """
            CREATE TABLE mysql_type_values (
                id BIGINT PRIMARY KEY,
                uuid_value CHAR(36)
            )
            """);
        mapper = new MySqlTypesMapperImpl(
            LiteOrm.jdbc(dataSource).domain("mysql-types").build().sqlExecutor());
    }

    @Test
    void mapsUuidParametersNullsAndScalarShapesThroughTheOfficialCollection() {
        UUID expected = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");

        assertEquals(1, mapper.insertUuid(1L, expected));
        assertEquals(1, mapper.insertUuid(2L, null));

        assertEquals(expected, mapper.findUuid(1L));
        assertNull(mapper.findUuid(2L));
        var values = mapper.findUuids();
        assertEquals(2, values.size());
        assertEquals(expected, values.get(0));
        assertNull(values.get(1));
        assertEquals(expected, mapper.findOptionalUuid(1L).orElseThrow());
        assertTrue(mapper.findOptionalUuid(2L).isEmpty());
        assertTrue(mapper.findOptionalUuid(99L).isEmpty());
        assertEquals(MySqlJdbcTypeMappings.class,
            ((JdbcTypeMappingsMetadata) mapper).jdbcTypeMappings());
    }
}
