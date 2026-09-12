package org.liteorm.compile;

import org.junit.jupiter.api.Test;
import org.liteorm.api.ExecutionPlan;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaSourceCodeGeneratorTest {

    private final JavaSourceCodeGenerator generator = new JavaSourceCodeGenerator();

    @Test
    void generatesExecutionPlanFactoriesForStaticStatements() throws Exception {
        MapperCompilationModel.MethodModel method = new MapperCompilationModel.MethodModel(
            "findById",
            "org.liteorm.test.User",
            "Long id",
            "buildFindByIdExecutionPlan",
            "org.liteorm.test.UserMapper.findById",
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.ANNOTATION,
            "SELECT id, name FROM users WHERE id = ?",
            false,
            "org.liteorm.test.User",
            "new org.liteorm.test.User((Long)row[0], (String)row[1], null, null)",
            "",
            List.of(),
            null,
            null,
            null,
            List.of(),
            List.of(),
            null,
            List.of(new SqlParameterParser.MethodParameter("id", "id", "java.lang.Long", List.of("id", "param1", "arg0"))),
            List.of(new SqlParameterParser.ParameterBinding(1, "id", "id", "java.lang.Long")),
            null
        );

        String code = generator.generateMethodImpl(method);

        assertTrue(code.contains(
            "QueryExecutionPlan<org.liteorm.test.User> executionPlan = buildFindByIdExecutionPlan(id);"));
        assertTrue(code.contains(
            "QueryResult<org.liteorm.test.User> executionResult = sqlExecutor.query(executionPlan);"));
        assertTrue(code.contains("return executionResult.oneOrNull();"));
        assertTrue(code.contains(
            "private QueryExecutionPlan<org.liteorm.test.User> buildFindByIdExecutionPlan(Long id)"));
        assertTrue(code.contains(
            "private static final QueryDefinition<org.liteorm.test.User> FIND_BY_ID_DEFINITION"));
        assertTrue(code.contains("return FIND_BY_ID_DEFINITION.bind(id);"));
        assertFalse(code.contains("Object[] params"));
        assertFalse(code.contains(
            "return new QueryExecutionPlan<>(\"org.liteorm.test.UserMapper.findById\", sql, params"));
    }

    @Test
    void generatesDynamicSqlLogicFromSharedAstNodes() throws Exception {
        MapperCompilationModel.MethodModel method = new MapperCompilationModel.MethodModel(
            "findByCondition",
            "java.util.List<org.liteorm.test.User>",
            "String name",
            "buildFindByConditionExecutionPlan",
            "org.liteorm.test.UserMapper.findByCondition",
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.ANNOTATION,
            "<script>SELECT * FROM users <if test=\"name != null and name != ''\">WHERE name = #{name}</if></script>",
            true,
            "java.util.List<org.liteorm.test.User>",
            "new org.liteorm.test.User((Long)row[0], (String)row[1], (String)row[2], (Integer)row[3])",
            "",
            List.of(),
            null,
            null,
            null,
            List.of(),
            List.of(new MapperCompilationModel.ParameterRoute(
                "NAME_BINDER", "java.lang.String.class", "java.sql.JDBCType.VARCHAR")),
            null,
            List.of(new SqlParameterParser.MethodParameter("name", "name", "java.lang.String", List.of("name", "param1", "arg0"))),
            List.of(),
            new AstNode.ContainerNode(List.of(
                new AstNode.TextNode("SELECT * FROM users "),
                new AstNode.IfNode("name != null and name != ''", List.of(
                    new AstNode.TextNode("WHERE name = #{name}")
                ))
            ))
        );

        String code = generator.generateMethodImpl(method);

        assertTrue(code.contains("if (name != null && !name.isEmpty())"));
        assertTrue(code.contains("BoundSqlBuilder sql = BoundSqlBuilder.create("));
        assertTrue(code.contains(
            "sql.parameter(name, NAME_BINDER, java.lang.String.class, java.sql.JDBCType.VARCHAR);"));
        assertFalse(code.contains("List<Object> parameters"));
        assertFalse(code.contains("List<Class<?>> parameterTypes"));
    }

    @Test
    void translatesSupportedExpressionsDirectlyToNativeJava() throws Exception {
        MapperCompilationModel.MethodModel method = new MapperCompilationModel.MethodModel(
            "search",
            "java.util.List<org.liteorm.test.User>",
            "String name, java.util.List<Long> ids, long[] excludedIds",
            "buildSearchExecutionPlan",
            "org.liteorm.test.UserMapper.search",
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.XML,
            "<script>SELECT * FROM users</script>",
            true,
            "java.util.List<org.liteorm.test.User>",
            "new org.liteorm.test.User((Long)row[0], (String)row[1], null, null)",
            "",
            List.of(),
            null,
            null,
            null,
            List.of(),
            List.of(
                new MapperCompilationModel.ParameterRoute(null, "null", "null"),
                new MapperCompilationModel.ParameterRoute(null, "java.util.List.class", "null"),
                new MapperCompilationModel.ParameterRoute(null, "long[].class", "null")
            ),
            null,
            List.of(
                new SqlParameterParser.MethodParameter("name", "name", "java.lang.String", List.of("name", "param1", "arg0")),
                new SqlParameterParser.MethodParameter("ids", "ids", "java.util.List<java.lang.Long>", List.of("ids", "param2", "arg1")),
                new SqlParameterParser.MethodParameter("excludedIds", "excludedIds", "long[]", List.of("excludedIds", "param3", "arg2"))
            ),
            List.of(),
            new AstNode.ContainerNode(List.of(
                new AstNode.BindNode("pattern", "'%' + name + '%'"),
                new AstNode.IfNode("name != null and name != ''", List.of(new AstNode.TextNode(" AND name LIKE #{pattern}"))),
                new AstNode.IfNode("ids != null and ids.size() > 0", List.of(new AstNode.TextNode(" AND id IN (#{ids})"))),
                new AstNode.IfNode("excludedIds != null and excludedIds.length > 0", List.of(new AstNode.TextNode(" AND id NOT IN (#{excludedIds})")))
            ))
        );

        String code = generator.generateMethodImpl(method);

        assertTrue(code.contains("Object pattern = \"%\" + name + \"%\";"));
        assertTrue(code.contains("if (name != null && !name.isEmpty())"));
        assertTrue(code.contains("if (ids != null && ids.size() > 0)"));
        assertTrue(code.contains("if (excludedIds != null && excludedIds.length > 0)"));
        assertFalse(code.matches("(?s).*\\b(?:ognl|Ognl|MVEL|SpEL)\\b.*"));
    }
}
