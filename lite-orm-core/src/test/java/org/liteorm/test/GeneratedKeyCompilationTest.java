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

class GeneratedKeyCompilationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesExplicitScalarConversionsForOptedInInsert() throws Exception {
        CompilationResult result = compile("GeneratedKeyMapper", """
            package org.liteorm.test.generatedkeyfixture;

            import java.math.BigDecimal;
            import java.math.BigInteger;
            import org.liteorm.annotation.GeneratedKey;
            import org.liteorm.annotation.Insert;
            import org.liteorm.annotation.Mapper;

            @Mapper
            interface GeneratedKeyMapper {
                @GeneratedKey("id") @Insert("INSERT INTO users (name) VALUES (#{name})") int insertInt(String name);
                @GeneratedKey("id") @Insert("INSERT INTO users (name) VALUES (#{name})") Integer insertInteger(String name);
                @GeneratedKey("id") @Insert("INSERT INTO users (name) VALUES (#{name})") long insertLong(String name);
                @GeneratedKey("id") @Insert("INSERT INTO users (name) VALUES (#{name})") Long insertBoxedLong(String name);
                @GeneratedKey("id") @Insert("INSERT INTO users (name) VALUES (#{name})") Short insertShort(String name);
                @GeneratedKey("id") @Insert("INSERT INTO users (name) VALUES (#{name})") Byte insertByte(String name);
                @GeneratedKey("id") @Insert("INSERT INTO users (name) VALUES (#{name})") Double insertDouble(String name);
                @GeneratedKey("id") @Insert("INSERT INTO users (name) VALUES (#{name})") Float insertFloat(String name);
                @GeneratedKey("id") @Insert("INSERT INTO users (name) VALUES (#{name})") BigDecimal insertDecimal(String name);
                @GeneratedKey("id") @Insert("INSERT INTO users (name) VALUES (#{name})") BigInteger insertBigInteger(String name);
                @GeneratedKey("id") @Insert("INSERT INTO users (name) VALUES (#{name})") String insertString(String name);
            }
            """);

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/generatedkeyfixture/GeneratedKeyMapperImpl.java"));
        assertTrue(generated.contains("ResultValueConverters.toInteger(executionResult.getGeneratedKey())"));
        assertTrue(generated.contains("ResultValueConverters.toLong(executionResult.getGeneratedKey())"));
        assertTrue(generated.contains("ResultValueConverters.toShort(executionResult.getGeneratedKey())"));
        assertTrue(generated.contains("ResultValueConverters.toByte(executionResult.getGeneratedKey())"));
        assertTrue(generated.contains("ResultValueConverters.toDouble(executionResult.getGeneratedKey())"));
        assertTrue(generated.contains("ResultValueConverters.toFloat(executionResult.getGeneratedKey())"));
        assertTrue(generated.contains("ResultValueConverters.toBigDecimal(executionResult.getGeneratedKey())"));
        assertTrue(generated.contains("ResultValueConverters.toBigInteger(executionResult.getGeneratedKey())"));
        assertTrue(generated.contains("ResultValueConverters.toStringValue(executionResult.getGeneratedKey())"));
        assertTrue(generated.contains("new ExecutionPlan.TypeRouting("));
        assertFalse(generated.contains("TypeHandlerManager"), generated);
    }

    @Test
    void supportsCustomRowMapperForNonScalarGeneratedKeys() throws Exception {
        CompilationResult result = compile("GeneratedUuidKeyMapper", """
            package org.liteorm.test.generatedkeyfixture;

            import java.sql.ResultSet;
            import java.sql.SQLException;
            import java.util.UUID;
            import org.liteorm.annotation.*;
            import org.liteorm.api.RowMapper;

            class UuidKeyRowMapper implements RowMapper<UUID> {
                public UuidKeyRowMapper() {}
                public UUID map(ResultSet resultSet) throws SQLException {
                    return UUID.fromString(resultSet.getString(1));
                }
            }

            @Mapper interface GeneratedUuidKeyMapper {
                @GeneratedKey("id")
                @UseRowMapper(UuidKeyRowMapper.class)
                @Insert("INSERT INTO users (name) VALUES (#{name})")
                UUID insert(String name);
            }
            """);

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/generatedkeyfixture/GeneratedUuidKeyMapperImpl.java"));
        assertTrue(generated.contains("UuidKeyRowMapper insertRowMapper"));
        assertTrue(generated.contains("return (java.util.UUID) executionResult.getGeneratedKey();"));
        assertTrue(generated.contains("insertRowMapper, StatementOptions.defaults()"));
    }

    @Test
    void rejectsNonInsertAndUnsupportedReturnTypeWithoutRowMapper() throws Exception {
        CompilationResult update = compile("GeneratedKeyUpdateMapper", """
            package org.liteorm.test.generatedkeyfixture;
            import org.liteorm.annotation.*;
            @Mapper interface GeneratedKeyUpdateMapper {
                @GeneratedKey("id") @Update("UPDATE users SET name = #{name}")
                Long update(String name);
            }
            """);
        CompilationResult unsupportedReturn = compile("GeneratedKeyUuidMapper", """
            package org.liteorm.test.generatedkeyfixture;
            import java.util.UUID;
            import org.liteorm.annotation.*;
            @Mapper interface GeneratedKeyUuidMapper {
                @GeneratedKey("id") @Insert("INSERT INTO users (name) VALUES (#{name})")
                UUID insert(String name);
            }
            """);

        assertFailure(update, "generated keys require an INSERT statement");
        assertFailure(unsupportedReturn, "generated-key return type java.util.UUID requires @UseRowMapper");
    }

    @Test
    void rejectsBlankGeneratedKeyColumn() throws Exception {
        CompilationResult result = compile("BlankGeneratedKeyMapper", """
            package org.liteorm.test.generatedkeyfixture;
            import org.liteorm.annotation.*;
            @Mapper interface BlankGeneratedKeyMapper {
                @GeneratedKey(" ") @Insert("INSERT INTO users (name) VALUES (#{name})")
                Long insert(String name);
            }
            """);

        assertFailure(result, "generated-key column must not be blank");
    }

    @Test
    void rejectsBatchDynamicSqlAndProviderCombinations() throws Exception {
        CompilationResult batch = compile("GeneratedKeyBatchMapper", """
            package org.liteorm.test.generatedkeyfixture;
            import java.util.List;
            import org.liteorm.annotation.*;
            @Mapper interface GeneratedKeyBatchMapper {
                @GeneratedKey("id") @Batch("INSERT INTO users (name) VALUES (#{item})")
                int[] insert(List<String> names);
            }
            """);
        CompilationResult dynamic = compile("GeneratedKeyDynamicMapper", """
            package org.liteorm.test.generatedkeyfixture;
            import org.liteorm.annotation.*;
            @Mapper interface GeneratedKeyDynamicMapper {
                @GeneratedKey("id") @Insert({"<script>", "INSERT INTO users (name)",
                    "<if test='name != null'>VALUES (#{name})</if>", "</script>"})
                Long insert(String name);
            }
            """);
        CompilationResult provider = compile("GeneratedKeyProviderMapper", """
            package org.liteorm.test.generatedkeyfixture;
            import java.util.List;
            import org.liteorm.annotation.*;
            import org.liteorm.api.*;
            class InsertProvider implements SqlProvider<String> {
                public InsertProvider() {}
                public BoundSql provide(String name) {
                    return new BoundSql("INSERT INTO users (name) VALUES (?)", List.of(BoundParameter.of(String.class, name)));
                }
            }
            @Mapper interface GeneratedKeyProviderMapper {
                @GeneratedKey("id")
                @UseSqlProvider(value = InsertProvider.class, statementType = ExecutionPlan.StatementType.INSERT)
                Long insert(String name);
            }
            """);

        assertFailure(batch, "generated keys are not supported for batch methods");
        assertFailure(dynamic, "generated keys require static SQL");
        assertFailure(provider, "generated keys are not supported with SQL providers");
    }

    private void assertFailure(CompilationResult result, String message) {
        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(message), result::diagnosticsText);
    }

    private CompilationResult compile(String typeName, String source) throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve(typeName + "-sources");
        Path classesDirectory = temporaryDirectory.resolve(typeName + "-classes");
        Path generatedDirectory = temporaryDirectory.resolve(typeName + "-generated");
        Path sourceFile = sourceDirectory.resolve(
            "org/liteorm/test/generatedkeyfixture/" + typeName + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classesDirectory);
        Files.createDirectories(generatedDirectory);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean succeeded;
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
            diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(
                MapperCompilationTestSupport.compilationUnits(List.of(sourceFile)));
            List<String> options = List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path"),
                "-d", classesDirectory.toString(),
                "-s", generatedDirectory.toString()
            );
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, units);
            task.setProcessors(List.of(new LiteOrmProcessor()));
            succeeded = task.call();
        }
        return new CompilationResult(succeeded, generatedDirectory, diagnostics.getDiagnostics());
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
