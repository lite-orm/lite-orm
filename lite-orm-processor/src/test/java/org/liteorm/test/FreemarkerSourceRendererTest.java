package org.liteorm.compile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreemarkerSourceRendererTest {

    @Test
    void rendersStructuredMapperMetadataAsSourceLayout() throws Exception {
        GeneratedMapperSourceModel sourceModel = new GeneratedMapperSourceModel(
            "org.liteorm.test",
            "UserMapper",
            "UserMapperImpl",
            List.of("org.liteorm.api.SqlExecutor"),
            List.of(new GeneratedSourceField("    private final String provider = \"provider\";")),
            List.of(new GeneratedMethodMember(new GeneratedMethodSource(
                "    /** Generated method. */",
                "String",
                "find",
                "",
                "        return provider;\n")))
        );

        String code = new FreemarkerSourceRenderer().render(sourceModel);

        assertTrue(code.contains("package org.liteorm.test;"));
        assertTrue(code.contains("import org.liteorm.api.SqlExecutor;"));
        assertTrue(code.contains("private final String provider = \"provider\";"));
        assertTrue(code.contains("public String find()"));
        assertTrue(code.contains("return provider;"));
        assertFalse(code.contains("generatedMethods"));
    }

    @Test
    void preservesMemberKindsAtTheModelBoundary() {
        GeneratedTextMember member = new GeneratedTextMember(
            GeneratedSourceMember.Kind.HELPER,
            "private static String helper() { return \"value\"; }");

        assertTrue(member.kind() == GeneratedSourceMember.Kind.HELPER);
        assertTrue(member.source().contains("helper"));
    }
}
