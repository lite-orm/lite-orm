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
    void generatesDirectTargetTypeAssignmentsForCommonJdbcTypes() throws Exception {
        CompilationResult result = compile("JdbcTypeMapper", """
            package org.liteorm.test.jdbctypefixture;

            import java.math.BigDecimal;
            import java.time.Instant;
            import java.time.LocalDate;
            import java.time.LocalDateTime;
            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Select;

            enum Status { ACTIVE, DISABLED }

            record JdbcTypes(
                Long id,
                Integer quantity,
                BigDecimal amount,
                LocalDate businessDate,
                LocalDateTime createdAt,
                Instant occurredAt,
                Status status,
                byte[] payload,
                Boolean enabled
            ) {}

            @Mapper
            interface JdbcTypeMapper {
                @Select("SELECT id, quantity, amount, business_date AS businessDate, "
                    + "created_at AS createdAt, occurred_at AS occurredAt, status, payload, enabled FROM jdbc_types")
                JdbcTypes find();
            }
            """);

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/jdbctypefixture/JdbcTypeMapperImpl.java"));
        assertTrue(generated.contains("executionResult.requireColumnIndex(\"businessDate\")"), generated);
        assertTrue(generated.contains("executionResult.requireColumnIndex(\"createdAt\")"), generated);
        assertTrue(generated.contains("(java.lang.Long)resultRow[resultColumnIndexes[0]]"), generated);
        assertTrue(generated.contains("(java.math.BigDecimal)resultRow[resultColumnIndexes[2]]"), generated);
        assertTrue(generated.contains("(java.time.LocalDate)resultRow[resultColumnIndexes[3]]"), generated);
        assertTrue(generated.contains("(java.time.LocalDateTime)resultRow[resultColumnIndexes[4]]"), generated);
        assertTrue(generated.contains("(java.time.Instant)resultRow[resultColumnIndexes[5]]"), generated);
        assertTrue(generated.contains("(org.liteorm.test.jdbctypefixture.Status)resultRow[resultColumnIndexes[6]]"), generated);
        assertTrue(generated.contains("(byte[])resultRow[resultColumnIndexes[7]]"), generated);
        assertFalse(generated.contains("ResultValueConverters"), generated);
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
        assertTrue(generated.contains("(java.util.UUID)resultRow[0]"), generated);
        assertTrue(generated.contains("(java.time.LocalTime)resultRow[0]"), generated);
        assertTrue(generated.contains("(java.time.OffsetDateTime)resultRow[0]"), generated);
        assertTrue(generated.contains("(java.util.UUID)row[resultColumnIndexes[0]]"), generated);
        assertTrue(generated.contains("(java.time.LocalTime)row[resultColumnIndexes[1]]"), generated);
        assertTrue(generated.contains("(java.time.OffsetDateTime)row[resultColumnIndexes[2]]"), generated);
        assertFalse(generated.contains("ResultValueConverters"), generated);
    }

    @Test
    void generatesRecordAndJavaBeanMappingsForStandardScalarTypes() throws Exception {
        CompilationResult result = compile("StandardJdbcTypeMapper", """
            package org.liteorm.test.jdbctypefixture;

            import java.math.BigInteger;
            import java.time.Month;
            import java.time.Year;
            import java.time.YearMonth;
            import java.time.chrono.JapaneseDate;
            import org.liteorm.annotation.Insert;
            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Param;
            import org.liteorm.annotation.Select;

            enum Status { ACTIVE, DISABLED }

            record StandardJdbcTypes(
                BigInteger integerValue,
                Byte[] binaryValue,
                java.util.Date utilDate,
                java.sql.Date sqlDate,
                java.sql.Time sqlTime,
                java.sql.Timestamp sqlTimestamp,
                Year yearValue,
                Month monthValue,
                YearMonth yearMonthValue,
                JapaneseDate japaneseDate
            ) {}

            class StandardJdbcTypeBean {
                private BigInteger integerValue;
                private YearMonth yearMonthValue;

                StandardJdbcTypeBean() {}

                public void setIntegerValue(BigInteger integerValue) {
                    this.integerValue = integerValue;
                }

                public void setYearMonthValue(YearMonth yearMonthValue) {
                    this.yearMonthValue = yearMonthValue;
                }
            }

            @Mapper
            interface StandardJdbcTypeMapper {
                @Select("SELECT integer_value, binary_value, util_date, sql_date, sql_time, "
                    + "sql_timestamp, year_value, month_value, year_month_value, japanese_date FROM values_table")
                StandardJdbcTypes find();

                @Select("SELECT integer_value, year_month_value FROM values_table")
                StandardJdbcTypeBean findBean();

                @Insert("INSERT INTO values_table (binary_value, enabled, initial_value, month_value, status, "
                    + "util_date_only, util_time_only) VALUES (#{binaryValue}, #{enabled,jdbcType=BOOLEAN}, "
                    + "#{initial,jdbcType=VARCHAR}, #{monthValue}, #{status,jdbcType=INTEGER}, "
                    + "#{utilDateOnly,jdbcType=DATE}, #{utilTimeOnly,jdbcType=TIME})")
                int insert(
                    @Param("binaryValue") Byte[] binaryValue,
                    @Param("enabled") boolean enabled,
                    @Param("initial") char initial,
                    @Param("monthValue") Month monthValue,
                    @Param("status") Status status,
                    @Param("utilDateOnly") java.util.Date utilDateOnly,
                    @Param("utilTimeOnly") java.util.Date utilTimeOnly);
            }
            """);

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/jdbctypefixture/StandardJdbcTypeMapperImpl.java"));
        assertTrue(generated.contains("(java.math.BigInteger)resultRow[resultColumnIndexes[0]]"), generated);
        assertTrue(generated.contains("(java.lang.Byte[])resultRow[resultColumnIndexes[1]]"), generated);
        assertTrue(generated.contains("(java.util.Date)resultRow[resultColumnIndexes[2]]"), generated);
        assertTrue(generated.contains("(java.sql.Date)resultRow[resultColumnIndexes[3]]"), generated);
        assertTrue(generated.contains("(java.sql.Time)resultRow[resultColumnIndexes[4]]"), generated);
        assertTrue(generated.contains("(java.sql.Timestamp)resultRow[resultColumnIndexes[5]]"), generated);
        assertTrue(generated.contains("(java.time.Year)resultRow[resultColumnIndexes[6]]"), generated);
        assertTrue(generated.contains("(java.time.Month)resultRow[resultColumnIndexes[7]]"), generated);
        assertTrue(generated.contains("(java.time.YearMonth)resultRow[resultColumnIndexes[8]]"), generated);
        assertTrue(generated.contains("(java.time.chrono.JapaneseDate)resultRow[resultColumnIndexes[9]]"), generated);
        assertTrue(generated.contains("mapped.setIntegerValue((java.math.BigInteger)"), generated);
        assertTrue(generated.contains("mapped.setYearMonthValue((java.time.YearMonth)"), generated);
        assertFalse(generated.contains("ResultValueConverters"), generated);
        assertFalse(generated.contains("TypeHandlerManager"), generated);
        assertTrue(generated.contains(
            "new Class<?>[]{java.lang.Byte[].class, boolean.class, char.class, java.time.Month.class, "
                + "org.liteorm.test.jdbctypefixture.Status.class, java.util.Date.class, java.util.Date.class}"),
            generated);
        assertTrue(generated.contains(
            "new java.sql.JDBCType[]{java.sql.JDBCType.VARBINARY, java.sql.JDBCType.BOOLEAN, "
                + "java.sql.JDBCType.VARCHAR, java.sql.JDBCType.INTEGER, java.sql.JDBCType.INTEGER, "
                + "java.sql.JDBCType.DATE, java.sql.JDBCType.TIME}"), generated);
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
                MapperCompilationTestSupport.compilationUnits(List.of(sourceFile)));
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
