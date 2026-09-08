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
        assertTrue(generated.contains("ResultValueConverters.toLong(resultRow[resultColumnIndexes[0]])"), generated);
        assertTrue(generated.contains("ResultValueConverters.toBigDecimal(resultRow[resultColumnIndexes[2]])"), generated);
        assertTrue(generated.contains("ResultValueConverters.toLocalDate(resultRow[resultColumnIndexes[3]])"), generated);
        assertTrue(generated.contains("ResultValueConverters.toLocalDateTime(resultRow[resultColumnIndexes[4]])"), generated);
        assertTrue(generated.contains("ResultValueConverters.toInstant(resultRow[resultColumnIndexes[5]])"), generated);
        assertTrue(generated.contains("Status.valueOf(resultRow[resultColumnIndexes[6]].toString())"), generated);
        assertTrue(generated.contains("instanceof Number ? ResultValueConverters.toEnumOrdinal("), generated);
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

                @Insert("INSERT INTO values_table (binary_value, enabled, initial_value, month_value, status) "
                    + "VALUES (#{binaryValue}, #{enabled,jdbcType=BOOLEAN}, #{initial,jdbcType=VARCHAR}, "
                    + "#{monthValue}, #{status,jdbcType=INTEGER})")
                int insert(
                    @Param("binaryValue") Byte[] binaryValue,
                    @Param("enabled") boolean enabled,
                    @Param("initial") char initial,
                    @Param("monthValue") Month monthValue,
                    @Param("status") Status status);
            }
            """);

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/jdbctypefixture/StandardJdbcTypeMapperImpl.java"));
        assertTrue(generated.contains("ResultValueConverters.toBigInteger("), generated);
        assertTrue(generated.contains("ResultValueConverters.toBoxedBytes("), generated);
        assertTrue(generated.contains("ResultValueConverters.toUtilDate("), generated);
        assertTrue(generated.contains("ResultValueConverters.toSqlDate("), generated);
        assertTrue(generated.contains("ResultValueConverters.toSqlTime("), generated);
        assertTrue(generated.contains("ResultValueConverters.toSqlTimestamp("), generated);
        assertTrue(generated.contains("ResultValueConverters.toYear("), generated);
        assertTrue(generated.contains("ResultValueConverters.toMonth("), generated);
        assertTrue(generated.contains("ResultValueConverters.toYearMonth("), generated);
        assertTrue(generated.contains("ResultValueConverters.toJapaneseDate("), generated);
        assertTrue(generated.contains("mapped.setIntegerValue(ResultValueConverters.toBigInteger("), generated);
        assertTrue(generated.contains("mapped.setYearMonthValue(ResultValueConverters.toYearMonth("), generated);
        assertTrue(generated.contains(
            "typeHandlerManager.parameterBinder(java.lang.Byte[].class, java.sql.JDBCType.VARBINARY)"), generated);
        assertTrue(generated.contains(
            "typeHandlerManager.parameterBinder(boolean.class, java.sql.JDBCType.BOOLEAN)"), generated);
        assertTrue(generated.contains(
            "typeHandlerManager.parameterBinder(char.class, java.sql.JDBCType.VARCHAR)"), generated);
        assertTrue(generated.contains(
            "typeHandlerManager.parameterBinder(java.time.Month.class, java.sql.JDBCType.INTEGER)"), generated);
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
