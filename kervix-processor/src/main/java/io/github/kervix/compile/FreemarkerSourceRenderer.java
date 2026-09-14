package io.github.kervix.compile;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

/**
 * Renders the processor-owned Mapper source layout with FreeMarker.
 *
 * <p>Compiler code owns SQL semantics, type decisions, diagnostics, and the
 * contents of generated members. The template only lays out that structured
 * source model.</p>
 */
final class FreemarkerSourceRenderer {

    private final Configuration configuration;

    FreemarkerSourceRenderer() {
        configuration = new Configuration(Configuration.VERSION_2_3_32);
        configuration.setClassForTemplateLoading(getClass(), "/templates");
        configuration.setDefaultEncoding("UTF-8");
    }

    String render(GeneratedMapperSourceModel sourceModel) throws CodeGenerator.GenerationException {
        try {
            Template template = configuration.getTemplate("mapper-impl.ftl");
            Map<String, Object> dataModel = Map.of(
                "mapper", Map.of(
                    "packageName", sourceModel.packageName(),
                    "interfaceName", sourceModel.interfaceName(),
                    "implementationName", sourceModel.implementationName(),
                    "imports", sourceModel.imports(),
                    "fields", sourceFields(sourceModel.fields()),
                    "members", sourceMembers(sourceModel.members())
                )
            );

            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            return writer.toString();
        } catch (IOException | TemplateException exception) {
            throw new CodeGenerator.GenerationException("Source generation failed", exception);
        }
    }

    private List<Map<String, Object>> sourceFields(List<GeneratedSourceField> fields) {
        return fields.stream()
            .map(field -> Map.<String, Object>of("declaration", field.declaration()))
            .toList();
    }

    private List<Map<String, Object>> sourceMembers(List<GeneratedSourceMember> members) {
        return members.stream()
            .map(this::sourceMember)
            .toList();
    }

    private Map<String, Object> sourceMember(GeneratedSourceMember member) {
        if (member instanceof GeneratedMethodMember methodMember) {
            GeneratedMethodSource method = methodMember.method();
            return Map.of(
                "kind", member.kind().name(),
                "method", Map.<String, Object>of(
                    "documentation", method.documentation(),
                    "returnType", method.returnType(),
                    "methodName", method.methodName(),
                    "parameters", method.parameters(),
                    "body", method.body()
                )
            );
        }
        GeneratedTextMember textMember = (GeneratedTextMember) member;
        return Map.of(
            "kind", member.kind().name(),
            "source", textMember.source()
        );
    }
}
