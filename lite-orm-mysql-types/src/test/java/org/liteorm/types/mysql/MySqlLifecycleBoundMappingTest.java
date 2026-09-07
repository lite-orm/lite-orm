package org.liteorm.types.mysql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.liteorm.LiteOrm;
import org.liteorm.testsupport.database.DatabaseEngine;
import org.liteorm.testsupport.database.TestDatabase;
import org.liteorm.types.mysql.fixture.MySqlLifecycleBoundTypesMapper;
import org.liteorm.types.mysql.fixture.MySqlLifecycleBoundTypesMapperImpl;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MySqlLifecycleBoundMappingTest {

    private MySqlLifecycleBoundTypesMapper mapper;

    @BeforeEach
    void resetDatabase() throws Exception {
        TestDatabase database = TestDatabase.shared(DatabaseEngine.MYSQL);
        DataSource dataSource = database.createDataSource();
        database.execute(dataSource, """
            CREATE TABLE mysql_lifecycle_values (
                id BIGINT PRIMARY KEY,
                blob_value LONGBLOB,
                clob_value LONGTEXT,
                nclob_value LONGTEXT CHARACTER SET utf8mb4
            )
            """, """
            INSERT INTO mysql_lifecycle_values (id, blob_value, clob_value, nclob_value)
            VALUES (1, X'01020304', 'large text value', 'national text value')
            """);
        mapper = new MySqlLifecycleBoundTypesMapperImpl(
            LiteOrm.jdbc(dataSource).domain("mysql-lifecycle-types").build().sqlExecutor());
    }

    @Test
    void materializesBlobClobAndNClobValuesBeforeJdbcCleanup() {
        assertArrayEquals(new byte[]{1, 2, 3, 4}, mapper.findBlob(1L));
        assertEquals("large text value", mapper.findClob(1L));
        assertEquals("national text value", mapper.findNClob(1L));
    }

    @Test
    void bindsBlobClobAndNClobValuesIncludingNulls() {
        assertEquals(1, mapper.insert(
            2L, new byte[]{5, 6, 7, 8}, "inserted large text", "inserted national text"));
        assertEquals(1, mapper.insert(3L, null, null, null));

        assertArrayEquals(new byte[]{5, 6, 7, 8}, mapper.findBlob(2L));
        assertEquals("inserted large text", mapper.findClob(2L));
        assertEquals("inserted national text", mapper.findNClob(2L));
        assertNull(mapper.findBlob(3L));
        assertNull(mapper.findClob(3L));
        assertNull(mapper.findNClob(3L));
    }
}
