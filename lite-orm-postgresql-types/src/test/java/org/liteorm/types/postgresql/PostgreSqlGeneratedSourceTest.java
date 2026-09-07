package org.liteorm.types.postgresql;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgreSqlGeneratedSourceTest {

    private static final Path GENERATED_MAPPER = Path.of(
        "target/generated-test-sources/test-annotations/",
        "org/liteorm/types/postgresql/fixture/PostgreSqlTypesMapperImpl.java"
    );

    @Test
    void generatesDirectUuidAdapterReferencesWithoutChangingMapperConstruction() throws Exception {
        String source = Files.readString(GENERATED_MAPPER);
        String adapter = adapterFieldName(source, "org.liteorm.types.postgresql.PostgreSqlUuidJdbcValueAdapter");

        assertEquals(1, occurrences(source,
            "new org.liteorm.types.postgresql.PostgreSqlUuidJdbcValueAdapter()"));
        assertTrue(source.contains(
            adapter + ".setNull(statement, index, java.sql.JDBCType.OTHER)"));
        assertTrue(source.contains(
            adapter + ".setNonNull(statement, index, value, java.sql.JDBCType.OTHER)"));
        assertTrue(source.contains("return " + adapter + ".getNullable(resultSet, 1);"));
        assertTrue(source.contains(
            "return org.liteorm.types.postgresql.PostgreSqlJdbcTypeMappings.class;"));
        assertEquals(1, occurrences(source, "public PostgreSqlTypesMapperImpl("));
        assertTrue(source.contains("public PostgreSqlTypesMapperImpl(SqlExecutor sqlExecutor)"));
        assertFalse(source.contains("Class.forName"));
        assertFalse(source.contains("ServiceLoader"));
    }

    @Test
    void generatesDirectLocalTimeAdapterReferences() throws Exception {
        String source = Files.readString(GENERATED_MAPPER);
        String adapter = adapterFieldName(source,
            "org.liteorm.types.postgresql.PostgreSqlLocalTimeJdbcValueAdapter");

        assertEquals(1, occurrences(source,
            "new org.liteorm.types.postgresql.PostgreSqlLocalTimeJdbcValueAdapter()"));
        assertTrue(source.contains(
            adapter + ".setNull(statement, index, java.sql.JDBCType.TIME)"));
        assertTrue(source.contains(
            adapter + ".setNonNull(statement, index, value, java.sql.JDBCType.TIME)"));
        assertTrue(source.contains("return " + adapter + ".getNullable(resultSet, 1);"));
    }

    @Test
    void generatesDirectOffsetDateTimeAdapterReferences() throws Exception {
        String source = Files.readString(GENERATED_MAPPER);
        String adapter = adapterFieldName(source,
            "org.liteorm.types.postgresql.PostgreSqlOffsetDateTimeJdbcValueAdapter");

        assertEquals(1, occurrences(source,
            "new org.liteorm.types.postgresql.PostgreSqlOffsetDateTimeJdbcValueAdapter()"));
        assertTrue(source.contains(
            adapter + ".setNull(statement, index, java.sql.JDBCType.TIMESTAMP_WITH_TIMEZONE)"));
        assertTrue(source.contains(
            adapter + ".setNonNull(statement, index, value, "
                + "java.sql.JDBCType.TIMESTAMP_WITH_TIMEZONE)"));
        assertTrue(source.contains("return " + adapter + ".getNullable(resultSet, 1);"));
    }

    private String adapterFieldName(String source, String adapterClass) {
        var matcher = Pattern.compile("(jdbcValueAdapter\\d+) = new "
            + Pattern.quote(adapterClass) + "\\(\\);").matcher(source);
        assertTrue(matcher.find(), source);
        return matcher.group(1);
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
