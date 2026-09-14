package io.github.lynxus.api;

import java.util.List;
import java.util.Objects;

/**
 * Immutable input for one JDBC batch execution.
 */
public final class BatchExecutionPlan extends ExecutionPlan {

    private final List<Object[]> batchParameters;

    BatchExecutionPlan(Definition definition, String sql, List<Object[]> batchParameters) {
        super(definition, sql, new Object[0]);
        this.batchParameters = copy(Objects.requireNonNull(batchParameters, "batchParameters"));
    }

    public BatchExecutionPlan(
            String statementId,
            String sql,
            List<Object[]> batchParameters,
            SqlSource sourceType) {
        this(statementId, sql, batchParameters, sourceType, null);
    }

    public BatchExecutionPlan(
            String statementId,
            String sql,
            List<Object[]> batchParameters,
            SqlSource sourceType,
            ParameterBinder<?>[] parameterBinders) {
        this(statementId, sql, batchParameters, sourceType, parameterBinders, StatementOptions.defaults());
    }

    public BatchExecutionPlan(
            String statementId,
            String sql,
            List<Object[]> batchParameters,
            SqlSource sourceType,
            ParameterBinder<?>[] parameterBinders,
            StatementOptions statementOptions) {
        this(statementId, sql, batchParameters, sourceType, parameterBinders, statementOptions, null);
    }

    public BatchExecutionPlan(
            String statementId,
            String sql,
            List<Object[]> batchParameters,
            SqlSource sourceType,
            ParameterBinder<?>[] parameterBinders,
            StatementOptions statementOptions,
            TypeRouting typeRouting) {
        super(statementId, sql, new Object[0], StatementType.BATCH, sourceType,
            null, parameterBinders, null, statementOptions, typeRouting);
        this.batchParameters = copy(Objects.requireNonNull(batchParameters, "batchParameters"));
    }

    public List<Object[]> getBatchParameters() {
        return copy(batchParameters);
    }

    private static List<Object[]> copy(List<Object[]> parameters) {
        return parameters.stream()
            .map(values -> Objects.requireNonNull(values, "batch parameter set").clone())
            .toList();
    }
}
