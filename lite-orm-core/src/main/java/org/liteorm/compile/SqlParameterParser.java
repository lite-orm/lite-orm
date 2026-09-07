package org.liteorm.compile;

import org.liteorm.annotation.Param;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code #{param}} expressions and MyBatis-style aliases at compile time.
 *
 * @author lite-orm
 * @since 2024/09/29
 */
final class SqlParameterParser {

    private static final Pattern HASH_PARAM_PATTERN = Pattern.compile("#\\{([^}]+)\\}");

    /**
     * Builds normalized parameter metadata from a Mapper method.
     */
    public List<MethodParameter> describeMethodParameters(List<? extends VariableElement> methodParameters) {
        return describeMethodParameters(methodParameters, methodParameters.stream()
            .map(VariableElement::asType)
            .toList());
    }

    public List<MethodParameter> describeMethodParameters(
            List<? extends VariableElement> methodParameters,
            List<? extends TypeMirror> resolvedParameterTypes) {
        List<MethodParameter> parameters = new ArrayList<>(methodParameters.size());
        for (int i = 0; i < methodParameters.size(); i++) {
            VariableElement param = methodParameters.get(i);
            String declaredName = param.getSimpleName().toString();
            LinkedHashSet<String> aliases = new LinkedHashSet<>();

            Param paramAnnotation = param.getAnnotation(Param.class);
            if (paramAnnotation != null && !paramAnnotation.value().isBlank()) {
                aliases.add(paramAnnotation.value().trim());
            }

            aliases.add(declaredName);
            aliases.add("param" + (i + 1));
            aliases.add("arg" + i);

            String typeName = resolvedParameterTypes.get(i).toString();
            if (methodParameters.size() == 1) {
                if (typeName.endsWith("[]")) {
                    aliases.add("array");
                }
                if (typeName.startsWith("java.util.List") || typeName.startsWith("java.util.Collection")) {
                    aliases.add("list");
                    aliases.add("collection");
                }
            }

            parameters.add(new MethodParameter(declaredName, declaredName, typeName, List.copyOf(aliases)));
        }
        return parameters;
    }

    /**
     * Parses SQL parameter binding order.
     */
    public SqlParseResult parseSqlFromElements(String sql, List<? extends VariableElement> methodParameters) {
        return parseSql(sql, describeMethodParameters(methodParameters));
    }

    /**
     * Parses SQL binding order with normalized parameter metadata.
     */
    public SqlParseResult parseSql(String sql, List<MethodParameter> methodParameters) {
        Map<String, MethodParameter> aliasLookup = buildAliasLookup(methodParameters);
        List<ParameterBinding> bindings = new ArrayList<>();
        StringBuffer processedSql = new StringBuffer();

        Matcher matcher = HASH_PARAM_PATTERN.matcher(sql);
        int parameterIndex = 1;
        while (matcher.find()) {
            ParameterExpression parameterExpression = parseParameterExpression(matcher.group(1));
            bindings.add(resolveBinding(parameterExpression, aliasLookup, parameterIndex++));
            matcher.appendReplacement(processedSql, "?");
        }
        matcher.appendTail(processedSql);

        return new SqlParseResult(processedSql.toString(), bindings);
    }

    /**
     * Generates direct parameter access source.
     */
    public String generateParameterBindingCode(List<ParameterBinding> bindings) {
        if (bindings.isEmpty()) {
            return "        Object[] params = new Object[0];\n";
        }

        StringBuilder code = new StringBuilder();
        code.append("        Object[] params = new Object[").append(bindings.size()).append("];\n");
        for (ParameterBinding binding : bindings) {
            code.append("        params[").append(binding.index() - 1).append("] = ")
                .append(binding.accessCode()).append(";\n");
        }
        return code.toString();
    }

    /**
     * Converts a supported OGNL-style property path into a Java access expression.
     */
    public String toJavaAccess(String expression, List<MethodParameter> methodParameters) {
        Map<String, MethodParameter> aliasLookup = buildAliasLookup(methodParameters);
        return toJavaAccess(expression, methodParameters, aliasLookup, false, Set.of());
    }

    public String toJavaAccess(String expression, List<MethodParameter> methodParameters, boolean strictRootValidation) {
        Map<String, MethodParameter> aliasLookup = buildAliasLookup(methodParameters);
        return toJavaAccess(expression, methodParameters, aliasLookup, strictRootValidation, Set.of());
    }

    public String toJavaAccess(String expression, List<MethodParameter> methodParameters, boolean strictRootValidation,
                               Set<String> localRoots) {
        Map<String, MethodParameter> aliasLookup = buildAliasLookup(methodParameters);
        return toJavaAccess(expression, methodParameters, aliasLookup, strictRootValidation, localRoots);
    }

