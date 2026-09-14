package io.github.kervix.test.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.kervix.compile.KervixProcessor;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlProviderCompilationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesDirectProviderInvocationWithoutReflection() throws Exception {
        Compilation result = compile("ValidProviderMapper", """
            package io.github.kervix.test.providerfixture;

            import io.github.kervix.annotation.Mapper;
            import io.github.kervix.annotation.UseSqlProvider;
            import io.github.kervix.api.BoundParameter;
            import io.github.kervix.api.BoundSql;
            import io.github.kervix.api.ExecutionPlan;
            import io.github.kervix.api.SqlProvider;
            import java.util.List;

            record Query(Long minimumId) {}
            record Result(Long id) {}

            class IdProvider implements SqlProvider<Query> {
                public IdProvider() {}
                public BoundSql provide(Query query) {
                    return new BoundSql("SELECT id FROM users WHERE id >= ?", List.of(BoundParameter.of(Long.class, query.minimumId())));
                }
            }

            @Mapper
            public interface ValidProviderMapper {
                @UseSqlProvider(value = IdProvider.class, statementType = ExecutionPlan.StatementType.SELECT)
                List<Result> find(Query query);
            }
            """);

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String generated = Files.readString(result.generatedDirectory()
            .resolve("org/kervix/test/providerfixture/ValidProviderMapperImpl.java"));
        assertTrue(generated.contains("private final io.github.kervix.test.providerfixture.IdProvider findSqlProvider = new io.github.kervix.test.providerfixture.IdProvider();"), generated);
        assertTrue(generated.contains("findSqlProvider.provide(query)"), generated);
        assertTrue(generated.contains("QueryDefinition<io.github.kervix.test.providerfixture.Result>"), generated);
        assertTrue(generated.contains("return FIND_DEFINITION.bind(boundSql);"), generated);
        assertFalse(generated.contains("boundSql.parameterBinders()"), generated);
        assertFalse(generated.contains("return new QueryExecutionPlan<>("), generated);
        assertFalse(generated.contains("Class.forName"), generated);
        assertFalse(generated.contains("Method.invoke"), generated);
    }

    @Test
    void generatesDirectRowMapperForProviderSingleAndListResults() throws Exception {
        Compilation result = compile("ProviderRowMapperMapper", """
            package io.github.kervix.test.providerfixture;

            import io.github.kervix.annotation.*;
            import io.github.kervix.api.*;
            import java.sql.*;
            import java.util.List;

            record Query(Long id) {}
            record Result(Long id) {}

            class ResultProvider implements SqlProvider<Query> {
                public ResultProvider() {}
                public BoundSql provide(Query query) {
                    return new BoundSql("SELECT id FROM users WHERE id = ?", List.of(BoundParameter.of(Long.class, query.id())));
                }
            }

            class ResultRowMapper implements RowMapper<Result> {
                public ResultRowMapper() {}
                public Result map(ResultSet resultSet) throws SQLException { return new Result(resultSet.getLong(1)); }
            }

            @Mapper
            public interface ProviderRowMapperMapper {
                @UseSqlProvider(value = ResultProvider.class, statementType = ExecutionPlan.StatementType.SELECT)
                @UseRowMapper(ResultRowMapper.class)
                Result findOne(Query query);

                @UseSqlProvider(value = ResultProvider.class, statementType = ExecutionPlan.StatementType.SELECT)
                @UseRowMapper(ResultRowMapper.class)
                List<Result> findAll(Query query);
            }
            """);

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String generated = Files.readString(result.generatedDirectory()
            .resolve("org/kervix/test/providerfixture/ProviderRowMapperMapperImpl.java"));
        assertTrue(generated.contains("findOneRowMapper"), generated);
        assertTrue(generated.contains("findAllRowMapper"), generated);
        assertTrue(generated.contains("QueryDefinition.rowMapped("), generated);
        assertTrue(generated.contains("return FIND_ONE_DEFINITION.bind(boundSql);"), generated);
        assertFalse(generated.contains("Class.forName"), generated);
        assertFalse(generated.contains("Method.invoke"), generated);
    }

    @Test
    void rejectsProviderCombinedWithSqlAnnotation() throws Exception {
        Compilation result = compile("ConflictingProviderMapper", source("""
            @io.github.kervix.annotation.Select("SELECT id FROM users")
            @UseSqlProvider(value = IdProvider.class, statementType = ExecutionPlan.StatementType.SELECT)
            Result find(Query query);
            """, "class IdProvider implements SqlProvider<Query> { public BoundSql provide(Query query) { return new BoundSql(\"SELECT 1\", List.of()); } }"));
        assertFailure(result, "ConflictingProviderMapper#find", "SQL provider cannot be combined with XML or SQL annotations");
    }

    @Test
    void rejectsProviderInputTypeMismatch() throws Exception {
        Compilation result = compile("MismatchedProviderMapper", source("""
            @UseSqlProvider(value = IdProvider.class, statementType = ExecutionPlan.StatementType.SELECT)
            Result find(String query);
            """, "class IdProvider implements SqlProvider<Query> { public BoundSql provide(Query query) { return new BoundSql(\"SELECT 1\", List.of()); } }"));
        assertFailure(result, "MismatchedProviderMapper#find", "provider input type");
    }

    @Test
    void rejectsProviderWithoutAccessibleNoArgConstructor() throws Exception {
        Compilation result = compile("ProviderConstructorMapper", source("""
            @UseSqlProvider(value = IdProvider.class, statementType = ExecutionPlan.StatementType.SELECT)
            Result find(Query query);
            """, "class IdProvider implements SqlProvider<Query> { IdProvider(String value) {} public BoundSql provide(Query query) { return new BoundSql(\"SELECT 1\", List.of()); } }"));
        assertFailure(result, "ProviderConstructorMapper#find", "requires an accessible no-arg constructor");
    }

    @Test
    void rejectsMultipleMapperParameters() throws Exception {
        Compilation result = compile("MultipleParameterProviderMapper", source("""
            @UseSqlProvider(value = IdProvider.class, statementType = ExecutionPlan.StatementType.SELECT)
            Result find(Query query, String extra);
            """, "class IdProvider implements SqlProvider<Query> { public BoundSql provide(Query query) { return new BoundSql(\"SELECT 1\", List.of()); } }"));
        assertFailure(result, "MultipleParameterProviderMapper#find", "wrap multiple values in a record");
    }

    @Test
    void rejectsMapperParameterBinderAnnotationForProviderMethods() throws Exception {
        Compilation result = compile("ProviderBinderAnnotationMapper", source("""
            @UseSqlProvider(value = IdProvider.class, statementType = ExecutionPlan.StatementType.SELECT)
            Result find(@io.github.kervix.annotation.UseParameterBinder(QueryBinder.class) Query query);
            """, """
            class IdProvider implements SqlProvider<Query> {
                public IdProvider() {}
                public BoundSql provide(Query query) { return new BoundSql("SELECT 1", List.of()); }
            }
            class QueryBinder implements ParameterBinder<Query> {
                public QueryBinder() {}
                public void bind(java.sql.PreparedStatement statement, int index, Query value) {}
            }
            """));
        assertFailure(result, "ProviderBinderAnnotationMapper#find", "typed BoundParameter contract");
    }

    private String source(String method, String provider) {
        return """
            package io.github.kervix.test.providerfixture;
            import io.github.kervix.annotation.Mapper;
            import io.github.kervix.annotation.UseSqlProvider;
            import io.github.kervix.api.*;
            import java.util.List;
            record Query(Long minimumId) {}
            record Result(Long id) {}
            %s
            @Mapper public interface PLACEHOLDER { %s }
            """.formatted(provider, method);
    }

    private void assertFailure(Compilation result, String location, String message) {
        assertFalse(result.succeeded(), () -> result.diagnostics().toString());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
            diagnostic.getKind() == Diagnostic.Kind.ERROR
                && diagnostic.getMessage(null).contains(location)
                && diagnostic.getMessage(null).contains(message)), () -> result.diagnostics().toString());
    }

    private Compilation compile(String mapperName, String source) throws Exception {
        Path sources = temporaryDirectory.resolve(mapperName + "/sources");
        Path classes = temporaryDirectory.resolve(mapperName + "/classes");
        Path generated = temporaryDirectory.resolve(mapperName + "/generated");
        Path file = sources.resolve("org/kervix/test/providerfixture/" + mapperName + ".java");
        Files.createDirectories(file.getParent());
        Files.createDirectories(classes);
        Files.createDirectories(generated);
        Files.writeString(file, source.replace("PLACEHOLDER", mapperName), StandardCharsets.UTF_8);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var units = manager.getJavaFileObjectsFromPaths(
                io.github.kervix.test.MapperCompilationTestSupport.compilationUnits(List.of(file)));
            var task = compiler.getTask(null, manager, diagnostics, List.of("--release", "21", "-classpath",
                System.getProperty("java.class.path"), "-d", classes.toString(), "-s", generated.toString()), null, units);
            task.setProcessors(List.of(new KervixProcessor()));
            return new Compilation(task.call(), diagnostics.getDiagnostics(), generated);
        }
    }

    private record Compilation(boolean succeeded, List<Diagnostic<? extends JavaFileObject>> diagnostics,
                               Path generatedDirectory) {}
}
