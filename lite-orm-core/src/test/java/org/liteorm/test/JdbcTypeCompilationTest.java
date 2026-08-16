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

import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcTypeCompilationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesNullSafeDirectConversionsForCommonJdbcTypes() throws Exception {
        CompilationResult result = compile("JdbcTypeMapper", """
            package org.liteorm.test.jdbctypefixture;

            import java.math.BigDecimal;
            import java.time.Instant;
            import java.time.LocalDate;
            import java.time.LocalDateTime;
            import org.liteorm.annotation.Column;
            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Select;

            enum Status { ACTIVE, DISABLED }

            record JdbcTypes(
                Long id,
                Integer quantity,
                BigDecimal amount,
                @Column("business_date") LocalDate businessDate,
                @Column("created_at") LocalDateTime createdAt,
                @Column("occurred_at") Instant occurredAt,
                Status status,
                byte[] payload,
                Boolean enabled
            ) {}

            @Mapper
            interface JdbcTypeMapper {
                @Select("SELECT id, quantity, amount, business_date, created_at, occurred_at, status, payload, enabled FROM jdbc_types")
                JdbcTypes find();
            }
            """);

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/jdbctypefixture/JdbcTypeMapperImpl.java"));
        assertTrue(generated.contains("executionResult.requireColumnIndex(\"business_date\")"), generated);
        assertTrue(generated.contains("executionResult.requireColumnIndex(\"created_at\")"), generated);
        assertTrue(generated.contains("ResultValueConverters.toLong(resultRow[resultColumnIndexes[0]])"), generated);
        assertTrue(generated.contains("ResultValueConverters.toBigDecimal(resultRow[resultColumnIndexes[2]])"), generated);
        assertTrue(generated.contains("ResultValueConverters.toLocalDate(resultRow[resultColumnIndexes[3]])"), generated);
        assertTrue(generated.contains("ResultValueConverters.toLocalDateTime(resultRow[resultColumnIndexes[4]])"), generated);
        assertTrue(generated.contains("ResultValueConverters.toInstant(resultRow[resultColumnIndexes[5]])"), generated);
        assertTrue(generated.contains("Status.valueOf(resultRow[resultColumnIndexes[6]].toString())"), generated);
        assertTrue(generated.contains("(byte[])resultRow[resultColumnIndexes[7]]"), generated);
    }

    private CompilationResult compile(String typeName, String source) throws Exception {
        Path sources = temporaryDirectory.resolve("sources");
        Path classes = temporaryDirectory.resolve("classes");
        Path generated = temporaryDirectory.resolve("generated");
        Path sourceFile = sources.resolve("org/liteorm/test/jdbctypefixture/" + typeName + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classes);
        Files.createDirectories(generated);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        boolean succeeded;
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(
            diagnostics, null, StandardCharsets.UTF_8)) {
            var units = manager.getJavaFileObjectsFromPaths(List.of(sourceFile));
            var task = compiler.getTask(null, manager, diagnostics, List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString(),
                "-s", generated.toString()
            ), null, units);
            task.setProcessors(List.of(new LiteOrmProcessor()));
            succeeded = task.call();
        }
        return new CompilationResult(succeeded, generated, diagnostics.getDiagnostics());
    }

    private record CompilationResult(
        boolean succeeded,
        Path generatedDirectory,
        List<Diagnostic<? extends JavaFileObject>> diagnostics) {

        String diagnosticsText() {
            return diagnostics.toString();
        }
    }
}
