package org.liteorm.compile;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.liteorm.api.ExecutionPlan;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FreeMarker-backed Java source generator.
 *
 * @author lite-orm
 * @since 2024/10/01
 */
final class FreemarkerCodeGenerator implements CodeGenerator {

    private static final Pattern HASH_PARAM_PATTERN = Pattern.compile("#\\{([^}]+)\\}");
    private static final Pattern DOLLAR_PARAM_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final Pattern EXPRESSION_TOKEN_PATTERN =
        Pattern.compile("\\b[a-zA-Z_][\\w]*(?:\\.[a-zA-Z_][\\w]*)*\\b");
    private static final Pattern EMPTY_STRING_NOT_EQUALS_PATTERN =
        Pattern.compile("([a-zA-Z_][\\w().]*)\\s*!=\\s*''");
    private static final Pattern EMPTY_STRING_EQUALS_PATTERN =
        Pattern.compile("([a-zA-Z_][\\w().]*)\\s*==\\s*''");
    private static final Pattern STRING_EQUALS_PATTERN =
        Pattern.compile("([a-zA-Z_][\\w().]*)\\s*==\\s*'([^']*)'");
    private static final Pattern STRING_NOT_EQUALS_PATTERN =
        Pattern.compile("([a-zA-Z_][\\w().]*)\\s*!=\\s*'([^']*)'");

    private final Configuration freemarkerConfig;
    private final SqlParameterParser parameterParser;

