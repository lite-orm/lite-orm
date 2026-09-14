package io.github.lynxus.compile;

import org.junit.jupiter.api.Test;
import io.github.lynxus.api.ExecutionPlan;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaSourceCodeGeneratorTest {

    private final JavaSourceCodeGenerator generator = new JavaSourceCodeGenerator();

    @Test
    void generatesExecutionPlanFactoriesForStaticStatements() throws Exception {
        MapperCompilationModel.MethodModel method = new MapperCompilationModel.MethodModel(
            "findById",
            "io.github.lynxus.test.User",
            "Long id",
            "buildFindByIdExecutionPlan",
            "io.github.lynxus.test.UserMapper.findById",
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.ANNOTATION,
            "SELECT id, name FROM users WHERE id = ?",
            false,
            "io.github.lynxus.test.User",
            "new io.github.lynxus.test.User((Long)row[0], (String)row[1], null, null)",
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
            "QueryExecutionPlan<io.github.lynxus.test.User> executionPlan = buildFindByIdExecutionPlan(id);"));
        assertTrue(code.contains(
            "QueryResult<io.github.lynxus.test.User> executionResult = sqlExecutor.query(executionPlan);"));
        assertTrue(code.contains("return executionResult.oneOrNull();"));
        assertTrue(code.contains(
            "private QueryExecutionPlan<io.github.lynxus.test.User> buildFindByIdExecutionPlan(Long id)"));
        assertTrue(code.contains(
            "private static final QueryDefinition<io.github.lynxus.test.User> FIND_BY_ID_DEFINITION"));
        assertTrue(code.contains("return FIND_BY_ID_DEFINITION.bind(id);"));
        assertFalse(code.contains("Object[] params"));
        assertFalse(code.contains(
            "return new QueryExecutionPlan<>(\"io.github.lynxus.test.UserMapper.findById\", sql, params"));
    }

    @Test
    void generatesDynamicSqlLogicFromSharedAstNodes() throws Exception {
        MapperCompilationModel.MethodModel method = new MapperCompilationModel.MethodModel(
            "findByCondition",
            "java.util.List<io.github.lynxus.test.User>",
            "String name",
            "buildFindByConditionExecutionPlan",
            "io.github.lynxus.test.UserMapper.findByCondition",
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.ANNOTATION,
            "<script>SELECT * FROM users <if test=\"name != null and name != ''\">WHERE name = #{name}</if></script>",
            true,
            "java.util.List<io.github.lynxus.test.User>",
            "new io.github.lynxus.test.User((Long)row[0], (String)row[1], (String)row[2], (Integer)row[3])",
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
            "java.util.List<io.github.lynxus.test.User>",
            "String name, java.util.List<Long> ids, long[] excludedIds",
            "buildSearchExecutionPlan",
            "io.github.lynxus.test.UserMapper.search",
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.XML,
            "<script>SELECT * FROM users</script>",
            true,
            "java.util.List<io.github.lynxus.test.User>",
            "new io.github.lynxus.test.User((Long)row[0], (String)row[1], null, null)",
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

    @Test
    void generatesCompleteMapperThroughSourceModelAndRendererPipeline() throws Exception {
        MapperCompilationModel.MethodModel query = new MapperCompilationModel.MethodModel(
            "find",
            "java.lang.String",
            "",
            "buildFindExecutionPlan",
            "io.github.lynxus.test.PipelineMapper.find",
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.ANNOTATION,
            "SELECT value FROM values_table",
            false,
            "java.lang.String",
            "(java.lang.String) row[0]",
            "    private static String mapHelper() { return \"helper\"; }\n",
            List.of(),
            null,
            null,
            null,
            List.of(new MapperCompilationModel.ExtensionField(
                "java.util.ArrayList<java.lang.String>", "extension")),
            List.of(),
            null,
            List.of(),
            List.of(),
            null
        );
        MapperCompilationModel.MethodModel update = new MapperCompilationModel.MethodModel(
            "update",
            "int",
            "",
            "buildUpdateExecutionPlan",
            "io.github.lynxus.test.PipelineMapper.update",
            ExecutionPlan.StatementType.UPDATE,
            ExecutionPlan.SqlSource.ANNOTATION,
            "UPDATE values_table SET value = 'updated'",
            false,
            "",
            "",
            "",
            List.of(),
            null,
            null,
            null,
            List.of(),
            List.of(),
            null,
            List.of(),
            List.of(),
            null
        );
        MapperCompilationModel compilationModel = new MapperCompilationModel(
            "io.github.lynxus.test",
            "PipelineMapper",
            "PipelineMapperImpl",
            "io.github.lynxus.test.PipelineMapper",
            List.of(query, update)
        );

        String source = generator.generateMapperImpl(null, compilationModel, null, null);

        assertTrue(source.contains("public class PipelineMapperImpl implements PipelineMapper"));
        assertTrue(source.contains(
            "private final java.util.ArrayList<java.lang.String> extension = new "
                + "java.util.ArrayList<java.lang.String>();"));
        assertTrue(source.contains("private static final QueryDefinition<java.lang.String> FIND_DEFINITION"));
        assertTrue(source.contains("public java.lang.String find()"));
        assertTrue(source.contains(
            "QueryResult<java.lang.String> executionResult = sqlExecutor.query(executionPlan);"));
        assertTrue(source.contains("return executionResult.oneOrNull();"));
        assertTrue(source.contains("private QueryExecutionPlan<java.lang.String> buildFindExecutionPlan()"));
        assertTrue(source.contains("private static final CommandDefinition UPDATE_DEFINITION"));
        assertTrue(source.contains("public int update()"));
        assertTrue(source.contains("UpdateResult executionResult = sqlExecutor.update(executionPlan);"));
        assertTrue(source.contains("return executionResult.count();"));
        assertTrue(source.contains("private static String mapHelper()"));
        assertFalse(source.contains("generatedMethods"));
        assertFalse(source.contains("<#"));
        assertFalse(source.contains("${"));

        assertBefore(source, "extension = new", "FIND_DEFINITION");
        assertBefore(source, "FIND_DEFINITION", "public java.lang.String find()");
        assertBefore(source, "public java.lang.String find()", "buildFindExecutionPlan()");
        assertBefore(source, "buildFindExecutionPlan()", "mapHelper()");
        assertBefore(source, "mapHelper()", "UPDATE_DEFINITION");
        assertBefore(source, "UPDATE_DEFINITION", "public int update()");
        assertBefore(source, "public int update()", "buildUpdateExecutionPlan()");
    }

    private static void assertBefore(String source, String first, String second) {
        assertTrue(
            source.indexOf(first) >= 0 && source.indexOf(second) >= 0
                && source.indexOf(first) < source.indexOf(second),
            () -> "Expected '" + first + "' before '" + second + "'\n" + source
        );
    }
}