    private ParameterBinding resolveBinding(
            ParameterExpression parameterExpression,
            Map<String, MethodParameter> aliasLookup,
            int index) {
        String expression = parameterExpression.expression();
        MethodParameter rootParameter = aliasLookup.get(expression.split("\\.", 2)[0]);
        List<MethodParameter> uniqueParameters = new ArrayList<>(new LinkedHashSet<>(aliasLookup.values()));
        String accessCode = toJavaAccess(
            expression,
            rootParameter == null ? uniqueParameters : List.of(rootParameter),
            aliasLookup,
            true,
            Set.of()
        );
        String root = expression.split("\\.", 2)[0];
        rootParameter = aliasLookup.get(root);
        if (rootParameter == null) {
            throw new IllegalArgumentException("Unknown SQL parameter root: " + root);
        }
        return new ParameterBinding(
            index, expression, accessCode, rootParameter.typeName(), parameterExpression.jdbcType());
    }

    static ParameterExpression parseParameterExpression(String content) {
        String[] parts = content.split(",", -1);
        String expression = parts[0].trim();
        if (expression.isEmpty()) {
            throw new IllegalArgumentException("SQL parameter expression must not be blank");
        }
        String jdbcType = null;
        for (int index = 1; index < parts.length; index++) {
            String attribute = parts[index].trim();
            int equals = attribute.indexOf('=');
            if (equals < 1 || equals == attribute.length() - 1) {
                throw new IllegalArgumentException("Malformed SQL parameter attribute: " + attribute);
            }
            String name = attribute.substring(0, equals).trim();
            String value = attribute.substring(equals + 1).trim();
            if (!"jdbcType".equals(name)) {
                throw new IllegalArgumentException("Unsupported SQL parameter attribute: " + name);
            }
            if (jdbcType != null) {
                throw new IllegalArgumentException("Duplicate SQL parameter jdbcType attribute");
            }
            try {
                jdbcType = JDBCType.valueOf(value).name();
            } catch (IllegalArgumentException invalidJdbcType) {
                throw new IllegalArgumentException("Unknown JDBCType in SQL parameter: " + value);
            }
        }
        return new ParameterExpression(expression, jdbcType);
    }

    private String toJavaAccess(String expression, List<MethodParameter> methodParameters,
                                Map<String, MethodParameter> aliasLookup, boolean strictRootValidation,
                                Set<String> localRoots) {
        String[] parts = expression.split("\\.");
        String root = parts[0];
        MethodParameter rootParameter = aliasLookup.get(root);

        StringBuilder access = new StringBuilder();
        if (rootParameter != null) {
            access.append(rootParameter.runtimeName());
        } else if (localRoots.contains(root)) {
            access.append(root);
        } else if (methodParameters.size() == 1) {
            MethodParameter soleParameter = methodParameters.get(0);
            access.append(soleParameter.runtimeName()).append(".").append(root).append("()");
        } else if (strictRootValidation) {
            throw new IllegalArgumentException("Unknown SQL parameter root: " + root);
        } else {
            access.append(root);
        }

        for (int i = 1; i < parts.length; i++) {
            access.append(".").append(parts[i]);
            if (i == parts.length - 1 && "length".equals(parts[i])) {
                continue;
            }
            access.append("()");
        }

        return access.toString();
    }

    private Map<String, MethodParameter> buildAliasLookup(List<MethodParameter> methodParameters) {
        Map<String, MethodParameter> aliasLookup = new LinkedHashMap<>();
        for (MethodParameter parameter : methodParameters) {
            for (String alias : parameter.aliases()) {
                MethodParameter existing = aliasLookup.putIfAbsent(alias, parameter);
                if (existing != null && existing != parameter) {
                    throw new IllegalArgumentException("Ambiguous SQL parameter alias: " + alias);
                }
            }
        }
        return aliasLookup;
    }

    public record SqlParseResult(
        String processedSql,
        List<ParameterBinding> bindings
    ) {
        public boolean hasParameters() {
            return !bindings.isEmpty();
        }
    }

    public record MethodParameter(
        String declaredName,
        String runtimeName,
        String typeName,
        List<String> aliases
    ) {
    }

    public record ParameterBinding(
        int index,
        String expression,
        String accessCode,
        String typeName,
        String jdbcType
    ) {
        public ParameterBinding(int index, String expression, String accessCode, String typeName) {
            this(index, expression, accessCode, typeName, null);
        }
    }

    record ParameterExpression(String expression, String jdbcType) {
    }
}
