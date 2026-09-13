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
    void overloadedMapperSqlMethodsFailWithBothResolvedSignatures() throws Exception {
        assertUnsupportedMethod(
            "OverloadedMapper",
            """
                @Select("SELECT 1")
                int find(Long id);

                @Select("SELECT 1")
                int find(String name);
                """,
            "find",
            "overloaded Mapper SQL methods are not supported: "
                + "find(java.lang.Long), find(java.lang.String)"
        );
    }

    @Test
    void xmlMethodOverloadIsRejectedBeforeMalformedXmlIsParsed() throws Exception {
        assertUnsupportedMethod(
            "OverloadedMalformedXmlMapper",
            """
                ValueRow findValue(Long id);

                ValueRow findValue(String name);
                """,
            "findValue",
            "overloaded Mapper SQL methods are not supported: "
                + "findValue(java.lang.Long), findValue(java.lang.String)"
        );
    }

    @Test
    void inheritedSqlMethodOverloadIsRejected() throws Exception {
        assertUnsupportedSource(
            "InheritedOverloadMapper",
            """
                package org.liteorm.test.diagnostics;

                import org.liteorm.annotation.Mapper;
                import org.liteorm.annotation.Select;

                interface ParentOverloadMapper {
                    @Select("SELECT 1")
                    int find(Long id);
                }

                @Mapper
                public interface InheritedOverloadMapper extends ParentOverloadMapper {
                    @Select("SELECT 1")
                    int find(String name);
                }
                """,
            "find",
            "overloaded Mapper SQL methods are not supported: "
                + "find(java.lang.Long), find(java.lang.String)"
        );
    }

    @Test
    void providerSqlMethodOverloadIsRejected() throws Exception {
        assertUnsupportedSource(
            "ProviderOverloadMapper",
            """
                package org.liteorm.test.diagnostics;

                import java.util.List;
                import org.liteorm.annotation.Mapper;
                import org.liteorm.annotation.UseSqlProvider;
                import org.liteorm.api.BoundSql;
                import org.liteorm.api.ExecutionPlan;
                import org.liteorm.api.SqlProvider;

                final class LongProvider implements SqlProvider<Long> {
                    public LongProvider() {
                    }

                    public BoundSql provide(Long value) {
                        return new BoundSql("SELECT 1", List.of());
                    }
                }

                final class StringProvider implements SqlProvider<String> {
                    public StringProvider() {
                    }

                    public BoundSql provide(String value) {
                        return new BoundSql("SELECT 1", List.of());
                    }
                }

                @Mapper
                public interface ProviderOverloadMapper {
                    @UseSqlProvider(value = LongProvider.class,
                        statementType = ExecutionPlan.StatementType.SELECT)
                    int find(Long id);

                    @UseSqlProvider(value = StringProvider.class,
                        statementType = ExecutionPlan.StatementType.SELECT)
                    int find(String name);
                }
                """,
            "find",
            "overloaded Mapper SQL methods are not supported: "
                + "find(java.lang.Long), find(java.lang.String)"
        );
    }

    @Test
    void sameSignatureOverrideDefaultHelperAndObjectMethodsDoNotTriggerOverload() throws Exception {
        assertSupportedSource(
            "ValidEffectiveMethodsMapper",
            """
                package org.liteorm.test.diagnostics;

                import org.liteorm.annotation.Mapper;
                import org.liteorm.annotation.Select;

                interface ParentEffectiveMethodsMapper {
                    @Select("SELECT 1")
                    int find(Long id);
                }

                @Mapper
                public interface ValidEffectiveMethodsMapper extends ParentEffectiveMethodsMapper {
                    @Override
                    @Select("SELECT 1")
                    int find(Long id);

                    default String find(String value) {
                        return value;
                    }

                    @Select("SELECT 1")
                    int equals(String value);
                }
                """
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
    void mapperXmlNamespaceMustMatchMapperType() throws Exception {
        assertUnsupportedXml(
            "NamespaceMismatchMapper",
            "ValueRow findValue();",
            """
                <mapper namespace="org.liteorm.test.diagnostics.OtherMapper">
                    <select id="findValue" resultType="java.lang.String">SELECT 'value'</select>
                </mapper>
                """,
            "XML namespace 'org.liteorm.test.diagnostics.OtherMapper' does not match Mapper "
                + "'org.liteorm.test.diagnostics.NamespaceMismatchMapper'"
        );
    }

    @Test
    void mapperXmlRejectsDuplicateStatementIdsAcrossStatementTypes() throws Exception {
        assertUnsupportedXml(
            "DuplicateStatementIdMapper",
            "ValueRow findValue();",
            """
                <mapper namespace="org.liteorm.test.diagnostics.DuplicateStatementIdMapper">
                    <select id="findValue" resultType="java.lang.String">SELECT 'value'</select>
                    <update id="findValue">UPDATE values_table SET value = 'value'</update>
                </mapper>
                """,
            "duplicate XML statement id 'findValue'"
        );
    }

    @Test
    void mapperXmlRejectsBlankStatementIds() throws Exception {
        assertUnsupportedXml(
            "BlankStatementIdMapper",
            "ValueRow findValue();",
            """
                <mapper namespace="org.liteorm.test.diagnostics.BlankStatementIdMapper">
                    <select id="" resultType="java.lang.String">SELECT 'blank'</select>
                    <select id="findValue" resultType="java.lang.String">SELECT 'value'</select>
                </mapper>
                """,
            "XML <select> requires non-blank attribute 'id'"
        );
    }

    @Test
    void mapperXmlRejectsBlankSqlFragmentIds() throws Exception {
        assertUnsupportedXml(
            "BlankSqlFragmentMapper",
            "ValueRow findValue();",
            """
                <mapper namespace="org.liteorm.test.diagnostics.BlankSqlFragmentMapper">
                    <sql id="">SELECT 'blank'</sql>
                    <select id="findValue" resultType="java.lang.String">SELECT 'value'</select>
                </mapper>
                """,
            "<sql> requires non-blank attribute 'id'"
        );
    }

    @Test
    void mapperXmlRejectsDuplicateSqlFragmentIds() throws Exception {
        assertUnsupportedXml(
            "DuplicateSqlFragmentMapper",
            "ValueRow findValue();",
            """
                <mapper namespace="org.liteorm.test.diagnostics.DuplicateSqlFragmentMapper">
                    <sql id="selectedValue">SELECT 'first'</sql>
                    <sql id="selectedValue">SELECT 'second'</sql>
                    <select id="findValue" resultType="java.lang.String">
                        <include refid="selectedValue"/>
                    </select>
                </mapper>
                """,
            "duplicate XML <sql> id 'selectedValue'"
        );
    }

    @Test
    void mapperXmlRejectsUnsupportedTopLevelElements() throws Exception {
        assertUnsupportedXml(
            "UnsupportedTopLevelMapper",
            "ValueRow findValue();",
            """
                <mapper namespace="org.liteorm.test.diagnostics.UnsupportedTopLevelMapper">
                    <cache/>
                    <select id="findValue" resultType="java.lang.String">SELECT 'value'</select>
                </mapper>
                """,
            "Unsupported XML top-level tag <cache>"
        );
    }

    @Test
    void mapperXmlRejectsMissingIncludeReferencesEvenWhenFragmentIsUnused() throws Exception {
        assertUnsupportedXml(
            "UnusedMissingIncludeMapper",
            "ValueRow findValue();",
            """
                <mapper namespace="org.liteorm.test.diagnostics.UnusedMissingIncludeMapper">
                    <sql id="selectedValue">
                        SELECT <include refid="missingFragment"/>
                    </sql>
                    <select id="findValue" resultType="java.lang.String">SELECT 'value'</select>
                </mapper>
                """,
            "Unknown XML <include> refid 'missingFragment'"
        );
    }

    @Test
    void mapperXmlRejectsMissingResultMapReferencesEvenWhenStatementIsUnused() throws Exception {
        assertUnsupportedXml(
            "UnusedMissingResultMapMapper",
            "ValueRow findValue();",
            """
                <mapper namespace="org.liteorm.test.diagnostics.UnusedMissingResultMapMapper">
                    <select id="findValue" resultType="java.lang.String">SELECT 'value'</select>
                    <select id="otherValue" resultMap="missingResult">SELECT 'other'</select>
                </mapper>
                """,
            "Unknown XML resultMap 'missingResult'"
        );
    }

    @Test
    void malformedMapperXmlFailsWithMapperMethodAndResourcePath() throws Exception {
        assertUnsupportedMethod(
            "MalformedXmlMapper",
            "ValueRow findValue();",
            "findValue",
            "failed to parse XML resource /org/liteorm/test/diagnostics/MalformedXmlMapper.xml"
        );
    }

    @Test
    void mapperXmlDoctypeIsRejectedWithResourcePath() throws Exception {
        assertUnsupportedMethod(
            "DoctypeXmlMapper",
            "ValueRow findValue();",
            "findValue",
            "DOCTYPE is not allowed in XML resource "
                + "/org/liteorm/test/diagnostics/DoctypeXmlMapper.xml"
        );
    }

    @Test
    void mapperXmlExternalParameterEntityIsRejectedBeforeNetworkAccess() throws Exception {
        assertUnsupportedMethod(
            "ParameterEntityXmlMapper",
            "ValueRow findValue();",
            "findValue",
            "DOCTYPE is not allowed in XML resource "
                + "/org/liteorm/test/diagnostics/ParameterEntityXmlMapper.xml"
        );
    }

    @Test
    void mapperXmlExternalDtdIsRejectedBeforeNetworkAccess() throws Exception {
        assertUnsupportedMethod(
            "ExternalDtdXmlMapper",
            "ValueRow findValue();",
            "findValue",
            "DOCTYPE is not allowed in XML resource "
                + "/org/liteorm/test/diagnostics/ExternalDtdXmlMapper.xml"
        );
    }

    @Test
    void mapperXmlXIncludeIsRejectedWithResourcePath() throws Exception {
        assertUnsupportedMethod(
            "XIncludeXmlMapper",
            "ValueRow findValue();",
            "findValue",
            "XInclude is not allowed in XML resource "
                + "/org/liteorm/test/diagnostics/XIncludeXmlMapper.xml"
        );
    }

    @Test
    void mapperXmlExternalSchemaIsRejectedWithResourcePath() throws Exception {
        assertUnsupportedMethod(
            "ExternalSchemaXmlMapper",
            "ValueRow findValue();",
            "findValue",
            "external schema declarations are not allowed in XML resource "
                + "/org/liteorm/test/diagnostics/ExternalSchemaXmlMapper.xml"
        );
    }

    @Test
    void malformedAnnotationScriptFailsInsteadOfFallingBackToTextSql() throws Exception {
        assertUnsupportedMethod(
            "MalformedAnnotationScriptMapper",
            """
                @Select({
                    "<script>",
                    "SELECT 1",
                    "<if test=\\\"id != null\\\">WHERE 1 = #{id}",
                    "</script>"
                })
                int find(Long id);
                """,
            "find",
            "failed to parse annotation SQL script"
        );
    }

    @Test
    void annotationScriptDoctypeIsRejected() throws Exception {
        assertUnsupportedMethod(
            "DoctypeAnnotationScriptMapper",
            """
                @Select({
                    "<script>",
                    "<!DOCTYPE root>",
                    "<if test=\\\"id != null\\\">SELECT #{id}</if>",
                    "</script>"
                })
                int find(Long id);
                """,
            "find",
            "DOCTYPE is not allowed in annotation SQL script"
        );
    }

    @Test
    void unsupportedAnnotationScriptTagFailsInsteadOfBecomingTextSql() throws Exception {
        assertUnsupportedMethod(
            "UnsupportedAnnotationTagMapper",
            """
                @Select({
                    "<script>",
                    "SELECT 1",
                    "<unsupported>WHERE 1 = 1</unsupported>",
                    "</script>"
                })
                int find();
                """,
            "find",
            "Unsupported annotation SQL tag <unsupported>"
        );
    }

    @Test
    void unsupportedXmlTagFailsWithMapperMethodAndTagName() throws Exception {
        assertUnsupportedMethod(
            "UnsupportedTagMapper",
            "ValueRow findValue();",
            "findValue",
            "XML resource /org/liteorm/test/diagnostics/UnsupportedTagMapper.xml "
                + "[namespace=org.liteorm.test.diagnostics.UnsupportedTagMapper, "
                + "statementId=findValue]: Unsupported XML tag <unsupported>"
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
            "Unsupported XML <association> in resultMap 'valueResult'"
        );
    }

    @Test
    void failedMapperDoesNotPreventIndependentMapperGeneration() throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path classesDirectory = temporaryDirectory.resolve("classes");
        Path generatedDirectory = temporaryDirectory.resolve("generated");
        Path invalidSource = sourceDirectory.resolve(
            "org/liteorm/test/diagnostics/AInvalidMapper.java");
        Path validSource = sourceDirectory.resolve(
            "org/liteorm/test/diagnostics/ZIndependentMapper.java");
        Files.createDirectories(invalidSource.getParent());
        Files.createDirectories(classesDirectory);
        Files.createDirectories(generatedDirectory);
        Files.writeString(invalidSource, """
            package org.liteorm.test.diagnostics;

            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Select;

            @Mapper
            public interface AInvalidMapper {
                @Select("SELECT 1")
                int find(Long id);

                @Select("SELECT 1")
                int find(String name);
            }
            """, StandardCharsets.UTF_8);
        Files.writeString(validSource, """
            package org.liteorm.test.diagnostics;

            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Select;

            @Mapper
            public interface ZIndependentMapper {
                @Select("SELECT 1")
                int count();
            }
            """, StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean compilationSucceeded = compile(
            List.of(invalidSource, validSource), classesDirectory, generatedDirectory, diagnostics);

        assertFalse(compilationSucceeded, () -> diagnostics.getDiagnostics().toString());
        assertTrue(diagnostics.getDiagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR
                && diagnostic.getMessage(null).contains("AInvalidMapper#find")));
        assertTrue(Files.exists(generatedDirectory.resolve(
            "org/liteorm/test/diagnostics/ZIndependentMapperImpl.java")));
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

    private void assertUnsupportedXml(
            String mapperName,
            String methodSource,
            String xml,
            String expectedMessage) throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("xml-" + mapperName + "/sources");
        Path resourceDirectory = temporaryDirectory.resolve("xml-" + mapperName + "/resources");
        Path classesDirectory = temporaryDirectory.resolve("xml-" + mapperName + "/classes");
        Path generatedDirectory = temporaryDirectory.resolve("xml-" + mapperName + "/generated");
        Path mapperSource = sourceDirectory.resolve("org/liteorm/test/diagnostics/" + mapperName + ".java");
        Path mapperXml = resourceDirectory.resolve("org/liteorm/test/diagnostics/" + mapperName + ".xml");

        Files.createDirectories(mapperSource.getParent());
        Files.createDirectories(mapperXml.getParent());
        Files.createDirectories(classesDirectory);
        Files.createDirectories(generatedDirectory);
        Files.writeString(mapperSource, """
            package org.liteorm.test.diagnostics;

            import org.liteorm.annotation.Mapper;

            record ValueRow(String value) {
            }

            @Mapper
            public interface %s {
            %s
            }
            """.formatted(mapperName, methodSource.indent(4)), StandardCharsets.UTF_8);
        Files.writeString(mapperXml, xml, StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean compilationSucceeded = compile(
            mapperSource, resourceDirectory, classesDirectory, generatedDirectory, diagnostics);

        assertFalse(compilationSucceeded, () -> diagnostics.getDiagnostics().toString());
        assertTrue(diagnostics.getDiagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR
                && diagnostic.getMessage(null).contains(mapperName + "#findValue")
                && diagnostic.getMessage(null).contains(expectedMessage)),
            () -> diagnostics.getDiagnostics().toString());
    }

    private void assertUnsupportedSource(
            String mapperName,
            String source,
            String methodName,
            String expectedMessage) throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path classesDirectory = temporaryDirectory.resolve("classes");
        Path generatedDirectory = temporaryDirectory.resolve("generated");
        Path mapperSource = sourceDirectory.resolve(
            "org/liteorm/test/diagnostics/" + mapperName + ".java");
        Files.createDirectories(mapperSource.getParent());
        Files.createDirectories(classesDirectory);
        Files.createDirectories(generatedDirectory);
        Files.writeString(mapperSource, source, StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean compilationSucceeded = compile(
            mapperSource, classesDirectory, generatedDirectory, diagnostics);

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

    private void assertSupportedSource(String mapperName, String source) throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path classesDirectory = temporaryDirectory.resolve("classes");
        Path generatedDirectory = temporaryDirectory.resolve("generated");
        Path mapperSource = sourceDirectory.resolve(
            "org/liteorm/test/diagnostics/" + mapperName + ".java");
        Files.createDirectories(mapperSource.getParent());
        Files.createDirectories(classesDirectory);
        Files.createDirectories(generatedDirectory);
        Files.writeString(mapperSource, source, StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean compilationSucceeded = compile(
            mapperSource, classesDirectory, generatedDirectory, diagnostics);

        assertTrue(compilationSucceeded, () -> diagnostics.getDiagnostics().toString());
    }

    private boolean compile(
            Path mapperSource,
            Path classesDirectory,
            Path generatedDirectory,
            DiagnosticCollector<JavaFileObject> diagnostics) throws Exception {
        return compile(mapperSource, null, classesDirectory, generatedDirectory, diagnostics);
    }

    private boolean compile(
            List<Path> mapperSources,
            Path classesDirectory,
            Path generatedDirectory,
            DiagnosticCollector<JavaFileObject> diagnostics) throws Exception {
        return compile(mapperSources, null, classesDirectory, generatedDirectory, diagnostics);
    }

    private boolean compile(
            Path mapperSource,
            Path resourceDirectory,
            Path classesDirectory,
            Path generatedDirectory,
            DiagnosticCollector<JavaFileObject> diagnostics) throws Exception {
        return compile(List.of(mapperSource), resourceDirectory, classesDirectory, generatedDirectory, diagnostics);
    }

    private boolean compile(
            List<Path> mapperSources,
            Path resourceDirectory,
            Path classesDirectory,
            Path generatedDirectory,
            DiagnosticCollector<JavaFileObject> diagnostics) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
            diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> compilationUnits =
                fileManager.getJavaFileObjectsFromPaths(
                    MapperCompilationTestSupport.compilationUnits(mapperSources));
            List<String> options = List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path")
                    + (resourceDirectory == null ? "" : java.io.File.pathSeparator + resourceDirectory),
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
