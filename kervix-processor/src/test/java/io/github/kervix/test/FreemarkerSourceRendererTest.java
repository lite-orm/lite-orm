package io.github.kervix.compile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreemarkerSourceRendererTest {

    @Test
    void rendersStructuredMapperMetadataAsSourceLayout() throws Exception {
        GeneratedMapperSourceModel sourceModel = new GeneratedMapperSourceModel(
            "io.github.kervix.test",
            "UserMapper",
            "UserMapperImpl",
            List.of("io.github.kervix.api.SqlExecutor"),
            List.of(new GeneratedSourceField("    private final String provider = \"provider\";")),
            List.of(
                new GeneratedTextMember(
                    GeneratedSourceMember.Kind.DEFINITION,
                    "    private static final String FIND_DEFINITION = \"definition\";"),
                new GeneratedMethodMember(new GeneratedMethodSource(
                    "    /** Generated method. */",
                    "String",
                    "find",
                    "",
                    "        return provider;\n")),
                new GeneratedTextMember(
                    GeneratedSourceMember.Kind.EXECUTION_FACTORY,
                    "    private String buildPlan() { return FIND_DEFINITION; }"),
                new GeneratedTextMember(
                    GeneratedSourceMember.Kind.HELPER,
                    "    private static String helper() { return \"value\"; }"))
        );

        String code = new FreemarkerSourceRenderer().render(sourceModel);

        assertTrue(code.contains("package io.github.kervix.test;"));
        assertTrue(code.contains("import io.github.kervix.api.SqlExecutor;"));
        assertTrue(code.contains("private final String provider = \"provider\";"));
        assertTrue(code.contains("public String find()"));
        assertTrue(code.contains("return provider;"));
        assertTrue(code.contains("FIND_DEFINITION"));
        assertTrue(code.contains("buildPlan"));
        assertTrue(code.contains("helper"));
        assertFalse(code.contains("generatedMethods"));
    }

    @Test
    void rendersEmptyFieldsAndMembersWithoutPlaceholderText() throws Exception {
        GeneratedMapperSourceModel sourceModel = new GeneratedMapperSourceModel(
            "io.github.kervix.empty",
            "EmptyMapper",
            "EmptyMapperImpl",
            List.of(),
            List.of(),
            List.of()
        );

        String code = new FreemarkerSourceRenderer().render(sourceModel);

        assertTrue(code.contains("package io.github.kervix.empty;"));
        assertTrue(code.contains("public class EmptyMapperImpl implements EmptyMapper"));
        assertTrue(code.contains("public EmptyMapperImpl(SqlExecutor sqlExecutor)"));
        assertFalse(code.contains("null"));
        assertFalse(code.contains("<#"));
        assertFalse(code.contains("${"));
    }

    @Test
    void preservesFieldAndMemberOrderWhenRenderingMixedMembers() throws Exception {
        GeneratedMapperSourceModel sourceModel = new GeneratedMapperSourceModel(
            "io.github.kervix.test",
            "OrderedMapper",
            "OrderedMapperImpl",
            List.of(),
            List.of(
                new GeneratedSourceField("    private final String first = \"first\";"),
                new GeneratedSourceField("    private final String second = \"second\";")
            ),
            List.of(
                new GeneratedTextMember(
                    GeneratedSourceMember.Kind.DEFINITION,
                    "    private static final String DEFINITION = \"definition\";"),
                new GeneratedMethodMember(new GeneratedMethodSource(
                    "    /** Ordered method. */",
                    "String",
                    "ordered",
                    "",
                    "        return DEFINITION;\n")),
                new GeneratedTextMember(
                    GeneratedSourceMember.Kind.EXECUTION_FACTORY,
                    "    private String factory() { return DEFINITION; }"),
                new GeneratedTextMember(
                    GeneratedSourceMember.Kind.HELPER,
                    "    private static String helper() { return \"helper\"; }"))
        );

        String code = new FreemarkerSourceRenderer().render(sourceModel);

        assertBefore(code, "first", "second");
        assertBefore(code, "second", "DEFINITION =");
        assertBefore(code, "DEFINITION =", "public String ordered()");
        assertBefore(code, "public String ordered()", "private String factory()");
        assertBefore(code, "private String factory()", "private static String helper()");
        assertEquals(-1, code.indexOf("generatedMethods"));
        assertFalse(code.contains("<#"));
        assertFalse(code.contains("${"));
    }

    @Test
    void rendersTextMembersWithoutRequiringAMethodNode() throws Exception {
        GeneratedMapperSourceModel sourceModel = new GeneratedMapperSourceModel(
            "io.github.kervix.test",
            "TextMapper",
            "TextMapperImpl",
            List.of(),
            List.of(),
            List.of(
                new GeneratedTextMember(
                    GeneratedSourceMember.Kind.DEFINITION,
                    "    private static final String VALUE = \"value\";"),
                new GeneratedTextMember(
                    GeneratedSourceMember.Kind.HELPER,
                    "    private static String helper() { return VALUE; }"))
        );

        String code = new FreemarkerSourceRenderer().render(sourceModel);

        assertTrue(code.contains("VALUE = \"value\";"));
        assertTrue(code.contains("private static String helper()"));
        assertFalse(code.contains("public null"));
        assertFalse(code.contains("generatedMethods"));
    }

    @Test
    void preservesTextMemberKindsAtTheModelBoundary() {
        GeneratedTextMember member = new GeneratedTextMember(
            GeneratedSourceMember.Kind.HELPER,
            "private static String helper() { return \"value\"; }");

        assertTrue(member.kind() == GeneratedSourceMember.Kind.HELPER);
        assertTrue(member.source().contains("helper"));
    }

    private static void assertBefore(String source, String first, String second) {
        assertTrue(
            source.indexOf(first) >= 0 && source.indexOf(second) >= 0
                && source.indexOf(first) < source.indexOf(second),
            () -> "Expected '" + first + "' before '" + second + "'\n" + source
        );
    }
}
