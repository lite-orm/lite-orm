package org.liteorm.test.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.liteorm.compile.LiteOrmProcessor;

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
            package org.liteorm.test.providerfixture;

            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.UseSqlProvider;
            import org.liteorm.api.BoundParameter;
            import org.liteorm.api.BoundSql;
            import org.liteorm.api.ExecutionPlan;
            import org.liteorm.api.SqlProvider;
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
            .resolve("org/liteorm/test/providerfixture/ValidProviderMapperImpl.java"));
        assertTrue(generated.contains("private final org.liteorm.test.providerfixture.IdProvider findSqlProvider = new org.liteorm.test.providerfixture.IdProvider();"), generated);
        assertTrue(generated.contains("findSqlProvider.provide(query)"), generated);
        assertTrue(generated.contains("boundSql.parameterBinders()"), generated);
        assertTrue(generated.contains("boundSql.parameterTypes()"), generated);
        assertTrue(generated.contains("boundSql.parameterJdbcTypes()"), generated);
        assertFalse(generated.contains("Class.forName"), generated);
        assertFalse(generated.contains("Method.invoke"), generated);
    }

    @Test
    void generatesDirectRowMapperForProviderSingleAndListResults() throws Exception {
        Compilation result = compile("ProviderRowMapperMapper", """
            package org.liteorm.test.providerfixture;

            import org.liteorm.annotation.*;
            import org.liteorm.api.*;
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
            .resolve("org/liteorm/test/providerfixture/ProviderRowMapperMapperImpl.java"));
        assertTrue(generated.contains("findOneRowMapper"), generated);
        assertTrue(generated.contains("findAllRowMapper"), generated);
        assertFalse(generated.contains("Class.forName"), generated);
        assertFalse(generated.contains("Method.invoke"), generated);
    }

    @Test
    void rejectsProviderCombinedWithSqlAnnotation() throws Exception {
        Compilation result = compile("ConflictingProviderMapper", source("""
            @org.liteorm.annotation.Select("SELECT id FROM users")
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
            Result find(@org.liteorm.annotation.UseParameterBinder(QueryBinder.class) Query query);
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
            package org.liteorm.test.providerfixture;
            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.UseSqlProvider;
            import org.liteorm.api.*;
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
        Path file = sources.resolve("org/liteorm/test/providerfixture/" + mapperName + ".java");
        Files.createDirectories(file.getParent());
        Files.createDirectories(classes);
        Files.createDirectories(generated);
        Files.writeString(file, source.replace("PLACEHOLDER", mapperName), StandardCharsets.UTF_8);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var units = manager.getJavaFileObjectsFromPaths(
                org.liteorm.test.MapperCompilationTestSupport.withJdbcTypeMappingsSelection(List.of(file)));
            var task = compiler.getTask(null, manager, diagnostics, List.of("--release", "21", "-classpath",
                System.getProperty("java.class.path"), "-d", classes.toString(), "-s", generated.toString()), null, units);
            task.setProcessors(List.of(new LiteOrmProcessor()));
            return new Compilation(task.call(), diagnostics.getDiagnostics(), generated);
        }
    }

    private record Compilation(boolean succeeded, List<Diagnostic<? extends JavaFileObject>> diagnostics,
                               Path generatedDirectory) {}
}
