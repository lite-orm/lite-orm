package org.liteorm.test;

import org.junit.jupiter.api.Test;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.compile.AstNode;
import org.liteorm.compile.FreemarkerCodeGenerator;
import org.liteorm.compile.MapperCompilationModel;
import org.liteorm.compile.SqlParameterParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FreemarkerCodeGeneratorTest {

    private final FreemarkerCodeGenerator generator = new FreemarkerCodeGenerator();

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
            false,
            "org.liteorm.test.User",
            "new org.liteorm.test.User((Long)row[0], (String)row[1], null, null)",
            List.of(new SqlParameterParser.MethodParameter("id", "id", "java.lang.Long", List.of("id", "param1", "arg0"))),
            List.of(new SqlParameterParser.ParameterBinding(1, "id", "id", "java.lang.Long")),
            null
        );

        String code = generator.generateMethodImpl(method);

        assertTrue(code.contains("ExecutionPlan plan = buildFindByIdExecutionPlan(id);"));
        assertTrue(code.contains("private ExecutionPlan buildFindByIdExecutionPlan(Long id)"));
        assertTrue(code.contains("return new SqlTask(\"org.liteorm.test.UserMapper.findById\", sql, params"));
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
            false,
            "java.util.List<org.liteorm.test.User>",
            "new org.liteorm.test.User((Long)row[0], (String)row[1], (String)row[2], (Integer)row[3])",
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
        assertTrue(code.contains("parameters.add(name);"));
        assertTrue(code.contains("sql.append(\"?\");"));
    }
}
