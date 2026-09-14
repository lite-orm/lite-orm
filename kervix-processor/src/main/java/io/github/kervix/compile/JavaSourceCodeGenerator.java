package io.github.kervix.compile;

import io.github.kervix.api.ExecutionPlan;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates Mapper source from compiler-owned semantic fragments.
 *
 * @author kervix
 * @since 2024/10/01
 */
final class JavaSourceCodeGenerator implements CodeGenerator {

    private static final List<String> GENERATED_IMPORTS = List.of(
        "io.github.kervix.api.BatchExecutionPlan",
        "io.github.kervix.api.BatchDefinition",
        "io.github.kervix.api.BoundSql",
        "io.github.kervix.api.BoundSqlBuilder",
        "io.github.kervix.api.BatchResult",
        "io.github.kervix.api.CommandDefinition",
        "io.github.kervix.api.ExecutionPlan",
        "io.github.kervix.api.GeneratedKeyResult",
        "io.github.kervix.api.ParameterBinder",
        "io.github.kervix.api.QueryDefinition",
        "io.github.kervix.api.QueryExecutionPlan",
        "io.github.kervix.api.QueryResult",
        "io.github.kervix.api.SqlExecutor",
        "io.github.kervix.api.SqlResult",
        "io.github.kervix.api.StatementOptions",
        "io.github.kervix.api.UpdateResult",
        "java.util.ArrayList",
        "java.util.List"
    );
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

    private final SqlParameterParser parameterParser;
    private final FreemarkerSourceRenderer sourceRenderer;

    JavaSourceCodeGenerator() {
        this.parameterParser = new SqlParameterParser();
        this.sourceRenderer = new FreemarkerSourceRenderer();
    }

    @Override
    public String generateMapperImpl(TypeElement mapperInterface, MapperCompilationModel compilationModel,
                                     Elements elementUtils, Types typeUtils) throws GenerationException {
        return sourceRenderer.render(buildSourceModel(compilationModel));
    }

    private GeneratedMapperSourceModel buildSourceModel(MapperCompilationModel compilationModel)
            throws GenerationException {
        List<GeneratedSourceField> fields = new ArrayList<>();
        for (MapperCompilationModel.MethodModel method : compilationModel.methods()) {
            if (method.providerClassName() != null) {
                fields.add(new GeneratedSourceField("    private final " + method.providerClassName() + " "
                    + method.providerFieldName() + " = new "
                    + method.providerClassName() + "();"));
            }
            for (MapperCompilationModel.ExtensionField extensionField : method.extensionFields()) {
                fields.add(new GeneratedSourceField("    private final " + extensionField.typeName() + " "
                    + extensionField.fieldName() + " = new "
                    + extensionField.typeName() + "();"));
            }
        }

        List<GeneratedSourceMember> members = new ArrayList<>();
        for (MapperCompilationModel.MethodModel method : compilationModel.methods()) {
            if (queryDefinition(method) || commandDefinition(method) || batchDefinition(method)) {
                members.add(new GeneratedTextMember(
                    GeneratedSourceMember.Kind.DEFINITION,
                    generateDefinition(method)));
            }
            members.add(new GeneratedMethodMember(generateMapperMethod(method)));
            members.add(new GeneratedTextMember(
                GeneratedSourceMember.Kind.EXECUTION_FACTORY,
                generateExecutionPlanFactory(method)));
            if (!method.resultMappingHelperCode().isBlank()) {
                members.add(new GeneratedTextMember(
                    GeneratedSourceMember.Kind.HELPER,
                    method.resultMappingHelperCode()));
            }
        }

        return new GeneratedMapperSourceModel(
            compilationModel.packageName(),
            compilationModel.interfaceName(),
            compilationModel.implementationName(),
            GENERATED_IMPORTS,
            fields,
            members
        );
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

        String definition = generateDefinition(methodModel);
        if (!definition.isBlank()) {
            code.append(definition).append("\n");
        }

        code.append(renderMapperMethod(generateMapperMethod(methodModel))).append("\n\n");
        code.append(generateExecutionPlanFactory(methodModel));
        return code.toString();
    }

    private String generateDefinition(MapperCompilationModel.MethodModel methodModel) {
        if (queryDefinition(methodModel)) {
            return generateQueryDefinition(methodModel);
        }
        if (commandDefinition(methodModel)) {
            return generateCommandDefinition(methodModel);
        }
        if (batchDefinition(methodModel)) {
            return generateBatchDefinition(methodModel);
        }
        return "";
    }

