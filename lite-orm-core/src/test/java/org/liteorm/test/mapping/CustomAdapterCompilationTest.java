package org.liteorm.test.mapping;

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

class CustomAdapterCompilationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesDirectBinderAndRowMapperReferences() throws Exception {
        Compilation result = compile("AdapterMapper", """
            package org.liteorm.test.mappingfixture;

            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Select;
            import org.liteorm.annotation.UseParameterBinder;
            import org.liteorm.annotation.UseRowMapper;
            import org.liteorm.api.ParameterBinder;
            import org.liteorm.api.RowMapper;
            import java.sql.*;
            import java.util.List;

            record JsonValue(String value) {}
            record UnsupportedShape(Long id, JsonValue payload) {}

            class JsonBinder implements ParameterBinder<JsonValue> {
                public JsonBinder() {}
                public void bind(PreparedStatement statement, int index, JsonValue value) throws SQLException {
                    statement.setString(index, value.value());
                }
            }

            class UnsupportedShapeMapper implements RowMapper<UnsupportedShape> {
                public UnsupportedShapeMapper() {}
                public UnsupportedShape map(ResultSet resultSet) throws SQLException {
                    return new UnsupportedShape(resultSet.getLong(1), new JsonValue(resultSet.getString(2)));
                }
            }

            @Mapper
            public interface AdapterMapper {
                @Select("SELECT id, payload FROM adapter_values WHERE payload = #{payload}")
                @UseRowMapper(UnsupportedShapeMapper.class)
                List<UnsupportedShape> find(@UseParameterBinder(JsonBinder.class) JsonValue payload);
            }
            """);

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        String generated = Files.readString(result.generatedDirectory()
            .resolve("org/liteorm/test/mappingfixture/AdapterMapperImpl.java"));
        assertTrue(generated.contains("private final org.liteorm.test.mappingfixture.JsonBinder findPayloadParameterBinder"), generated);
        assertTrue(generated.contains("private final org.liteorm.test.mappingfixture.UnsupportedShapeMapper findRowMapper"), generated);
        assertTrue(generated.contains("findPayloadParameterBinder"), generated);
        assertTrue(generated.contains("findRowMapper"), generated);
        assertFalse(generated.contains("Class.forName"), generated);
        assertFalse(generated.contains("Method.invoke"), generated);
    }

    @Test
    void rejectsIncompatibleRowMapperTarget() throws Exception {
        Compilation result = compile("WrongRowMapper", source(
            "@UseRowMapper(StringRowMapper.class) Result find();",
            "class StringRowMapper implements RowMapper<String> { public String map(ResultSet rs) throws SQLException { return rs.getString(1); } }"));
        assertFailure(result, "WrongRowMapper#find", "row mapper target type");
    }

    @Test
    void rejectsIncompatibleParameterBinderType() throws Exception {
        Compilation result = compile("WrongBinderMapper", source(
            "Result find(@UseParameterBinder(StringBinder.class) JsonValue payload);",
            "class StringBinder implements ParameterBinder<String> { public void bind(PreparedStatement ps, int index, String value) throws SQLException {} }"));
        assertFailure(result, "WrongBinderMapper#find", "parameter binder target type");
    }

    private String source(String method, String adapter) {
        return """
            package org.liteorm.test.mappingfixture;
            import org.liteorm.annotation.*;
            import org.liteorm.annotation.*;
            import org.liteorm.api.*;
            import java.sql.*;
            record JsonValue(String value) {}
            record Result(Long id) {}
            %s
            @Mapper public interface PLACEHOLDER { @Select("SELECT 1") %s }
            """.formatted(adapter, method);
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
        Path file = sources.resolve("org/liteorm/test/mappingfixture/" + mapperName + ".java");
        Files.createDirectories(file.getParent());
        Files.createDirectories(classes);
        Files.createDirectories(generated);
        Files.writeString(file, source.replace("PLACEHOLDER", mapperName), StandardCharsets.UTF_8);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var units = manager.getJavaFileObjectsFromPaths(List.of(file));
            var task = compiler.getTask(null, manager, diagnostics, List.of("--release", "21", "-classpath",
                System.getProperty("java.class.path"), "-d", classes.toString(), "-s", generated.toString()), null, units);
            task.setProcessors(List.of(new LiteOrmProcessor()));
            return new Compilation(task.call(), diagnostics.getDiagnostics(), generated);
        }
    }

    private record Compilation(boolean succeeded, List<Diagnostic<? extends JavaFileObject>> diagnostics,
                               Path generatedDirectory) {}
}
