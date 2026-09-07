package org.liteorm.test;

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

class CursorCompilationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesExplicitCallbackScopedCursorMethod() throws Exception {
        Compilation result = compile("CursorMapper", """
            package org.liteorm.test.cursorfixture;

            import java.sql.ResultSet;
            import java.sql.SQLException;
            import org.liteorm.annotation.*;
            import org.liteorm.api.CursorCallback;
            import org.liteorm.api.RowMapper;

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
            "org/liteorm/test/cursorfixture/CursorMapperImpl.java"));
        assertTrue(generated.contains("return sqlExecutor.queryCursor(executionPlan, callback);"), generated);
        assertTrue(generated.contains("buildScanExecutionPlan(long minimumId)"), generated);
        assertFalse(generated.contains("buildScanExecutionPlan(long minimumId, org.liteorm.api.CursorCallback"), generated);
        assertTrue(generated.contains("params[0] = minimumId;"), generated);
        assertFalse(generated.contains("sqlExecutor.execute(executionPlan)"), generated);
    }

    @Test
    void rejectsRawCursorReturnsAndInvalidCallbackShapes() throws Exception {
        assertFailure("RawCursorReturnMapper", """
            import org.liteorm.api.RowCursor;
            @Mapper interface RawCursorReturnMapper {
                @Select("SELECT name FROM users") RowCursor<String> scan();
            }
            """, "cursor rows cannot escape the callback scope");
        assertFailure("RawCallbackMapper", """
            import org.liteorm.api.CursorCallback;
            @Mapper interface RawCallbackMapper {
                @Select("SELECT name FROM users") int scan(CursorCallback callback);
            }
            """, "CursorCallback must declare row and result types");
        assertFailure("MissingRowMapper", """
            import org.liteorm.api.CursorCallback;
            @Mapper interface MissingRowMapper {
                @Select("SELECT name FROM users") int scan(CursorCallback<String, Integer> callback);
            }
            """, "cursor methods require @UseRowMapper");
        assertFailure("MismatchedCallbackResult", """
            import java.sql.*;
            import org.liteorm.api.*;
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

    @Test
    void rejectsLifecycleBoundStreamsOutsideCursorCallbacks() throws Exception {
        assertFailure("EscapingInputStreamMapper", """
            import java.io.InputStream;
            import java.sql.*;
            import org.liteorm.api.RowMapper;
            class StreamMapper implements RowMapper<InputStream> {
                public StreamMapper() {}
                public InputStream map(ResultSet rows) throws SQLException { return rows.getBinaryStream(1); }
            }
            @Mapper interface EscapingInputStreamMapper {
                @Select("SELECT payload FROM values_table") @UseRowMapper(StreamMapper.class)
                InputStream scan();
            }
            """, "InputStream and Reader results require callback-scoped cursor consumption");
        assertFailure("EscapingReaderMapper", """
            import java.io.Reader;
            import java.sql.*;
            import org.liteorm.api.RowMapper;
            class ReaderMapper implements RowMapper<Reader> {
                public ReaderMapper() {}
                public Reader map(ResultSet rows) throws SQLException { return rows.getCharacterStream(1); }
            }
            @Mapper interface EscapingReaderMapper {
                @Select("SELECT payload FROM values_table") @UseRowMapper(ReaderMapper.class)
                Reader scan();
            }
            """, "InputStream and Reader results require callback-scoped cursor consumption");
    }

    @Test
    void allowsCallbackScopedStreamConsumption() throws Exception {
        Compilation result = compile("StreamCursorMapper", """
            package org.liteorm.test.cursorfixture;

            import java.io.InputStream;
            import java.io.Reader;
            import java.sql.*;
            import org.liteorm.annotation.*;
            import org.liteorm.api.CursorCallback;
            import org.liteorm.api.RowMapper;

            class StreamMapper implements RowMapper<InputStream> {
                public StreamMapper() {}
                public InputStream map(ResultSet rows) throws SQLException { return rows.getBinaryStream(1); }
            }
            class ReaderMapper implements RowMapper<Reader> {
                public ReaderMapper() {}
                public Reader map(ResultSet rows) throws SQLException { return rows.getCharacterStream(1); }
            }

            @Mapper interface StreamCursorMapper {
                @Select("SELECT payload FROM values_table") @UseRowMapper(StreamMapper.class)
                Integer scanBinary(CursorCallback<InputStream, Integer> callback);

                @Select("SELECT payload FROM values_table") @UseRowMapper(ReaderMapper.class)
                Integer scanText(CursorCallback<Reader, Integer> callback);
            }
            """);

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/cursorfixture/StreamCursorMapperImpl.java"));
        assertTrue(generated.contains("return sqlExecutor.queryCursor(executionPlan, callback);"), generated);
    }

    private void assertFailure(String typeName, String body, String expectedMessage) throws Exception {
        Compilation result = compile(typeName, """
            package org.liteorm.test.cursorfixture;
            import org.liteorm.annotation.*;
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
        Path sourceFile = sources.resolve("org/liteorm/test/cursorfixture/" + typeName + ".java");
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
                MapperCompilationTestSupport.withJdbcTypeMappingsSelection(List.of(sourceFile)));
            var task = compiler.getTask(null, manager, diagnostics, List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString(),
                "-s", generated.toString()
            ), null, units);
            task.setProcessors(List.of(new LiteOrmProcessor()));
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
