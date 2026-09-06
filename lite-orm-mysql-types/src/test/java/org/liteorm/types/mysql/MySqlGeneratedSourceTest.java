package org.liteorm.types.mysql;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlGeneratedSourceTest {

    private static final Path GENERATED_MAPPER = Path.of(
        "target/generated-test-sources/test-annotations/",
        "org/liteorm/types/mysql/fixture/MySqlTypesMapperImpl.java"
    );

    @Test
    void generatesDirectUuidAdapterReferencesWithoutChangingMapperConstruction() throws Exception {
        String source = Files.readString(GENERATED_MAPPER);

        assertEquals(1, occurrences(source,
            "new org.liteorm.types.mysql.MySqlUuidJdbcValueAdapter()"));
        assertTrue(source.contains(
            "statement.setNull(index, java.sql.JDBCType.CHAR.getVendorTypeNumber())"));
        assertTrue(source.contains(
            "jdbcValueAdapter1.setNonNull(statement, index, value, java.sql.JDBCType.CHAR)"));
        assertTrue(source.contains("return jdbcValueAdapter1.getNullable(resultSet, 1);"));
        assertTrue(source.contains(
            "return org.liteorm.types.mysql.MySqlJdbcTypeMappings.class;"));
        assertEquals(1, occurrences(source, "public MySqlTypesMapperImpl("));
        assertTrue(source.contains("public MySqlTypesMapperImpl(SqlExecutor sqlExecutor)"));
        assertFalse(source.contains("Class.forName"));
        assertFalse(source.contains("ServiceLoader"));
    }

    @Test
    void generatesDirectLocalTimeAdapterReferences() throws Exception {
        String source = Files.readString(GENERATED_MAPPER);

        assertEquals(1, occurrences(source,
            "new org.liteorm.types.mysql.MySqlLocalTimeJdbcValueAdapter()"));
        assertTrue(source.contains(
            "statement.setNull(index, java.sql.JDBCType.TIME.getVendorTypeNumber())"));
        assertTrue(source.contains(
            "jdbcValueAdapter2.setNonNull(statement, index, value, java.sql.JDBCType.TIME)"));
        assertTrue(source.contains("return jdbcValueAdapter2.getNullable(resultSet, 1);"));
    }

    @Test
    void generatesDirectOffsetDateTimeAdapterReferences() throws Exception {
        String source = Files.readString(GENERATED_MAPPER);

        assertEquals(1, occurrences(source,
            "new org.liteorm.types.mysql.MySqlOffsetDateTimeJdbcValueAdapter()"));
        assertTrue(source.contains(
            "statement.setNull(index, java.sql.JDBCType.TIMESTAMP.getVendorTypeNumber())"));
        assertTrue(source.contains(
            "jdbcValueAdapter3.setNonNull(statement, index, value, java.sql.JDBCType.TIMESTAMP)"));
        assertTrue(source.contains("return jdbcValueAdapter3.getNullable(resultSet, 1);"));
    }

    private int occurrences(String source, String fragment) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(fragment, offset)) >= 0) {
            count++;
            offset += fragment.length();
        }
        return count;
    }
}
