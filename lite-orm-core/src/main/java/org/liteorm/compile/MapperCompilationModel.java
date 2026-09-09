package org.liteorm.compile;

import org.liteorm.api.ExecutionPlan;

import java.util.List;

/**
 * Validated compile-time Mapper model.
 *
 * @author lite-orm
 * @since 2026/03/26
 */
record MapperCompilationModel(
    String packageName,
    String interfaceName,
    String implementationName,
    String qualifiedInterfaceName,
    List<MethodModel> methods
) {

    public record MethodModel(
        String methodName,
        String returnType,
        String parameterList,
        String executionPlanFactoryName,
        String statementId,
        ExecutionPlan.StatementType statementType,
        ExecutionPlan.SqlSource sourceType,
        String sqlTemplate,
        boolean dynamic,
        String generatedKeyColumn,
        String resultType,
        String resultMappingCode,
        String resultMappingHelperCode,
        List<String> resultColumnLabels,
        List<String> resultTypeNames,
        String providerClassName,
        String providerFieldName,
        String providerArgumentExpression,
        List<ExtensionField> extensionFields,
        List<ParameterRoute> parameterRoutes,
        String rowMapperFieldName,
        List<SqlParameterParser.MethodParameter> methodParameters,
        List<SqlParameterParser.ParameterBinding> parameterBindings,
        AstNode astNode,
        String executionPlanParameterList,
        String cursorCallbackParameterName
    ) {
        public boolean generatedKey() {
            return generatedKeyColumn != null;
        }

        public MethodModel(
            String methodName,
            String returnType,
            String parameterList,
            String executionPlanFactoryName,
            String statementId,
            ExecutionPlan.StatementType statementType,
            ExecutionPlan.SqlSource sourceType,
            String sqlTemplate,
            boolean dynamic,
            String resultType,
            String resultMappingCode,
            String resultMappingHelperCode,
            List<String> resultColumnLabels,
            String providerClassName,
            String providerFieldName,
            String providerArgumentExpression,
            List<ExtensionField> extensionFields,
            List<ParameterRoute> parameterRoutes,
            String rowMapperFieldName,
            List<SqlParameterParser.MethodParameter> methodParameters,
            List<SqlParameterParser.ParameterBinding> parameterBindings,
            AstNode astNode
        ) {
            this(methodName, returnType, parameterList, executionPlanFactoryName, statementId, statementType,
                sourceType, sqlTemplate, dynamic, null, resultType, resultMappingCode,
                resultMappingHelperCode, resultColumnLabels, List.of(), providerClassName, providerFieldName, providerArgumentExpression,
                extensionFields, parameterRoutes, rowMapperFieldName,
                methodParameters, parameterBindings,
                astNode, parameterList, null);
        }
    }

    public record ExtensionField(String typeName, String fieldName) {
    }

    record ParameterRoute(
        String binderFieldName,
        String javaTypeExpression,
        String jdbcTypeExpression
    ) {
    }

}
