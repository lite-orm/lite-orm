package org.liteorm.compile;

import java.util.StringJoiner;

/**
 * Renders a validated Mapper source model using deterministic JDK-only logic.
 *
 * <p>This renderer owns source layout. Compiler code owns the semantic content
 * of fields and members before they reach this interface.</p>
 */
final class JavaSourceRenderer {

    String render(GeneratedMapperSourceModel mapper) {
        StringBuilder source = new StringBuilder();
        source.append("package ").append(mapper.packageName()).append(";\n\n");
        for (String importName : mapper.imports()) {
            source.append("import ").append(importName).append(";\n");
        }
        source.append("\n");
        source.append("""
            /**
             * Generated Mapper implementation.
             * Contains compile-time SQL binding and result mapping without reflection.
             */
            """);
        source.append("public class ").append(mapper.implementationName())
            .append(" implements ").append(mapper.interfaceName()).append(" {\n\n");
        source.append("    private final SqlExecutor sqlExecutor;\n\n");
        source.append("    public ").append(mapper.implementationName())
            .append("(SqlExecutor sqlExecutor) {\n");
        source.append("        this.sqlExecutor = java.util.Objects.requireNonNull(sqlExecutor, \"sqlExecutor\");\n");
        source.append("    }\n");

        appendSection(source, mapper.fields());
        appendSection(source, mapper.members());
        source.append("\n}\n");
        return source.toString();
    }

    private void appendSection(StringBuilder source, Iterable<String> entries) {
        var joiner = new StringJoiner("\n\n");
        for (String entry : entries) {
            joiner.add(entry);
        }
        if (joiner.length() > 0) {
            source.append("\n\n").append(joiner).append("\n");
        }
    }
}
