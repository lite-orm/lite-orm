package org.liteorm.test;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCodeTest {

    private static final Path GENERATED_CODE_DIRECTORY =
        Path.of("target", "generated-test-sources", "test-annotations", "org", "liteorm", "test");

    @Test
    void generatesAnnotationAndXmlMapperImplementations() {
        assertTrue(Files.isRegularFile(GENERATED_CODE_DIRECTORY.resolve("UserMapperImpl.java")));
        assertTrue(Files.isRegularFile(GENERATED_CODE_DIRECTORY.resolve("UserMapperXmlImpl.java")));
    }

    @Test
    void generatedMapperDependsOnlyOnSqlExecutor() throws Exception {
        String generatedSource = userMapperSource();

        assertTrue(generatedSource.contains("private final SqlExecutor sqlExecutor;"), generatedSource);
        assertTrue(generatedSource.contains("public UserMapperImpl(SqlExecutor sqlExecutor)"), generatedSource);
        assertFalse(generatedSource.contains("SqlEngine"), generatedSource);
        assertFalse(generatedSource.contains("ConnectionProvider"), generatedSource);
        assertFalse(generatedSource.contains("DefaultSqlEngine"), generatedSource);
    }

    @Test
    void generatedMapperUsesDirectBindingAndMappingWithoutReflection() throws Exception {
        String generatedSource = userMapperSource();

        assertTrue(generatedSource.contains("FIND_BY_ID_DEFINITION.bind(id)"), generatedSource);
        assertTrue(generatedSource.contains("new org.liteorm.test.User("), generatedSource);
        for (String forbiddenApi : List.of(
                "Class.forName", ".getClass()", "Method.invoke", "Field.set", "Field.get",
                "Constructor.newInstance", "getDeclaredMethod", "getDeclaredField",
                "BeanUtils", "getParameterValue(")) {
            assertFalse(generatedSource.contains(forbiddenApi), forbiddenApi + "\n" + generatedSource);
        }
    }

    @Test
    void generatedMapperUsesTypedExecutionContractAndDeterministicSource() throws Exception {
        String generatedSource = userMapperSource();

        assertTrue(generatedSource.contains(
            "SqlResult<org.liteorm.test.User> executionResult = sqlExecutor.execute(executionPlan);"));
        assertTrue(generatedSource.contains("return (long) executionResult.getUpdateCount();"));
        assertTrue(generatedSource.contains("Generated Mapper implementation."));
        assertFalse(generatedSource.contains("executionResult.hasError()"));
        assertFalse(generatedSource.contains("throw new RuntimeException(\"SQL execution failed"));
        assertFalse(generatedSource.matches("(?s).*Generated (at|on) .*"), generatedSource);
    }

    private String userMapperSource() throws Exception {
        return Files.readString(GENERATED_CODE_DIRECTORY.resolve("UserMapperImpl.java"));
    }
}