    public FreemarkerCodeGenerator() {
        this.freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);
        this.freemarkerConfig.setClassForTemplateLoading(this.getClass(), "/templates");
        this.freemarkerConfig.setDefaultEncoding("UTF-8");
        this.parameterParser = new SqlParameterParser();
    }

    @Override
    public String generateMapperImpl(TypeElement mapperInterface, MapperCompilationModel compilationModel,
                                     Elements elementUtils, Types typeUtils) throws GenerationException {
        try {
            Template template = freemarkerConfig.getTemplate("mapper-impl.ftl");

            Map<String, Object> dataModel = new HashMap<>();
            dataModel.put("packageName", compilationModel.packageName());
            dataModel.put("interfaceName", compilationModel.interfaceName());
            dataModel.put("implClassName", compilationModel.implementationName());
            dataModel.put("generatedMethods", generateMethods(
                compilationModel.methods(), compilationModel.packageName()));

            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            return writer.toString();
        } catch (IOException | TemplateException e) {
            throw new GenerationException("Source generation failed", e);
        }
    }

    private String classReference(String packageName, String qualifiedClassName) {
        String packagePrefix = packageName + ".";
        return qualifiedClassName.startsWith(packagePrefix)
            ? qualifiedClassName.substring(packagePrefix.length())
            : qualifiedClassName;
    }

    @Override
    public String generateMethodImpl(MapperCompilationModel.MethodModel methodModel) throws GenerationException {
        StringBuilder code = new StringBuilder();

        code.append("    /**\n");
        code.append("     * Mapper method: ").append(mapperMethodLocation(methodModel)).append("\n");
        code.append("     * SQL source: ").append(methodModel.sourceType().name()).append("\n");
        code.append("     */\n");
        code.append("    @Override\n");
        code.append("    public ").append(methodModel.returnType()).append(" ")
            .append(methodModel.methodName()).append("(").append(methodModel.parameterList()).append(") {\n");
        code.append("        ExecutionPlan executionPlan = ").append(methodModel.executionPlanFactoryName()).append("(")
            .append(callArguments(methodModel)).append(");\n");
        if (methodModel.cursorCallbackParameterName() != null) {
            code.append("        return sqlExecutor.queryCursor(executionPlan, ")
                .append(methodModel.cursorCallbackParameterName()).append(");\n");
            code.append("    }\n\n");
            code.append(generateExecutionPlanFactory(methodModel));
            return code.toString();
        }
        code.append("        SqlResult executionResult = sqlExecutor.execute(executionPlan);\n");
        code.append(generateReturnCode(methodModel));
        code.append("    }\n\n");
        code.append(generateExecutionPlanFactory(methodModel));
        return code.toString();
    }

    private String generateMethods(
            List<MapperCompilationModel.MethodModel> methods, String packageName) throws GenerationException {
        StringBuilder builder = new StringBuilder();
        builder.append("    private final TypeHandlerManager typeHandlerManager = new TypeHandlerManager();\n");
        for (MapperCompilationModel.MethodModel method : methods) {
            if (method.providerClassName() != null) {
                builder.append("    private final ").append(method.providerClassName()).append(" ")
                    .append(method.providerFieldName()).append(" = new ")
                    .append(method.providerClassName()).append("();\n");
            }
            for (MapperCompilationModel.ExtensionField extensionField : method.extensionFields()) {
                builder.append("    private final ").append(extensionField.typeName()).append(" ")
                    .append(extensionField.fieldName()).append(" = new ")
                    .append(extensionField.typeName()).append("();\n");
            }
        }
        if (methods.stream().anyMatch(method -> method.providerClassName() != null
                || !method.extensionFields().isEmpty())) {
            builder.append("\n");
        }
        for (MapperCompilationModel.MethodModel method : methods) {
            builder.append(generateMethodImpl(method)).append("\n");
            if (!method.resultMappingHelperCode().isBlank()) {
                builder.append(method.resultMappingHelperCode()).append("\n");
            }
        }
        return builder.toString();
    }

    private String callArguments(MapperCompilationModel.MethodModel methodModel) {
        List<String> names = new ArrayList<>(methodModel.methodParameters().size());
        for (SqlParameterParser.MethodParameter parameter : methodModel.methodParameters()) {
            names.add(parameter.runtimeName());
        }
        return String.join(", ", names);
    }

    private String generateReturnCode(MapperCompilationModel.MethodModel methodModel) {
        String returnType = methodModel.returnType();
        boolean isSelect = methodModel.statementType() == org.liteorm.api.ExecutionPlan.StatementType.SELECT;

        if (methodModel.statementType() == org.liteorm.api.ExecutionPlan.StatementType.BATCH) {
            return "        return executionResult.getBatchUpdateCounts();\n";
        }

        if (methodModel.generatedKey()) {
            return "        return " + generatedKeyExpression(methodModel) + ";\n";
        }

        if (!isSelect) {
            if ("void".equals(returnType)) {
                return "        return;\n";
            }
            if ("long".equals(returnType)) {
                return "        return (long) executionResult.getUpdateCount();\n";
            }
            return "        return executionResult.getUpdateCount();\n";
        }

        if (returnType.contains("List<")) {
            String elementType = returnType.substring(returnType.indexOf('<') + 1, returnType.lastIndexOf('>')).trim();
            return """
                List<Object[]> resultRows = executionResult.getQueryResults();
                if (resultRows == null || resultRows.isEmpty()) {
                    return new ArrayList<>();
                }
                %s
                List<%s> mappedResults = new ArrayList<>(resultRows.size());
                for (Object[] resultRow : resultRows) {
                    mappedResults.add(%s);
                }
                return mappedResults;
                """.formatted(resultColumnIndexes(methodModel), elementType,
                    resultRowExpression(methodModel.resultMappingCode())).indent(8);
        }

        if ("void".equals(returnType)) {
            return "        return;\n";
        }

        boolean optional = returnType.startsWith("java.util.Optional<");
        boolean primitive = isPrimitive(returnType);
        String noRows = optional
            ? "return java.util.Optional.empty();"
            : primitive
                ? "throw new MappingException(" + javaString(
                    "No row returned for " + methodModel.statementId() + " required primitive " + returnType)
                    + ", " + javaString(methodModel.statementId()) + ", " + returnType
                    + ".class, null, null, null);"
                : "return null;";
        String mappedValue = resultRowExpression(methodModel.resultMappingCode());
        String mappedReturn = optional
            ? "java.util.Optional.ofNullable(" + mappedValue + ")"
            : mappedValue;

        return """
            List<Object[]> resultRows = executionResult.getQueryResults();
            if (resultRows == null || resultRows.isEmpty()) {
                %s
            }
            if (resultRows.size() > 1) {
                throw new NonUniqueResultException(%s, resultRows.size());
            }
            %s
            Object[] resultRow = resultRows.get(0);
            return %s;
            """.formatted(noRows, javaString(methodModel.statementId()), resultColumnIndexes(methodModel),
                mappedReturn).indent(8);
    }

    private boolean isPrimitive(String typeName) {
        return switch (typeName) {
            case "boolean", "byte", "short", "int", "long", "char", "float", "double" -> true;
            default -> false;
        };
    }

    private String generatedKeyExpression(MapperCompilationModel.MethodModel methodModel) {
        String key = "executionResult.getGeneratedKey()";
        if (methodModel.rowMapperFieldName() != null) {
            return "(" + methodModel.returnType() + ") " + key;
        }
        return switch (methodModel.returnType()) {
            case "int", "java.lang.Integer" -> "ResultValueConverters.toInteger(" + key + ")";
            case "long", "java.lang.Long" -> "ResultValueConverters.toLong(" + key + ")";
            case "short", "java.lang.Short" -> "ResultValueConverters.toShort(" + key + ")";
            case "byte", "java.lang.Byte" -> "ResultValueConverters.toByte(" + key + ")";
            case "double", "java.lang.Double" -> "ResultValueConverters.toDouble(" + key + ")";
            case "float", "java.lang.Float" -> "ResultValueConverters.toFloat(" + key + ")";
            case "java.math.BigDecimal" -> "ResultValueConverters.toBigDecimal(" + key + ")";
            case "java.math.BigInteger" -> "ResultValueConverters.toBigInteger(" + key + ")";
            case "java.lang.String" -> "ResultValueConverters.toStringValue(" + key + ")";
            default -> throw new IllegalStateException(
                "Unsupported generated-key return type: " + methodModel.returnType());
        };
    }

    private String resultColumnIndexes(MapperCompilationModel.MethodModel methodModel) {
        if (methodModel.resultColumnLabels().isEmpty()) {
            return "";
        }
        return "int[] resultColumnIndexes = new int[]{" + methodModel.resultColumnLabels().stream()
            .map(label -> "executionResult.requireColumnIndex(" + javaString(label) + ")")
            .collect(java.util.stream.Collectors.joining(", ")) + "};";
    }

    private String mapperMethodLocation(MapperCompilationModel.MethodModel methodModel) {
        int methodSeparator = methodModel.statementId().lastIndexOf('.');
        if (methodSeparator < 0) {
            return methodModel.statementId();
        }
        return methodModel.statementId().substring(0, methodSeparator) + "#"
            + methodModel.statementId().substring(methodSeparator + 1);
    }

    private String resultRowExpression(String mappingCode) {
        return mappingCode.replace("row[", "resultRow[")
            .replace("(row,", "(resultRow,")
            .replace("(row)", "(resultRow)");
    }

    private String generateExecutionPlanFactory(MapperCompilationModel.MethodModel methodModel) throws GenerationException {
        StringBuilder code = new StringBuilder();
        code.append("    private ExecutionPlan ").append(methodModel.executionPlanFactoryName()).append("(")
            .append(methodModel.executionPlanParameterList()).append(") {\n");

        if (methodModel.statementType() == ExecutionPlan.StatementType.BATCH) {
            SqlParameterParser.MethodParameter collection = methodModel.methodParameters().get(0);
            String elementType = collection.typeName().substring(
                collection.typeName().indexOf('<') + 1, collection.typeName().lastIndexOf('>'));
            code.append("        List<Object[]> batchParameters = new ArrayList<>(")
                .append(collection.runtimeName()).append(".size());\n");
            code.append("        for (").append(elementType).append(" item : ")
                .append(collection.runtimeName()).append(") {\n");
            code.append("            Object[] params = new Object[")
                .append(methodModel.parameterBindings().size()).append("];\n");
            for (SqlParameterParser.ParameterBinding binding : methodModel.parameterBindings()) {
                code.append("            params[").append(binding.index() - 1).append("] = ")
                    .append(binding.accessCode()).append(";\n");
            }
            code.append("            batchParameters.add(params);\n");
            code.append("        }\n");
            code.append("        return new BatchExecutionPlan(")
                .append(javaString(methodModel.statementId())).append(", ")
                .append(javaString(methodModel.sqlTemplate())).append(", batchParameters, ")
                .append("ExecutionPlan.SqlSource.").append(methodModel.sourceType().name()).append(", ")
                .append(parameterBinderArray(methodModel)).append(", StatementOptions.defaults(), ")
                .append(typeRoutingExpression(methodModel)).append(");\n");
        } else if (methodModel.providerClassName() != null) {
            code.append("        BoundSql boundSql = BoundSql.requireValid(")
                .append(methodModel.providerFieldName()).append(".provide(")
                .append(methodModel.providerArgumentExpression()).append("), ")
                .append(javaString(methodModel.statementId())).append(");\n");
            code.append("        return new ExecutionPlan(")
                .append(javaString(methodModel.statementId())).append(", boundSql.sql(), ")
                .append("boundSql.parameterValues(), ExecutionPlan.StatementType.")
                .append(methodModel.statementType().name()).append(", ExecutionPlan.SqlSource.GENERATED, null, ")
                .append("boundSql.parameterBinders()").append(", ")
                .append(rowMapperExpression(methodModel)).append(", StatementOptions.defaults(), ")
                .append(typeRoutingExpression(
                    methodModel, "boundSql.parameterTypes()", "boundSql.parameterJdbcTypes()"))
                .append(");\n");
        } else if (methodModel.dynamic()) {
            code.append("        StringBuilder sql = new StringBuilder();\n");
            code.append("        List<Object> parameters = new ArrayList<>();\n");
            code.append("        List<ParameterBinder<?>> binders = new ArrayList<>();\n");
            Iterator<String> parameterBinders = methodModel.parameterBinderFields().iterator();
            if (methodModel.astNode() != null) {
                code.append(generateAstLogic(
                    methodModel.astNode(),
                    methodModel,
                    "sql",
                    "parameters",
                    "binders",
                    "        ",
                    new LinkedHashSet<>(),
                    parameterBinders
                ));
            } else {
                appendTextNode(code, methodModel.sqlTemplate(), methodModel,
                    "sql", "parameters", "binders", "        ", Set.of(), parameterBinders);
            }
            if (parameterBinders.hasNext()) {
                throw new GenerationException(methodModel.statementId()
                    + ": generated JDBC binder plan contains unused entries");
            }
            code.append("        return new ExecutionPlan(")
                .append(javaString(methodModel.statementId())).append(", ")
                .append("sql.toString().trim(), parameters.toArray(new Object[0]), ")
                .append("ExecutionPlan.StatementType.").append(methodModel.statementType().name()).append(", ")
                .append("ExecutionPlan.SqlSource.").append(methodModel.sourceType().name()).append(", ")
                .append(javaString(methodModel.generatedKeyColumn())).append(", ")
                .append("binders.toArray(new ParameterBinder<?>[0])").append(", ")
                .append(rowMapperExpression(methodModel)).append(", StatementOptions.defaults(), ")
                .append(typeRoutingExpression(methodModel)).append(");\n");
        } else {
            code.append("        String sql = ").append(javaString(methodModel.sqlTemplate())).append(";\n");
            code.append(parameterParser.generateParameterBindingCode(methodModel.parameterBindings()));
            code.append("        return new ExecutionPlan(")
                .append(javaString(methodModel.statementId())).append(", ")
                .append("sql, params, ExecutionPlan.StatementType.").append(methodModel.statementType().name()).append(", ")
                .append("ExecutionPlan.SqlSource.").append(methodModel.sourceType().name()).append(", ")
                .append(javaString(methodModel.generatedKeyColumn())).append(", ")
                .append(parameterBinderArray(methodModel)).append(", ")
                .append(rowMapperExpression(methodModel)).append(", StatementOptions.defaults(), ")
                .append(typeRoutingExpression(methodModel)).append(");\n");
        }

        code.append("    }\n");
        return code.toString();
    }

    private String parameterBinderArray(MapperCompilationModel.MethodModel methodModel) {
        if (methodModel.parameterBinderFields().isEmpty()
                || methodModel.parameterBinderFields().stream().allMatch(java.util.Objects::isNull)) {
            return "null";
        }
        return "new ParameterBinder<?>[]{" + methodModel.parameterBinderFields().stream()
            .map(field -> field == null ? "null" : field)
            .collect(java.util.stream.Collectors.joining(", ")) + "}";
    }

    private String rowMapperExpression(MapperCompilationModel.MethodModel methodModel) {
        return methodModel.rowMapperFieldName() == null ? "null" : methodModel.rowMapperFieldName();
    }

    private String typeRoutingExpression(MapperCompilationModel.MethodModel methodModel) {
        return typeRoutingExpression(methodModel, "null", "null");
    }

    private String typeRoutingExpression(
            MapperCompilationModel.MethodModel methodModel,
            String parameterTypes,
            String parameterJdbcTypes) {
        String resultTypes = methodModel.resultTypeNames().isEmpty()
            ? "new Class<?>[0]"
            : "new Class<?>[]{" + String.join(", ", methodModel.resultTypeNames()) + "}";
        String labels = methodModel.resultColumnLabels().isEmpty()
            ? "null"
            : "new String[]{" + methodModel.resultColumnLabels().stream()
                .map(this::javaString)
                .collect(java.util.stream.Collectors.joining(", ")) + "}";
        return "new ExecutionPlan.TypeRouting(typeHandlerManager, "
            + parameterTypes + ", " + parameterJdbcTypes + ", "
            + resultTypes + ", " + labels + ")";
    }

    private String generateAstLogic(AstNode astNode, MapperCompilationModel.MethodModel methodModel,
                                    String sqlVar, String paramsVar, String bindersVar, String indent,
                                    Set<String> localRoots, Iterator<String> parameterBinders)
        throws GenerationException {
        StringBuilder code = new StringBuilder();
        switch (astNode.getNodeType()) {
            case CONTAINER -> {
                Set<String> scope = new LinkedHashSet<>(localRoots);
                for (AstNode child : astNode.getChildren()) {
                    code.append(generateAstLogic(
                        child, methodModel, sqlVar, paramsVar, bindersVar, indent, scope, parameterBinders));
                    if (child instanceof AstNode.BindNode bindNode) {
                        scope.add(bindNode.name());
                    }
                }
            }
            case TEXT -> appendTextNode(code, ((AstNode.TextNode) astNode).text(), methodModel,
                sqlVar, paramsVar, bindersVar, indent, localRoots, parameterBinders);
            case IF -> {
                AstNode.IfNode ifNode = (AstNode.IfNode) astNode;
                code.append(indent).append("if (").append(translateCondition(ifNode.test(), methodModel, localRoots)).append(") {\n");
                for (AstNode child : ifNode.children()) {
                    code.append(generateAstLogic(child, methodModel, sqlVar, paramsVar, bindersVar,
                        indent + "    ", localRoots, parameterBinders));
                }
                code.append(indent).append("}\n");
            }
            case FOREACH -> {
                AstNode.ForeachNode foreachNode = (AstNode.ForeachNode) astNode;
                code.append(indent).append("{\n");
                String scopedIndent = indent + "    ";
                code.append(scopedIndent).append(sqlVar).append(".append(").append(javaString(foreachNode.open())).append(");\n");
                code.append(scopedIndent).append("boolean foreachFirstItem = true;\n");
                code.append(scopedIndent).append("for (").append(resolveForeachItemType(foreachNode, methodModel)).append(" ")
                    .append(foreachNode.item()).append(" : ")
                    .append(toJavaAccess(foreachNode.collection(), methodModel, false, localRoots)).append(") {\n");
                code.append(scopedIndent).append("    if (!foreachFirstItem) {\n");
                code.append(scopedIndent).append("        ").append(sqlVar).append(".append(")
                    .append(javaString(foreachNode.separator())).append(");\n");
                code.append(scopedIndent).append("    }\n");
                code.append(scopedIndent).append("    foreachFirstItem = false;\n");
                Set<String> foreachScope = new LinkedHashSet<>(localRoots);
                foreachScope.add(foreachNode.item());
                for (AstNode child : foreachNode.children()) {
                    code.append(generateAstLogic(child, methodModel, sqlVar, paramsVar, bindersVar,
                        scopedIndent + "    ", foreachScope, parameterBinders));
                }
                code.append(scopedIndent).append("}\n");
                code.append(scopedIndent).append(sqlVar).append(".append(").append(javaString(foreachNode.close())).append(");\n");
                code.append(indent).append("}\n");
            }
            case CHOOSE -> {
                AstNode.ChooseNode chooseNode = (AstNode.ChooseNode) astNode;
                code.append(indent).append("{\n");
                String scopedIndent = indent + "    ";
                String matchedVar = "chooseBranchMatched";
                code.append(scopedIndent).append("boolean ").append(matchedVar).append(" = false;\n");
                for (AstNode child : chooseNode.children()) {
                    if (child instanceof AstNode.WhenNode whenNode) {
                        code.append(scopedIndent).append("if (!").append(matchedVar).append(" && ")
                            .append(translateCondition(whenNode.test(), methodModel, localRoots)).append(") {\n");
                        code.append(scopedIndent).append("    ").append(matchedVar).append(" = true;\n");
                        for (AstNode whenChild : whenNode.children()) {
                            code.append(generateAstLogic(whenChild, methodModel, sqlVar, paramsVar, bindersVar,
                                scopedIndent + "    ", localRoots, parameterBinders));
                        }
                        code.append(scopedIndent).append("}\n");
                    } else if (child instanceof AstNode.OtherwiseNode otherwiseNode) {
                        code.append(scopedIndent).append("if (!").append(matchedVar).append(") {\n");
                        for (AstNode otherwiseChild : otherwiseNode.children()) {
                            code.append(generateAstLogic(otherwiseChild, methodModel, sqlVar, paramsVar, bindersVar,
                                scopedIndent + "    ", localRoots, parameterBinders));
                        }
                        code.append(scopedIndent).append("}\n");
                    }
                }
                code.append(indent).append("}\n");
            }
            case WHERE -> {
                code.append(indent).append("{\n");
                String scopedIndent = indent + "    ";
                String innerSql = "whereClauseSql";
                String innerParams = "whereClauseParameters";
                String innerBinders = "whereClauseBinders";
                code.append(scopedIndent).append("StringBuilder ").append(innerSql).append(" = new StringBuilder();\n");
                code.append(scopedIndent).append("List<Object> ").append(innerParams).append(" = new ArrayList<>();\n");
                code.append(scopedIndent).append("List<ParameterBinder<?>> ").append(innerBinders).append(" = new ArrayList<>();\n");
                for (AstNode child : astNode.getChildren()) {
                    code.append(generateAstLogic(child, methodModel, innerSql, innerParams, innerBinders,
                        scopedIndent, localRoots, parameterBinders));
                }
                code.append(scopedIndent).append("String normalizedWhereClause")
                    .append(" = normalizeWhereClause(").append(innerSql).append(".toString());\n");
                code.append(scopedIndent).append("if (!normalizedWhereClause.isBlank()) {\n");
                code.append(scopedIndent).append("    ").append(sqlVar).append(".append(\" WHERE \").append(normalizedWhereClause);\n");
                code.append(scopedIndent).append("    ").append(paramsVar).append(".addAll(").append(innerParams).append(");\n");
                code.append(scopedIndent).append("    ").append(bindersVar).append(".addAll(").append(innerBinders).append(");\n");
                code.append(scopedIndent).append("}\n");
                code.append(indent).append("}\n");
            }
            case SET -> {
                code.append(indent).append("{\n");
                String scopedIndent = indent + "    ";
                String innerSql = "setClauseSql";
                String innerParams = "setClauseParameters";
                String innerBinders = "setClauseBinders";
                code.append(scopedIndent).append("StringBuilder ").append(innerSql).append(" = new StringBuilder();\n");
                code.append(scopedIndent).append("List<Object> ").append(innerParams).append(" = new ArrayList<>();\n");
                code.append(scopedIndent).append("List<ParameterBinder<?>> ").append(innerBinders).append(" = new ArrayList<>();\n");
                for (AstNode child : astNode.getChildren()) {
                    code.append(generateAstLogic(child, methodModel, innerSql, innerParams, innerBinders,
                        scopedIndent, localRoots, parameterBinders));
                }
                code.append(scopedIndent).append("String normalizedSetClause")
                    .append(" = normalizeSetClause(").append(innerSql).append(".toString());\n");
                code.append(scopedIndent).append("if (normalizedSetClause.isBlank()) {\n");
                code.append(scopedIndent).append("    throw new IllegalStateException(")
                    .append(javaString("Dynamic <set> produced no assignments [statementId="
                        + methodModel.statementId() + "]"))
                    .append(");\n");
                code.append(scopedIndent).append("}\n");
                code.append(scopedIndent).append(sqlVar).append(".append(\" SET \").append(normalizedSetClause);\n");
                code.append(scopedIndent).append(paramsVar).append(".addAll(").append(innerParams).append(");\n");
                code.append(scopedIndent).append(bindersVar).append(".addAll(").append(innerBinders).append(");\n");
                code.append(indent).append("}\n");
            }
            case TRIM -> {
                AstNode.TrimNode trimNode = (AstNode.TrimNode) astNode;
                code.append(indent).append("{\n");
                String scopedIndent = indent + "    ";
                String innerSql = "trimmedClauseSql";
                String innerParams = "trimmedClauseParameters";
                String innerBinders = "trimmedClauseBinders";
                code.append(scopedIndent).append("StringBuilder ").append(innerSql).append(" = new StringBuilder();\n");
                code.append(scopedIndent).append("List<Object> ").append(innerParams).append(" = new ArrayList<>();\n");
                code.append(scopedIndent).append("List<ParameterBinder<?>> ").append(innerBinders).append(" = new ArrayList<>();\n");
                for (AstNode child : trimNode.children()) {
                    code.append(generateAstLogic(child, methodModel, innerSql, innerParams, innerBinders,
                        scopedIndent, localRoots, parameterBinders));
                }
                code.append(scopedIndent).append("String normalizedTrimmedClause = applyTrim(")
                    .append(innerSql).append(".toString(), ")
                    .append(javaString(trimNode.prefix())).append(", ")
                    .append(javaString(trimNode.suffix())).append(", ")
                    .append(javaString(trimNode.prefixOverrides())).append(", ")
                    .append(javaString(trimNode.suffixOverrides())).append(");\n");
                code.append(scopedIndent).append("if (!normalizedTrimmedClause.isBlank()) {\n");
                code.append(scopedIndent).append("    appendSqlFragment(").append(sqlVar)
                    .append(", normalizedTrimmedClause);\n");
                code.append(scopedIndent).append("    ").append(paramsVar).append(".addAll(").append(innerParams).append(");\n");
                code.append(scopedIndent).append("    ").append(bindersVar).append(".addAll(").append(innerBinders).append(");\n");
                code.append(scopedIndent).append("}\n");
                code.append(indent).append("}\n");
            }
            case BIND -> {
                AstNode.BindNode bindNode = (AstNode.BindNode) astNode;
                code.append(indent).append("Object ").append(bindNode.name()).append(" = ")
                    .append(translateBindExpression(bindNode.value(), methodModel, localRoots)).append(";\n");
            }
            case INCLUDE -> throw new GenerationException("Unresolved <include> fragment: " +
                ((AstNode.IncludeNode) astNode).refId());
            case WHEN, OTHERWISE -> {
            }
        }
        return code.toString();
    }

    private void appendTextNode(StringBuilder code, String text, MapperCompilationModel.MethodModel methodModel,
                                String sqlVar, String paramsVar, String bindersVar, String indent,
                                Set<String> localRoots, Iterator<String> parameterBinders)
            throws GenerationException {
        int cursor = 0;
        Matcher hashMatcher = HASH_PARAM_PATTERN.matcher(text);
        while (hashMatcher.find()) {
            SqlParameterParser.ParameterExpression parameterExpression =
                SqlParameterParser.parseParameterExpression(hashMatcher.group(1));
            String literal = text.substring(cursor, hashMatcher.start());
            appendLiteral(code, literal, sqlVar, indent);
            code.append(indent).append("appendSqlFragment(").append(sqlVar).append(", \"?\");\n");
            code.append(indent).append(paramsVar).append(".add(")
                .append(toJavaAccess(parameterExpression.expression(), methodModel, false, localRoots))
                .append(");\n");
            code.append(indent).append(bindersVar).append(".add(")
                .append(binderExpression(methodModel, parameterBinders)).append(");\n");
            cursor = hashMatcher.end();
        }
        String remainder = text.substring(cursor);
        appendLiteral(code, remainder, sqlVar, indent);
    }

    private String binderExpression(
            MapperCompilationModel.MethodModel methodModel,
            Iterator<String> parameterBinders) throws GenerationException {
        if (!parameterBinders.hasNext()) {
            throw new GenerationException(methodModel.statementId()
                + ": generated JDBC binder plan is missing an entry");
        }
        String binderField = parameterBinders.next();
        return binderField == null ? "null" : binderField;
    }

    private void appendLiteral(StringBuilder code, String text, String sqlVar, String indent) {
        int cursor = 0;
        Matcher dollarMatcher = DOLLAR_PARAM_PATTERN.matcher(text);
        while (dollarMatcher.find()) {
            String literal = text.substring(cursor, dollarMatcher.start());
            if (!literal.isEmpty()) {
                code.append(indent).append("appendSqlFragment(").append(sqlVar).append(", ")
                    .append(javaString(literal)).append(");\n");
            }
            code.append(indent).append(sqlVar).append(".append(String.valueOf(")
                .append(toJavaAccess(dollarMatcher.group(1).trim(), null, false, Set.of())).append("));\n");
            cursor = dollarMatcher.end();
        }
        String tail = text.substring(cursor);
        if (!tail.isEmpty()) {
            code.append(indent).append("appendSqlFragment(").append(sqlVar).append(", ")
                .append(javaString(tail)).append(");\n");
        }
    }

    private String translateCondition(String expression, MapperCompilationModel.MethodModel methodModel, Set<String> localRoots) {
        String translated = replaceTokens(expression, methodModel, localRoots);
        translated = EMPTY_STRING_NOT_EQUALS_PATTERN.matcher(translated).replaceAll("!$1.isEmpty()");
        translated = EMPTY_STRING_EQUALS_PATTERN.matcher(translated).replaceAll("$1.isEmpty()");
        translated = STRING_EQUALS_PATTERN.matcher(translated)
            .replaceAll("java.util.Objects.equals($1, \"$2\")");
        translated = STRING_NOT_EQUALS_PATTERN.matcher(translated)
            .replaceAll("!java.util.Objects.equals($1, \"$2\")");
        translated = translated.replaceAll("\\band\\b", "&&");
        translated = translated.replaceAll("\\bor\\b", "||");
        translated = translated.replace('\'', '"');
        return translated;
    }

    private String translateBindExpression(String expression, MapperCompilationModel.MethodModel methodModel,
                                           Set<String> localRoots) {
        return replaceTokens(expression, methodModel, localRoots).replace('\'', '"');
    }

    private String replaceTokens(String expression, MapperCompilationModel.MethodModel methodModel, Set<String> localRoots) {
        Matcher matcher = EXPRESSION_TOKEN_PATTERN.matcher(expression);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group();
            String replacement = switch (token) {
                case "null", "true", "false", "and", "or" -> token;
                default -> {
                    String resolved = toJavaAccess(token, methodModel, false, localRoots);
                    if (matcher.end() < expression.length() && expression.charAt(matcher.end()) == '('
                        && resolved.endsWith("()")) {
                        resolved = resolved.substring(0, resolved.length() - 2);
                    }
                    yield resolved;
                }
            };
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String toJavaAccess(String expression, MapperCompilationModel.MethodModel methodModel, boolean strict,
                                Set<String> localRoots) {
        if (methodModel == null) {
            String[] parts = expression.split("\\.");
            if (parts.length == 1) {
                return parts[0];
            }
            StringBuilder access = new StringBuilder(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                access.append(".").append(parts[i]).append("()");
            }
            return access.toString();
        }
        return parameterParser.toJavaAccess(expression, methodModel.methodParameters(), strict, localRoots);
    }

    private String resolveForeachItemType(AstNode.ForeachNode foreachNode, MapperCompilationModel.MethodModel methodModel) {
        String collection = foreachNode.collection();
        for (SqlParameterParser.MethodParameter parameter : methodModel.methodParameters()) {
            if (parameter.aliases().contains(collection) || parameter.runtimeName().equals(collection)) {
                String typeName = parameter.typeName();
                if (typeName.endsWith("[]")) {
                    return typeName.substring(0, typeName.length() - 2);
                }
                int genericStart = typeName.indexOf('<');
                int genericEnd = typeName.lastIndexOf('>');
                if (genericStart > 0 && genericEnd > genericStart) {
                    return typeName.substring(genericStart + 1, genericEnd).trim();
                }
                return "Object";
            }
        }
        return "Object";
    }

    private String javaString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "\"";
    }
}
