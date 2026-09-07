package org.liteorm.types.postgresql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.liteorm.LiteOrm;
import org.liteorm.testsupport.database.DatabaseEngine;
import org.liteorm.testsupport.database.TestDatabase;
import org.liteorm.types.postgresql.fixture.PostgreSqlLifecycleBoundTypesMapper;
import org.liteorm.types.postgresql.fixture.PostgreSqlLifecycleBoundTypesMapperImpl;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PostgreSqlLifecycleBoundMappingTest {

    private PostgreSqlLifecycleBoundTypesMapper mapper;

    @BeforeEach
    void resetDatabase() throws Exception {
        TestDatabase database = TestDatabase.shared(DatabaseEngine.POSTGRESQL);
        DataSource dataSource = database.createDataSource();
        database.execute(dataSource, """
            CREATE TABLE postgresql_lifecycle_values (
                id BIGINT PRIMARY KEY,
                xml_value XML,
                array_value TEXT[]
            )
            """, """
            INSERT INTO postgresql_lifecycle_values (id, xml_value, array_value)
            VALUES (1, '<value>xml</value>', ARRAY['one', 'two'])
            """);
        mapper = new PostgreSqlLifecycleBoundTypesMapperImpl(
            LiteOrm.jdbc(dataSource).domain("postgresql-lifecycle-types").build().sqlExecutor());
    }

    @Test
    void materializesSqlXmlAndArrayValuesBeforeJdbcCleanup() {
        assertEquals("<value>xml</value>", mapper.findXml(1L));
        assertArrayEquals(new Object[]{"one", "two"}, mapper.findArray(1L));
    }

    @Test
    void bindsSqlXmlIncludingNulls() {
        assertEquals(1, mapper.insertXml(2L, "<value>inserted</value>"));
        assertEquals(1, mapper.insertXml(3L, null));

        assertEquals("<value>inserted</value>", mapper.findXml(2L));
        assertNull(mapper.findXml(3L));
    }
}
