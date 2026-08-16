package org.liteorm.compile;

import org.liteorm.api.ExecutionPlan;

import java.util.List;

/**
 * Mapper编译期标准模型。
 *
 * @author lite-orm
 * @since 2026/03/26
 */
public record MapperCompilationModel(
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
        boolean generatedKey,
        String resultType,
        String resultMappingCode,
        String resultMappingHelperCode,
        List<String> resultColumnLabels,
        String providerClassName,
        String providerFieldName,
        String providerArgumentExpression,
        List<AdapterField> adapterFields,
        List<String> parameterBinderFields,
        String rowMapperFieldName,
        List<SqlParameterParser.MethodParameter> methodParameters,
        List<SqlParameterParser.ParameterBinding> parameterBindings,
        AstNode astNode
    ) {
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
            List<AdapterField> adapterFields,
            List<String> parameterBinderFields,
            String rowMapperFieldName,
            List<SqlParameterParser.MethodParameter> methodParameters,
            List<SqlParameterParser.ParameterBinding> parameterBindings,
            AstNode astNode
        ) {
            this(methodName, returnType, parameterList, executionPlanFactoryName, statementId, statementType,
                sourceType, sqlTemplate, dynamic, false, resultType, resultMappingCode,
                resultMappingHelperCode, resultColumnLabels, providerClassName, providerFieldName, providerArgumentExpression,
                adapterFields, parameterBinderFields, rowMapperFieldName, methodParameters, parameterBindings,
                astNode);
        }
    }

    public record AdapterField(String typeName, String fieldName) {
    }
}