    private GeneratedMethodSource generateMapperMethod(MapperCompilationModel.MethodModel methodModel) {
        StringBuilder body = new StringBuilder();
        body.append("        ").append(executionPlanType(methodModel)).append(" executionPlan = ")
            .append(methodModel.executionPlanFactoryName()).append("(")
            .append(callArguments(methodModel)).append(");\n");
        if (methodModel.cursorCallbackParameterName() != null) {
            body.append("        return sqlExecutor.queryCursor(executionPlan, ")
                .append(methodModel.cursorCallbackParameterName()).append(");\n");
        } else {
            body.append("        ").append(executionResultType(methodModel))
                .append(" executionResult = ").append(executionCall(methodModel)).append(";\n");
            body.append(generateReturnCode(methodModel));
        }
        return new GeneratedMethodSource(
            "    /**\n"
                + "     * Mapper method: " + mapperMethodLocation(methodModel) + "\n"
                + "     * SQL source: " + methodModel.sourceType().name() + "\n"
                + "     */",
            methodModel.returnType(),
            methodModel.methodName(),
            methodModel.parameterList(),
            body.toString()
        );
    }

    private String renderMapperMethod(GeneratedMethodSource method) {
        return method.documentation() + "\n"
            + "    @Override\n"
            + "    public " + method.returnType() + " " + method.methodName()
            + "(" + method.parameters() + ") {\n"
            + method.body()
            + "    }";
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
        boolean isSelect = methodModel.statementType() == io.github.kervix.api.ExecutionPlan.StatementType.SELECT;

        if (methodModel.statementType() == io.github.kervix.api.ExecutionPlan.StatementType.BATCH) {
            return "        return executionResult.counts();\n";
        }

        if (methodModel.generatedKey()) {
            return "        return executionResult.key();\n";
        }

        if (!isSelect) {
            if ("void".equals(returnType)) {
                return "        return;\n";
            }
            return "        return executionResult.count();\n";
        }

        if (returnType.contains("List<")) {
            return "        return new ArrayList<>(executionResult.rows());\n";
        }

        if ("void".equals(returnType)) {
            return "        return;\n";
        }

        boolean optional = returnType.startsWith("java.util.Optional<");
        boolean primitive = isPrimitive(returnType);
        if (optional) {
            return "        return executionResult.optional();\n";
        }
        return "        return executionResult." + (primitive ? "required()" : "oneOrNull()") + ";\n";
    }

    private boolean isPrimitive(String typeName) {
        return switch (typeName) {
            case "boolean", "byte", "short", "int", "long", "char", "float", "double" -> true;
            default -> false;
        };
    }

    private String generatedKeyType(MapperCompilationModel.MethodModel methodModel) {
        return switch (methodModel.returnType()) {
            case "int" -> "java.lang.Integer";
            case "long" -> "java.lang.Long";
            case "short" -> "java.lang.Short";
            case "byte" -> "java.lang.Byte";
            case "double" -> "java.lang.Double";
            case "float" -> "java.lang.Float";
            default -> methodModel.returnType();
        };
    }

    private String mapperMethodLocation(MapperCompilationModel.MethodModel methodModel) {
        int methodSeparator = methodModel.statementId().lastIndexOf('.');
        if (methodSeparator < 0) {
            return methodModel.statementId();
        }
        return methodModel.statementId().substring(0, methodSeparator) + "#"
            + methodModel.statementId().substring(methodSeparator + 1);
    }

