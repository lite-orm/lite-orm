package io.github.lynxus.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.lynxus.compile.LynxusProcessor;

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

class CursorCompilationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesExplicitCallbackScopedCursorMethod() throws Exception {
        Compilation result = compile("CursorMapper", """
            package io.github.lynxus.test.cursorfixture;

            import java.sql.ResultSet;
            import java.sql.SQLException;
            import io.github.lynxus.annotation.*;
            import io.github.lynxus.api.CursorCallback;
            import io.github.lynxus.api.RowMapper;

            class NameRowMapper implements RowMapper<String> {
                public NameRowMapper() {}
                public String map(ResultSet resultSet) throws SQLException {
                    return resultSet.getString(1);
                }
            }

            @Mapper
            interface CursorMapper {
                @Select("SELECT name FROM users WHERE id >= #{minimumId}")
                @UseRowMapper(NameRowMapper.class)
                Integer scan(long minimumId, CursorCallback<String, Integer> callback);
            }
            """);

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "io/github/lynxus/test/cursorfixture/CursorMapperImpl.java"));
        assertTrue(generated.contains("return sqlExecutor.queryCursor(executionPlan, callback);"), generated);
        assertTrue(generated.contains("buildScanExecutionPlan(long minimumId)"), generated);
        assertFalse(generated.contains("buildScanExecutionPlan(long minimumId, io.github.lynxus.api.CursorCallback"), generated);
        assertTrue(generated.contains("params[0] = minimumId;"), generated);
        assertFalse(generated.contains("sqlExecutor.execute(executionPlan)"), generated);
    }

    @Test
    void rejectsRawCursorReturnsAndInvalidCallbackShapes() throws Exception {
        assertFailure("RawCursorReturnMapper", """
            import io.github.lynxus.api.RowCursor;
            @Mapper interface RawCursorReturnMapper {
                @Select("SELECT name FROM users") RowCursor<String> scan();
            }
            """, "cursor rows cannot escape the callback scope");
        assertFailure("RawCallbackMapper", """
            import io.github.lynxus.api.CursorCallback;
            @Mapper interface RawCallbackMapper {
                @Select("SELECT name FROM users") int scan(CursorCallback callback);
            }
            """, "CursorCallback must declare row and result types");
        assertFailure("MissingRowMapper", """
            import io.github.lynxus.api.CursorCallback;
            @Mapper interface MissingRowMapper {
                @Select("SELECT name FROM users") int scan(CursorCallback<String, Integer> callback);
            }
            """, "cursor methods require @UseRowMapper");
        assertFailure("MismatchedCallbackResult", """
            import java.sql.*;
            import io.github.lynxus.api.*;
            class NameMapper implements RowMapper<String> {
                public NameMapper() {}
                public String map(ResultSet rows) throws SQLException { return rows.getString(1); }
            }
            @Mapper interface MismatchedCallbackResult {
                @Select("SELECT name FROM users") @UseRowMapper(NameMapper.class)
                String scan(CursorCallback<String, Integer> callback);
            }
            """, "cursor callback result type java.lang.Integer does not match Mapper return type java.lang.String");
    }

    private void assertFailure(String typeName, String body, String expectedMessage) throws Exception {
        Compilation result = compile(typeName, """
            package io.github.lynxus.test.cursorfixture;
            import io.github.lynxus.annotation.*;
            %s
            """.formatted(body));
        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(typeName + "#scan"), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(expectedMessage), result::diagnosticsText);
    }

    private Compilation compile(String typeName, String source) throws Exception {
        Path sources = temporaryDirectory.resolve(typeName + "/sources");
        Path classes = temporaryDirectory.resolve(typeName + "/classes");
        Path generated = temporaryDirectory.resolve(typeName + "/generated");
        Path sourceFile = sources.resolve("io/github/lynxus/test/cursorfixture/" + typeName + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classes);
        Files.createDirectories(generated);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        boolean succeeded;
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            var units = manager.getJavaFileObjectsFromPaths(
                MapperCompilationTestSupport.compilationUnits(List.of(sourceFile)));
            var task = compiler.getTask(null, manager, diagnostics, List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString(),
                "-s", generated.toString()
            ), null, units);
            task.setProcessors(List.of(new LynxusProcessor()));
            succeeded = task.call();
        }
        return new Compilation(succeeded, generated, diagnostics.getDiagnostics());
    }

    private record Compilation(
            boolean succeeded,
            Path generatedDirectory,
            List<Diagnostic<? extends JavaFileObject>> diagnostics) {

        String diagnosticsText() {
            return diagnostics.toString();
        }
    }
}
