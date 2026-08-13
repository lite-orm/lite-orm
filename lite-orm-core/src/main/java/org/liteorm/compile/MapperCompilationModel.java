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
        boolean requiresTransaction,
        String resultType,
        String resultMappingCode,
        String resultMappingHelperCode,
        String providerClassName,
        String providerFieldName,
        String providerArgumentExpression,
        List<SqlParameterParser.MethodParameter> methodParameters,
        List<SqlParameterParser.ParameterBinding> parameterBindings,
        AstNode astNode
    ) {
    }
}
