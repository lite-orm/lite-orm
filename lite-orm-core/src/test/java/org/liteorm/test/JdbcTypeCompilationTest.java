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

    @Test
    void generatesScalarRecordAndJavaBeanMappingsForFrozenJdbcTypes() throws Exception {
        CompilationResult result = compile("FrozenJdbcTypeMapper", """
            package org.liteorm.test.jdbctypefixture;

            import java.time.LocalTime;
            import java.time.OffsetDateTime;
            import java.util.UUID;
            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Select;

            record FrozenJdbcTypes(UUID uuid, LocalTime localTime, OffsetDateTime offsetDateTime) {}

            class FrozenJdbcTypeBean {
                private UUID uuid;
                private LocalTime localTime;
                private OffsetDateTime offsetDateTime;

                FrozenJdbcTypeBean() {}

                public void setUuid(UUID uuid) { this.uuid = uuid; }
                public void setLocalTime(LocalTime localTime) { this.localTime = localTime; }
                public void setOffsetDateTime(OffsetDateTime offsetDateTime) { this.offsetDateTime = offsetDateTime; }
            }

            @Mapper
            interface FrozenJdbcTypeMapper {
                @Select("SELECT uuid_value FROM jdbc_types")
                UUID findUuid();

                @Select("SELECT local_time_value FROM jdbc_types")
                LocalTime findLocalTime();

                @Select("SELECT offset_date_time_value FROM jdbc_types")
                OffsetDateTime findOffsetDateTime();

                @Select("SELECT uuid, local_time, offset_date_time FROM jdbc_types")
                FrozenJdbcTypes findRecord();

                @Select("SELECT uuid, local_time, offset_date_time FROM jdbc_types")
                FrozenJdbcTypeBean findBean();
            }
            """);

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/jdbctypefixture/FrozenJdbcTypeMapperImpl.java"));
        assertTrue(generated.contains("ResultValueConverters.toUuid(resultRow[0])"), generated);
        assertTrue(generated.contains("ResultValueConverters.toLocalTime(resultRow[0])"), generated);
        assertTrue(generated.contains("ResultValueConverters.toOffsetDateTime(resultRow[0])"), generated);
        assertTrue(generated.contains("ResultValueConverters.toUuid(row[resultColumnIndexes[0]])"), generated);
        assertTrue(generated.contains("ResultValueConverters.toLocalTime(row[resultColumnIndexes[1]])"), generated);
        assertTrue(generated.contains("ResultValueConverters.toOffsetDateTime(row[resultColumnIndexes[2]])"), generated);
    }

    @Test
    void rejectsUnsupportedJdbcResultTypeWithRowMapperGuidance() throws Exception {
        CompilationResult result = compile("UnsupportedJdbcTypeMapper", """
            package org.liteorm.test.jdbctypefixture;

            import java.time.OffsetTime;
            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Select;

            @Mapper
            interface UnsupportedJdbcTypeMapper {
                @Select("SELECT offset_time_value FROM jdbc_types")
                OffsetTime findOffsetTime();
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains("requires @UseRowMapper"), result::diagnosticsText);
    }

    @Test
    void rejectsUnsupportedJdbcParameterTypeWithBinderGuidance() throws Exception {
        CompilationResult result = compile("UnsupportedJdbcParameterMapper", """
            package org.liteorm.test.jdbctypefixture;

            import java.time.OffsetTime;
            import org.liteorm.annotation.Insert;
            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Param;

            @Mapper
            interface UnsupportedJdbcParameterMapper {
                @Insert("INSERT INTO jdbc_types (offset_time_value) VALUES (#{value})")
                int insertOffsetTime(@Param("value") OffsetTime value);
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains("requires @UseParameterBinder"), result::diagnosticsText);
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
