package org.liteorm.types.postgresql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.liteorm.LiteOrm;
import org.liteorm.api.JdbcTypeMappingsMetadata;
import org.liteorm.types.postgresql.fixture.PostgreSqlTypesMapper;
import org.liteorm.types.postgresql.fixture.PostgreSqlTypesMapperImpl;
import org.liteorm.testsupport.database.DatabaseEngine;
import org.liteorm.testsupport.database.TestDatabase;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgreSqlUuidMappingTest {

    private PostgreSqlTypesMapper mapper;

    @BeforeEach
    void resetDatabase() throws Exception {
        TestDatabase database = TestDatabase.shared(DatabaseEngine.POSTGRESQL);
        DataSource dataSource = database.createDataSource();
        database.execute(dataSource, """
            CREATE TABLE postgresql_type_values (
                id BIGINT PRIMARY KEY,
                uuid_value UUID
            )
            """);
        mapper = new PostgreSqlTypesMapperImpl(
            LiteOrm.jdbc(dataSource).domain("postgresql-types").build().sqlExecutor());
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
        assertEquals(PostgreSqlJdbcTypeMappings.class,
            ((JdbcTypeMappingsMetadata) mapper).jdbcTypeMappings());
    }
}
