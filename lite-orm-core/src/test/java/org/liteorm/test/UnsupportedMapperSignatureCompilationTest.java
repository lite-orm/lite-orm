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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnsupportedMapperSignatureCompilationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultMapperMethodFailsWithMapperAndMethodName() throws Exception {
        assertUnsupportedMethod(
            "DefaultMethodMapper",
            """
                @Select("SELECT 1")
                default int unsupportedDefault() {
                    return 1;
                }
                """,
            "unsupportedDefault",
            "default mapper methods are not supported"
        );
    }

    @Test
    void staticMapperMethodFailsWithMapperAndMethodName() throws Exception {
        assertUnsupportedMethod(
            "StaticMethodMapper",
            """
                @Select("SELECT 1")
                static int unsupportedStatic() {
                    return 1;
                }
                """,
            "unsupportedStatic",
            "static mapper methods are not supported"
        );
    }

    @Test
    void varargsMapperMethodFailsWithMapperAndMethodName() throws Exception {
        assertUnsupportedMethod(
            "VarargsMethodMapper",
            """
                @Select("SELECT 1")
                int unsupportedVarargs(String... values);
                """,
            "unsupportedVarargs",
            "varargs mapper methods are not supported"
        );
    }

    @Test
    void unknownSqlParameterRootFailsWithMapperMethodAndReference() throws Exception {
        assertUnsupportedMethod(
            "UnknownParameterMapper",
            """
                @Select("SELECT 1 WHERE 1 = #{missing}")
                int findById(Long id);
                """,
            "findById",
            "Unknown SQL parameter root: missing"
        );
    }

    @Test
    void duplicateParamAliasFailsWithMapperMethodAndAlias() throws Exception {
        assertUnsupportedMethod(
            "AmbiguousParameterMapper",
            """
                @Select("SELECT 1 WHERE 1 = #{value}")
                int find(@org.liteorm.annotation.Param("value") Long first,
                         @org.liteorm.annotation.Param("value") Long second);
                """,
            "find",
            "Ambiguous SQL parameter alias: value"
        );
    }

    @Test
    void missingXmlStatementFailsWithMapperAndMethodName() throws Exception {
        assertUnsupportedMethod(
            "MissingStatementMapper",
            "ValueRow findValue();",
            "findValue",
            "XML mapper exists but statement 'findValue' was not found"
        );
    }

    @Test
    void unsupportedXmlTagFailsWithMapperMethodAndTagName() throws Exception {
        assertUnsupportedMethod(
            "UnsupportedTagMapper",
            "ValueRow findValue();",
            "findValue",
            "Unsupported XML tag <unsupported>"
        );
    }

    @Test
    void missingRequiredXmlAttributeFailsAtCompileTime() throws Exception {
        assertUnsupportedMethod(
            "MissingIfTestMapper",
            "ValueRow findValue(String value);",
            "findValue",
            "XML <if> requires non-blank attribute 'test'"
        );
    }

    @Test
    void unknownXmlAttributeFailsAtCompileTime() throws Exception {
        assertUnsupportedMethod(
            "UnknownForeachAttributeMapper",
            "ValueRow findValue(java.util.List<String> values);",
            "findValue",
            "Unsupported XML attribute 'seperator' on <foreach>"
        );
    }

    @Test
    void chooseRejectsUnsupportedChildElements() throws Exception {
        assertUnsupportedMethod(
            "InvalidChooseChildMapper",
            "ValueRow findValue(String value);",
            "findValue",
            "XML <choose> only supports <when> and <otherwise> children"
        );
    }

    @Test
    void chooseRejectsMultipleOtherwiseBranches() throws Exception {
        assertUnsupportedMethod(
            "DuplicateOtherwiseMapper",
            "ValueRow findValue(String value);",
            "findValue",
            "XML <choose> supports at most one <otherwise>"
        );
    }

    @Test
    void chooseRejectsWhenAfterOtherwise() throws Exception {
        assertUnsupportedMethod(
            "WhenAfterOtherwiseMapper",
            "ValueRow findValue(String value);",
            "findValue",
            "XML <otherwise> must be the last child of <choose>"
        );
    }

    @Test
    void missingIncludeReferenceFailsDuringXmlParsing() throws Exception {
        assertUnsupportedMethod(
            "MissingIncludeMapper",
            "ValueRow findValue();",
            "findValue",
            "Unknown XML <include> refid 'missingFragment'"
        );
    }

    @Test
    void cyclicIncludeReferenceReportsDeterministicPath() throws Exception {
        assertUnsupportedMethod(
            "CyclicIncludeMapper",
            "ValueRow findValue();",
            "findValue",
            "Cyclic XML <include> reference: first -> second -> first"
        );
    }

    @Test
    void dollarSubstitutionIsRejectedWithMapperMethodAndExpression() throws Exception {
        assertUnsupportedMethod(
            "UnsafeDollarMapper",
            """
                @Select("SELECT value FROM users ORDER BY ${column}")
                ValueRow find(String column);
                """,
            "find",
            "Unsafe SQL substitution ${column} is not supported"
        );
    }

    @Test
    void staticOgnlMethodCallIsRejectedWithMapperMethodAndExpression() throws Exception {
        assertUnsupportedMethod(
            "UnsupportedExpressionMapper",
            "ValueRow findValue();",
            "findValue",
            "Unsupported dynamic SQL expression: @java.lang.System@currentTimeMillis() > 0"
        );
    }

    @Test
    void nestedJavaBeanPropertyMappingFailsAtCompileTime() throws Exception {
        assertUnsupportedMethod(
            "NestedResultMapper",
            """
                @Select("SELECT NULL")
                NestedResult findValue();
                """,
            "findValue",
            "Unsupported result mapping: nested object property org.liteorm.test.diagnostics.NestedResult.detail"
        );
    }

    @Test
    void nestedRecordComponentMappingFailsAtCompileTime() throws Exception {
        assertUnsupportedMethod(
            "NestedRecordMapper",
            """
                @Select("SELECT NULL")
                NestedRecord findValue();
                """,
            "findValue",
            "Unsupported result mapping: nested record component org.liteorm.test.diagnostics.NestedRecord.detail"
        );
    }

    @Test
    void primitiveSingleResultFailsBecauseZeroRowsCannotBeRepresented() throws Exception {
        assertUnsupportedMethod(
            "PrimitiveSingleResultMapper",
            "@Select(\"SELECT COUNT(*) FROM users\") long count();",
            "count",
            "primitive SELECT return types cannot represent zero rows"
        );
    }

    @Test
    void boxedUpdateReturnTypeFailsAtCompileTime() throws Exception {
        assertUnsupportedMethod(
            "BoxedUpdateResultMapper",
            "@org.liteorm.annotation.Update(\"UPDATE users SET name = #{name}\") Integer update(String name);",
            "update",
            "write methods must return void, int, or long"
        );
    }

    @Test
    void complexXmlResultMapFailsWithMigrationGuidance() throws Exception {
        assertUnsupportedMethod(
            "ComplexResultMapMapper",
            "ValueRow findValue();",
            "findValue",
            "Unsupported XML resultMap 'valueResult'; use resultType, @UseRowMapper, or raw JDBC"
        );
    }

    private void assertUnsupportedMethod(
            String mapperName,
            String methodSource,
            String methodName,
            String expectedMessage) throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path classesDirectory = temporaryDirectory.resolve("classes");
        Path generatedDirectory = temporaryDirectory.resolve("generated");
        Path mapperSource = sourceDirectory.resolve("org/liteorm/test/diagnostics/" + mapperName + ".java");

        Files.createDirectories(mapperSource.getParent());
        Files.createDirectories(classesDirectory);
        Files.createDirectories(generatedDirectory);
        String supportTypes = switch (mapperName) {
            case "NestedResultMapper" -> """
                class NestedDetail {
                }

                class NestedResult {
                    private NestedDetail detail;

                    public NestedResult() {
                    }

                    public void setDetail(NestedDetail detail) {
                        this.detail = detail;
                    }
                }

                """;
            case "NestedRecordMapper" -> """
                class NestedDetail {
                }

                record NestedRecord(NestedDetail detail) {
                }

                """;
            default -> "";
        };
        Files.writeString(mapperSource, """
            package org.liteorm.test.diagnostics;

            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Select;

            record ValueRow(String value) {
            }

            %s

            @Mapper
            public interface %s {
            %s
            }
            """.formatted(supportTypes, mapperName, methodSource.indent(4)), StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean compilationSucceeded = compile(mapperSource, classesDirectory, generatedDirectory, diagnostics);

        assertFalse(compilationSucceeded, () -> diagnostics.getDiagnostics().toString());
        Diagnostic<? extends JavaFileObject> diagnostic = diagnostics.getDiagnostics().stream()
            .filter(candidate -> candidate.getKind() == Diagnostic.Kind.ERROR)
            .filter(candidate -> candidate.getMessage(null).contains(mapperName + "#" + methodName))
            .filter(candidate -> candidate.getMessage(null).contains(expectedMessage))
            .findFirst()
            .orElseThrow(() -> new AssertionError(diagnostics.getDiagnostics().toString()));
        long methodLine = Files.readAllLines(mapperSource).stream()
            .takeWhile(line -> !line.contains(methodName + "("))
            .count() + 1;
        assertEquals(methodLine, diagnostic.getLineNumber(), diagnostics.getDiagnostics().toString());
    }

    private boolean compile(
            Path mapperSource,
            Path classesDirectory,
            Path generatedDirectory,
            DiagnosticCollector<JavaFileObject> diagnostics) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
            diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> compilationUnits =
                fileManager.getJavaFileObjectsFromPaths(List.of(mapperSource));
            List<String> options = List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path"),
                "-d", classesDirectory.toString(),
                "-s", generatedDirectory.toString()
            );

            JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, options, null, compilationUnits);
            task.setProcessors(List.of(new LiteOrmProcessor()));
            return task.call();
        }
    }
}
