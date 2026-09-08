package org.liteorm.types.mysql;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlGeneratedSourceTest {

    private static final Path GENERATED_MAPPER = Path.of(
        "target/generated-test-sources/test-annotations/",
        "org/liteorm/types/mysql/fixture/MySqlTypesMapperImpl.java"
    );

    @Test
    void generatesUuidTypeHandlerRoutingWithoutChangingMapperConstruction() throws Exception {
        String source = Files.readString(GENERATED_MAPPER);
        String handler = handlerFieldName(source, "org.liteorm.types.mysql.MySqlUuidTypeHandler");

        assertEquals(1, occurrences(source,
            "new org.liteorm.types.mysql.MySqlUuidTypeHandler()"));
        assertTrue(source.contains(
            "TypeHandlerManager.mapping(java.util.UUID.class, java.sql.JDBCType.CHAR, " + handler + ")"));
        assertTrue(source.contains(
            "typeHandlerManager.parameterBinder(java.util.UUID.class, java.sql.JDBCType.CHAR)"));
        assertTrue(source.contains("new Class<?>[]{java.util.UUID.class}, null"));
        assertTrue(source.contains(
            "return org.liteorm.types.mysql.MySqlJdbcTypeMappings.class;"));
        assertEquals(1, occurrences(source, "public MySqlTypesMapperImpl("));
        assertTrue(source.contains("public MySqlTypesMapperImpl(SqlExecutor sqlExecutor)"));
        assertFalse(source.contains("Class.forName"));
        assertFalse(source.contains("ServiceLoader"));
    }

    @Test
    void generatesLocalTimeTypeHandlerRouting() throws Exception {
        String source = Files.readString(GENERATED_MAPPER);
        String handler = handlerFieldName(source, "org.liteorm.types.mysql.MySqlLocalTimeTypeHandler");

        assertEquals(1, occurrences(source,
            "new org.liteorm.types.mysql.MySqlLocalTimeTypeHandler()"));
        assertTrue(source.contains(
            "TypeHandlerManager.mapping(java.time.LocalTime.class, java.sql.JDBCType.TIME, " + handler + ")"));
        assertTrue(source.contains(
            "typeHandlerManager.parameterBinder(java.time.LocalTime.class, java.sql.JDBCType.TIME)"));
    }

    @Test
    void generatesOffsetDateTimeTypeHandlerRouting() throws Exception {
        String source = Files.readString(GENERATED_MAPPER);
        String handler = handlerFieldName(source,
            "org.liteorm.types.mysql.MySqlOffsetDateTimeTypeHandler");

        assertEquals(1, occurrences(source,
            "new org.liteorm.types.mysql.MySqlOffsetDateTimeTypeHandler()"));
        assertTrue(source.contains(
            "TypeHandlerManager.mapping(java.time.OffsetDateTime.class, java.sql.JDBCType.TIMESTAMP, "
                + handler + ")"));
        assertTrue(source.contains(
            "typeHandlerManager.parameterBinder(java.time.OffsetDateTime.class, "
                + "java.sql.JDBCType.TIMESTAMP)"));
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
