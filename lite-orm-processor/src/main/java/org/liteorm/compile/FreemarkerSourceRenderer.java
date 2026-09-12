package org.liteorm.compile;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
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
                    "fields", sourceModel.fields(),
                    "members", sourceModel.members()
                )
            );

            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            return writer.toString();
        } catch (IOException | TemplateException exception) {
            throw new CodeGenerator.GenerationException("Source generation failed", exception);
        }
    }
}
