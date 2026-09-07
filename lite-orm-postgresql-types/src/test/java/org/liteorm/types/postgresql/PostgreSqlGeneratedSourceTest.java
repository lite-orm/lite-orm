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
    void generatesUuidTypeHandlerRoutingWithoutChangingMapperConstruction() throws Exception {
        String source = Files.readString(GENERATED_MAPPER);
        String handler = handlerFieldName(source, "org.liteorm.types.postgresql.PostgreSqlUuidTypeHandler");

        assertEquals(1, occurrences(source,
            "new org.liteorm.types.postgresql.PostgreSqlUuidTypeHandler()"));
        assertTrue(source.contains(
            "JdbcTypeRouter.mapping(java.util.UUID.class, java.sql.JDBCType.OTHER, \"uuid\", "
                + handler + ")"));
        assertTrue(source.contains(
            "jdbcTypeRouter.parameterBinder(java.util.UUID.class, java.sql.JDBCType.OTHER)"));
        assertTrue(source.contains("new Class<?>[]{java.util.UUID.class}, null"));
        assertTrue(source.contains(
            "return org.liteorm.types.postgresql.PostgreSqlJdbcTypeMappings.class;"));
        assertEquals(1, occurrences(source, "public PostgreSqlTypesMapperImpl("));
        assertTrue(source.contains("public PostgreSqlTypesMapperImpl(SqlExecutor sqlExecutor)"));
        assertFalse(source.contains("Class.forName"));
        assertFalse(source.contains("ServiceLoader"));
    }

    @Test
    void generatesLocalTimeTypeHandlerRouting() throws Exception {
        String source = Files.readString(GENERATED_MAPPER);
        String handler = handlerFieldName(source,
            "org.liteorm.types.postgresql.PostgreSqlLocalTimeTypeHandler");

        assertEquals(1, occurrences(source,
            "new org.liteorm.types.postgresql.PostgreSqlLocalTimeTypeHandler()"));
        assertTrue(source.contains(
            "JdbcTypeRouter.mapping(java.time.LocalTime.class, java.sql.JDBCType.TIME, " + handler + ")"));
        assertTrue(source.contains(
            "jdbcTypeRouter.parameterBinder(java.time.LocalTime.class, java.sql.JDBCType.TIME)"));
    }

    @Test
    void generatesOffsetDateTimeTypeHandlerRouting() throws Exception {
        String source = Files.readString(GENERATED_MAPPER);
        String handler = handlerFieldName(source,
            "org.liteorm.types.postgresql.PostgreSqlOffsetDateTimeTypeHandler");

        assertEquals(1, occurrences(source,
            "new org.liteorm.types.postgresql.PostgreSqlOffsetDateTimeTypeHandler()"));
        assertTrue(source.contains(
            "JdbcTypeRouter.mapping(java.time.OffsetDateTime.class, "
                + "java.sql.JDBCType.TIMESTAMP_WITH_TIMEZONE, " + handler + ")"));
        assertTrue(source.contains(
            "jdbcTypeRouter.parameterBinder(java.time.OffsetDateTime.class, "
                + "java.sql.JDBCType.TIMESTAMP_WITH_TIMEZONE)"));
    }

    private String handlerFieldName(String source, String handlerClass) {
        var matcher = Pattern.compile("(typeHandler\\d+) = new "
            + Pattern.quote(handlerClass) + "\\(\\);").matcher(source);
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