    private String generateExecutionPlanFactory(MapperCompilationModel.MethodModel methodModel) throws GenerationException {
        StringBuilder code = new StringBuilder();
        code.append("    private ").append(executionPlanType(methodModel)).append(" ")
            .append(methodModel.executionPlanFactoryName()).append("(")
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
            code.append("        return ").append(definitionFieldName(methodModel))
                .append(".bind(batchParameters);\n");
        } else if (methodModel.providerClassName() != null) {
            code.append("        BoundSql boundSql = BoundSql.requireValid(")
                .append(methodModel.providerFieldName()).append(".provide(")
                .append(methodModel.providerArgumentExpression()).append("), ")
                .append(javaString(methodModel.statementId())).append(");\n");
            if (queryDefinition(methodModel) || commandDefinition(methodModel)) {
                code.append("        return ").append(definitionFieldName(methodModel))
                    .append(".bind(boundSql);\n");
            } else {
                code.append("        return ").append(executionPlanConstructor(methodModel)).append("(")
                .append(javaString(methodModel.statementId())).append(", boundSql.sql(), ")
                .append("boundSql.parameterValues(), ExecutionPlan.StatementType.")
                .append(methodModel.statementType().name()).append(", ExecutionPlan.SqlSource.GENERATED, null, ")
                .append("boundSql.parameterBinders()").append(", ")
                .append(mappingStrategyArguments(methodModel)).append(", StatementOptions.defaults(), ")
                .append(typeRoutingExpression(
                    methodModel, "boundSql.parameterTypes()", "boundSql.parameterJdbcTypes()"))
                .append(");\n");
            }
        } else if (methodModel.dynamic()) {
            code.append("        BoundSqlBuilder sql = BoundSqlBuilder.create(")
                .append(javaString(methodModel.statementId())).append(");\n");
            Iterator<MapperCompilationModel.ParameterRoute> parameterRoutes =
                methodModel.parameterRoutes().iterator();
            if (methodModel.astNode() != null) {
                code.append(generateAstLogic(
                    methodModel.astNode(),
                    methodModel,
                    "sql",
                    "        ",
                    new LinkedHashSet<>(),
                    parameterRoutes
                ));
            } else {
                appendTextNode(code, methodModel.sqlTemplate(), methodModel,
                    "sql", "        ", Set.of(), parameterRoutes);
            }
            if (parameterRoutes.hasNext()) {
                throw new GenerationException(methodModel.statementId()
                    + ": generated JDBC parameter route contains unused entries");
            }
            if (queryDefinition(methodModel) || commandDefinition(methodModel)) {
                code.append("        return ").append(definitionFieldName(methodModel))
                    .append(".bind(sql.build());\n");
            } else {
                code.append("        BoundSql boundSql = sql.build();\n");
                code.append("        return ").append(executionPlanConstructor(methodModel)).append("(")
                .append(javaString(methodModel.statementId())).append(", ")
                .append("boundSql.sql(), boundSql.parameterValues(), ")
                .append("ExecutionPlan.StatementType.").append(methodModel.statementType().name()).append(", ")
                .append("ExecutionPlan.SqlSource.").append(methodModel.sourceType().name()).append(", ")
                .append(javaString(methodModel.generatedKeyColumn())).append(", ")
                .append("boundSql.parameterBinders(), ")
                .append(mappingStrategyArguments(methodModel)).append(", StatementOptions.defaults(), ")
                .append(typeRoutingExpression(
                    methodModel,
                    "boundSql.parameterTypes()", "boundSql.parameterJdbcTypes()"))
                .append(");\n");
            }
        } else {
            if (queryDefinition(methodModel) || commandDefinition(methodModel)) {
                code.append("        return ").append(definitionFieldName(methodModel))
                    .append(".bind(").append(staticBindingArguments(methodModel)).append(");\n");
            } else {
                code.append("        String sql = ").append(javaString(methodModel.sqlTemplate())).append(";\n");
                code.append(parameterParser.generateParameterBindingCode(methodModel.parameterBindings()));
                code.append("        return ").append(executionPlanConstructor(methodModel)).append("(")
                .append(javaString(methodModel.statementId())).append(", ")
                .append("sql, params, ExecutionPlan.StatementType.").append(methodModel.statementType().name()).append(", ")
                .append("ExecutionPlan.SqlSource.").append(methodModel.sourceType().name()).append(", ")
                .append(javaString(methodModel.generatedKeyColumn())).append(", ")
                .append(parameterBinderArray(methodModel)).append(", ")
                .append(mappingStrategyArguments(methodModel)).append(", StatementOptions.defaults(), ")
                .append(typeRoutingExpression(methodModel)).append(");\n");
            }
        }

