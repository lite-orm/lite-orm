package io.github.lynxus.compile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlParameterParserTest {

    private final SqlParameterParser parser = new SqlParameterParser();

    @Test
    void resolvesMybatisStyleAliasesAndPropertyAccess() {
        List<SqlParameterParser.MethodParameter> parameters = List.of(
            new SqlParameterParser.MethodParameter(
                "id",
                "id",
                "java.lang.Long",
                List.of("userId", "id", "param1", "arg0")
            ),
            new SqlParameterParser.MethodParameter(
                "user",
                "user",
                "io.github.lynxus.test.User",
                List.of("user", "param2", "arg1")
            )
        );

        SqlParameterParser.SqlParseResult result = parser.parseSql(
            "SELECT * FROM users WHERE id = #{userId} AND name = #{user.name}",
            parameters
        );

        assertEquals("SELECT * FROM users WHERE id = ? AND name = ?", result.processedSql());
        assertEquals(2, result.bindings().size());
        assertEquals("id", result.bindings().get(0).accessCode());
        assertEquals("user.name()", result.bindings().get(1).accessCode());
    }

    @Test
    void exposesCollectionAliasesForSingleCollectionParameters() {
        List<SqlParameterParser.MethodParameter> parameters = List.of(
            new SqlParameterParser.MethodParameter(
                "orders",
                "orders",
                "java.util.List<io.github.lynxus.test.Order>",
                List.of("orders", "list", "collection", "param1", "arg0")
            )
        );

        assertEquals("orders", parser.toJavaAccess("list", parameters, true));
        assertEquals("orders", parser.toJavaAccess("collection", parameters, true));
    }

    @Test
    void failsFastForUnknownSqlParameters() {
        List<SqlParameterParser.MethodParameter> parameters = List.of(
            new SqlParameterParser.MethodParameter(
                "id",
                "id",
                "java.lang.Long",
                List.of("id", "param1", "arg0")
            )
        );

        assertThrows(IllegalArgumentException.class, () ->
            parser.parseSql("SELECT * FROM users WHERE name = #{missing}", parameters)
        );
    }
}