        code.append("    }\n");
        return code.toString();
    }

    private String generateQueryDefinition(MapperCompilationModel.MethodModel methodModel) {
        boolean invocationSql = methodModel.dynamic() || methodModel.providerClassName() != null;
        boolean rowMapped = methodModel.rowMapperFieldName() != null;
        StringBuilder definition = new StringBuilder("    private ");
        if (staticQueryDefinition(methodModel)) {
            definition.append("static ");
        }
        definition.append("final QueryDefinition<").append(mappedResultType(methodModel)).append("> ")
            .append(definitionFieldName(methodModel)).append(" = QueryDefinition.")
            .append(rowMapped ? "rowMapped(" : "assembled(")
            .append(javaString(methodModel.statementId())).append(", ");
        if (!invocationSql) {
            definition.append(javaString(methodModel.sqlTemplate())).append(", ");
        }
        definition.append("ExecutionPlan.SqlSource.").append(methodModel.sourceType().name()).append(", ")
            .append(rowMapped ? rowMapperExpression(methodModel) : resultAssemblerExpression(methodModel))
            .append(", ");
        if (!invocationSql) {
            definition.append(parameterBinderArray(methodModel)).append(", ");
        }
        definition.append("StatementOptions.defaults(), ")
            .append(invocationSql
                ? typeRoutingExpression(methodModel, "new Class<?>[0]", "null")
                : typeRoutingExpression(methodModel))
            .append(");\n");
        return definition.toString();
    }

    private boolean queryDefinition(MapperCompilationModel.MethodModel methodModel) {
        return typedQuery(methodModel);
    }

    private boolean commandDefinition(MapperCompilationModel.MethodModel methodModel) {
        return methodModel.statementType() != ExecutionPlan.StatementType.SELECT
            && methodModel.statementType() != ExecutionPlan.StatementType.BATCH;
    }

    private boolean batchDefinition(MapperCompilationModel.MethodModel methodModel) {
        return methodModel.statementType() == ExecutionPlan.StatementType.BATCH;
    }

    private String generateCommandDefinition(MapperCompilationModel.MethodModel methodModel) {
        boolean invocationSql = methodModel.dynamic() || methodModel.providerClassName() != null;
        boolean rowMappedKey = methodModel.generatedKey() && methodModel.rowMapperFieldName() != null;
        String factory = rowMappedKey ? "rowMappedGeneratedKey"
            : methodModel.generatedKey() ? "generatedKey" : "command";
        StringBuilder definition = new StringBuilder("    private ");
        if (methodModel.extensionFields().isEmpty()) definition.append("static ");
        definition.append("final CommandDefinition ").append(definitionFieldName(methodModel))
            .append(" = CommandDefinition.").append(factory).append("(")
            .append(javaString(methodModel.statementId())).append(", ");
        if (!invocationSql) definition.append(javaString(methodModel.sqlTemplate())).append(", ");
        if (!methodModel.generatedKey()) {
            definition.append("ExecutionPlan.StatementType.")
                .append(methodModel.statementType().name()).append(", ");
        }
        definition.append("ExecutionPlan.SqlSource.").append(methodModel.sourceType().name()).append(", ");
        if (methodModel.generatedKey()) {
            definition.append(javaString(methodModel.generatedKeyColumn())).append(", ");
            if (rowMappedKey) definition.append(rowMapperExpression(methodModel)).append(", ");
        }
        if (!invocationSql) definition.append(parameterBinderArray(methodModel)).append(", ");
        definition.append("StatementOptions.defaults(), ")
            .append(invocationSql
                ? typeRoutingExpression(methodModel, "new Class<?>[0]", "null")
                : typeRoutingExpression(methodModel))
            .append(");\n");
        return definition.toString();
    }

    private String generateBatchDefinition(MapperCompilationModel.MethodModel methodModel) {
        String modifier = methodModel.extensionFields().isEmpty() ? "static " : "";
        return "    private " + modifier + "final BatchDefinition " + definitionFieldName(methodModel)
            + " = new BatchDefinition(" + javaString(methodModel.statementId()) + ", "
            + javaString(methodModel.sqlTemplate()) + ", ExecutionPlan.SqlSource."
            + methodModel.sourceType().name() + ", " + parameterBinderArray(methodModel)
            + ", StatementOptions.defaults(), " + typeRoutingExpression(methodModel) + ");\n";
    }

    private boolean staticQueryDefinition(MapperCompilationModel.MethodModel methodModel) {
        return queryDefinition(methodModel)
            && methodModel.rowMapperFieldName() == null
            && (methodModel.dynamic() || methodModel.providerClassName() != null
                || methodModel.parameterRoutes().stream()
                .noneMatch(route -> route.binderFieldName() != null));
    }

    private String definitionFieldName(MapperCompilationModel.MethodModel methodModel) {
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < methodModel.methodName().length(); index++) {
            char current = methodModel.methodName().charAt(index);
            if (Character.isUpperCase(current) && index > 0) {
                name.append('_');
            }
            name.append(Character.toUpperCase(current));
        }
        return name.append("_DEFINITION").toString();
    }

    private String staticBindingArguments(MapperCompilationModel.MethodModel methodModel) {
        return methodModel.parameterBindings().stream()
            .map(SqlParameterParser.ParameterBinding::accessCode)
            .collect(java.util.stream.Collectors.joining(", "));
    }

    private String parameterBinderArray(MapperCompilationModel.MethodModel methodModel) {
        if (methodModel.parameterRoutes().isEmpty()
                || methodModel.parameterRoutes().stream()
                    .allMatch(route -> route.binderFieldName() == null)) {
            return "null";
        }
        return "new ParameterBinder<?>[]{" + methodModel.parameterRoutes().stream()
            .map(route -> route.binderFieldName() == null ? "null" : route.binderFieldName())
            .collect(java.util.stream.Collectors.joining(", ")) + "}";
    }

    private String rowMapperExpression(MapperCompilationModel.MethodModel methodModel) {
        return methodModel.rowMapperFieldName() == null ? "null" : methodModel.rowMapperFieldName();
    }

    private String mappingStrategyArguments(MapperCompilationModel.MethodModel methodModel) {
        String rowMapper = rowMapperExpression(methodModel);
        return typedQuery(methodModel)
            ? rowMapper + ", " + resultAssemblerExpression(methodModel)
            : rowMapper;
    }

    private String resultAssemblerExpression(MapperCompilationModel.MethodModel methodModel) {
        return methodModel.rowMapperFieldName() == null
            ? "row -> " + methodModel.resultMappingCode()
            : "null";
    }

    private String executionPlanType(MapperCompilationModel.MethodModel methodModel) {
        if (methodModel.statementType() == ExecutionPlan.StatementType.BATCH) {
            return "BatchExecutionPlan";
        }
        return typedQuery(methodModel)
            ? "QueryExecutionPlan<" + mappedResultType(methodModel) + ">"
            : "ExecutionPlan";
    }

    private String executionPlanConstructor(MapperCompilationModel.MethodModel methodModel) {
        return typedQuery(methodModel) ? "new QueryExecutionPlan<>" : "new ExecutionPlan";
    }

    private String executionResultType(MapperCompilationModel.MethodModel methodModel) {
        if (methodModel.statementType() == ExecutionPlan.StatementType.SELECT && typedQuery(methodModel)) {
            return "QueryResult<" + mappedResultType(methodModel) + ">";
        }
        if (methodModel.statementType() == ExecutionPlan.StatementType.SELECT) {
            return "SqlResult<?>";
        }
        if (methodModel.statementType() == ExecutionPlan.StatementType.BATCH) {
            return "BatchResult";
        }
        if (methodModel.generatedKey()) {
            return "GeneratedKeyResult<" + generatedKeyType(methodModel) + ">";
        }
        return "UpdateResult";
    }

    private String executionCall(MapperCompilationModel.MethodModel methodModel) {
        if (methodModel.statementType() == ExecutionPlan.StatementType.SELECT && typedQuery(methodModel)) {
            return "sqlExecutor.query(executionPlan)";
        }
        if (methodModel.statementType() == ExecutionPlan.StatementType.SELECT) {
            return "sqlExecutor.execute(executionPlan)";
        }
        if (methodModel.statementType() == ExecutionPlan.StatementType.BATCH) {
            return "sqlExecutor.batch(executionPlan)";
        }
        if (methodModel.generatedKey()) {
            return "sqlExecutor.generatedKey(executionPlan)";
        }
        return "sqlExecutor.update(executionPlan)";
    }

    private boolean typedQuery(MapperCompilationModel.MethodModel methodModel) {
        return methodModel.statementType() == ExecutionPlan.StatementType.SELECT
            && methodModel.cursorCallbackParameterName() == null
            && !"void".equals(methodModel.returnType());
    }

    private String mappedResultType(MapperCompilationModel.MethodModel methodModel) {
        String returnType = methodModel.returnType();
        if (returnType.contains("List<")) {
            return returnType.substring(returnType.indexOf('<') + 1, returnType.lastIndexOf('>')).trim();
        }
        if (returnType.startsWith("java.util.Optional<")) {
            return returnType.substring(returnType.indexOf('<') + 1, returnType.lastIndexOf('>')).trim();
        }
        return switch (returnType) {
            case "boolean" -> "java.lang.Boolean";
            case "byte" -> "java.lang.Byte";
            case "short" -> "java.lang.Short";
            case "int" -> "java.lang.Integer";
            case "long" -> "java.lang.Long";
            case "char" -> "java.lang.Character";
            case "float" -> "java.lang.Float";
            case "double" -> "java.lang.Double";
            default -> returnType;
        };
    }

    private String typeRoutingExpression(MapperCompilationModel.MethodModel methodModel) {
        String parameterTypes = methodModel.parameterRoutes().isEmpty()
            ? "new Class<?>[0]"
            : "new Class<?>[]{" + methodModel.parameterRoutes().stream()
                .map(MapperCompilationModel.ParameterRoute::javaTypeExpression)
                .collect(java.util.stream.Collectors.joining(", ")) + "}";
        String parameterJdbcTypes = methodModel.parameterRoutes().isEmpty()
            ? "null"
            : "new java.sql.JDBCType[]{" + methodModel.parameterRoutes().stream()
                .map(MapperCompilationModel.ParameterRoute::jdbcTypeExpression)
                .collect(java.util.stream.Collectors.joining(", ")) + "}";
        return typeRoutingExpression(methodModel, parameterTypes, parameterJdbcTypes);
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
        return "new ExecutionPlan.TypeRouting("
            + parameterTypes + ", " + parameterJdbcTypes + ", "
            + resultTypes + ", " + labels + ")";
    }

    private String generateAstLogic(AstNode astNode, MapperCompilationModel.MethodModel methodModel,
                                    String sqlVar, String indent,
                                    Set<String> localRoots,
                                    Iterator<MapperCompilationModel.ParameterRoute> parameterRoutes)
        throws GenerationException {
        StringBuilder code = new StringBuilder();
        switch (astNode.getNodeType()) {
            case CONTAINER -> {
                Set<String> scope = new LinkedHashSet<>(localRoots);
                for (AstNode child : astNode.getChildren()) {
                    code.append(generateAstLogic(
                        child, methodModel, sqlVar, indent, scope, parameterRoutes));
                    if (child instanceof AstNode.BindNode bindNode) {
                        scope.add(bindNode.name());
                    }
                }
            }
            case TEXT -> appendTextNode(code, ((AstNode.TextNode) astNode).text(), methodModel,
                sqlVar, indent, localRoots, parameterRoutes);
            case IF -> {
                AstNode.IfNode ifNode = (AstNode.IfNode) astNode;
                code.append(indent).append("if (").append(translateCondition(ifNode.test(), methodModel, localRoots)).append(") {\n");
                for (AstNode child : ifNode.children()) {
                    code.append(generateAstLogic(child, methodModel, sqlVar,
                        indent + "    ", localRoots, parameterRoutes));
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
                    code.append(generateAstLogic(child, methodModel, sqlVar,
                        scopedIndent + "    ", foreachScope, parameterRoutes));
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
                            code.append(generateAstLogic(whenChild, methodModel, sqlVar,
                                scopedIndent + "    ", localRoots, parameterRoutes));
                        }
                        code.append(scopedIndent).append("}\n");
                    } else if (child instanceof AstNode.OtherwiseNode otherwiseNode) {
                        code.append(scopedIndent).append("if (!").append(matchedVar).append(") {\n");
                        for (AstNode otherwiseChild : otherwiseNode.children()) {
                            code.append(generateAstLogic(otherwiseChild, methodModel, sqlVar,
                                scopedIndent + "    ", localRoots, parameterRoutes));
                        }
                        code.append(scopedIndent).append("}\n");
                    }
                }
                code.append(indent).append("}\n");
            }
            case WHERE -> {
                code.append(indent).append("{\n");
                String scopedIndent = indent + "    ";
                String innerSql = "whereClause";
                code.append(scopedIndent).append("BoundSqlBuilder ").append(innerSql)
                    .append(" = ").append(sqlVar).append(".fragment();\n");
                for (AstNode child : astNode.getChildren()) {
                    code.append(generateAstLogic(child, methodModel, innerSql,
                        scopedIndent, localRoots, parameterRoutes));
                }
                code.append(scopedIndent).append(sqlVar).append(".where(")
                    .append(innerSql).append(");\n");
                code.append(indent).append("}\n");
            }
            case SET -> {
                code.append(indent).append("{\n");
                String scopedIndent = indent + "    ";
                String innerSql = "setClause";
                code.append(scopedIndent).append("BoundSqlBuilder ").append(innerSql)
                    .append(" = ").append(sqlVar).append(".fragment();\n");
                for (AstNode child : astNode.getChildren()) {
                    code.append(generateAstLogic(child, methodModel, innerSql,
                        scopedIndent, localRoots, parameterRoutes));
                }
                code.append(scopedIndent).append(sqlVar).append(".set(")
                    .append(innerSql).append(");\n");
                code.append(indent).append("}\n");
            }
            case TRIM -> {
                AstNode.TrimNode trimNode = (AstNode.TrimNode) astNode;
                code.append(indent).append("{\n");
                String scopedIndent = indent + "    ";
                String innerSql = "trimmedClause";
                code.append(scopedIndent).append("BoundSqlBuilder ").append(innerSql)
                    .append(" = ").append(sqlVar).append(".fragment();\n");
                for (AstNode child : trimNode.children()) {
                    code.append(generateAstLogic(child, methodModel, innerSql,
                        scopedIndent, localRoots, parameterRoutes));
                }
                code.append(scopedIndent).append(sqlVar).append(".trim(")
                    .append(innerSql).append(", ")
                    .append(javaString(trimNode.prefix())).append(", ")
                    .append(javaString(trimNode.suffix())).append(", ")
                    .append(javaString(trimNode.prefixOverrides())).append(", ")
                    .append(javaString(trimNode.suffixOverrides())).append(");\n");
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
                                String sqlVar, String indent,
                                Set<String> localRoots,
                                Iterator<MapperCompilationModel.ParameterRoute> parameterRoutes)
            throws GenerationException {
        int cursor = 0;
        Matcher hashMatcher = HASH_PARAM_PATTERN.matcher(text);
        while (hashMatcher.find()) {
            SqlParameterParser.ParameterExpression parameterExpression =
                SqlParameterParser.parseParameterExpression(hashMatcher.group(1));
            String literal = text.substring(cursor, hashMatcher.start());
            appendLiteral(code, literal, sqlVar, indent);
            MapperCompilationModel.ParameterRoute parameterRoute =
                parameterRoute(methodModel, parameterRoutes);
            code.append(indent).append(sqlVar).append(".parameter(")
                .append(toJavaAccess(parameterExpression.expression(), methodModel, false, localRoots))
                .append(", ")
                .append(parameterRoute.binderFieldName() == null
                    ? "null" : parameterRoute.binderFieldName()).append(", ")
                .append(parameterRoute.javaTypeExpression()).append(", ")
                .append(parameterRoute.jdbcTypeExpression()).append(");\n");
            cursor = hashMatcher.end();
        }
        String remainder = text.substring(cursor);
        appendLiteral(code, remainder, sqlVar, indent);
    }

    private MapperCompilationModel.ParameterRoute parameterRoute(
            MapperCompilationModel.MethodModel methodModel,
            Iterator<MapperCompilationModel.ParameterRoute> parameterRoutes) throws GenerationException {
        if (!parameterRoutes.hasNext()) {
            throw new GenerationException(methodModel.statementId()
                + ": generated JDBC parameter route is missing an entry");
        }
        return parameterRoutes.next();
    }

    private void appendLiteral(StringBuilder code, String text, String sqlVar, String indent) {
        int cursor = 0;
        Matcher dollarMatcher = DOLLAR_PARAM_PATTERN.matcher(text);
        while (dollarMatcher.find()) {
            String literal = text.substring(cursor, dollarMatcher.start());
            if (!literal.isEmpty()) {
                code.append(indent).append(sqlVar).append(".append(")
                    .append(javaString(literal)).append(");\n");
            }
            code.append(indent).append(sqlVar).append(".append(String.valueOf(")
                .append(toJavaAccess(dollarMatcher.group(1).trim(), null, false, Set.of())).append("));\n");
            cursor = dollarMatcher.end();
        }
        String tail = text.substring(cursor);
        if (!tail.isEmpty()) {
            code.append(indent).append(sqlVar).append(".append(")
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
