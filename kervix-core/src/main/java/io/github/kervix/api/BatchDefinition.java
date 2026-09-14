package io.github.kervix.api;

import java.util.List;
import java.util.Objects;

/**
 * Immutable compile-time definition of one fixed JDBC batch statement.
 *
 * <p>The definition retains no batch values and is thread-safe when its configured parameter
 * binders are thread-safe.</p>
 */
public final class BatchDefinition {

    private final String statementId;
    private final String sql;
    private final ExecutionPlan.SqlSource sourceType;
    private final ParameterBinder<?>[] parameterBinders;
    private final StatementOptions statementOptions;
    private final ExecutionPlan.TypeRouting typeRouting;
    private final ExecutionPlan.Definition planDefinition;

    /**
     * Creates a fixed batch definition and defensively copies its binder metadata.
     * A {@code null} options value selects {@link StatementOptions#defaults()}.
     */
    public BatchDefinition(
            String statementId, String sql, ExecutionPlan.SqlSource sourceType,
            ParameterBinder<?>[] parameterBinders, StatementOptions statementOptions,
            ExecutionPlan.TypeRouting typeRouting) {
        this.statementId = Objects.requireNonNull(statementId, "statementId");
        this.sql = Objects.requireNonNull(sql, "sql");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.parameterBinders = parameterBinders == null ? null : parameterBinders.clone();
        this.statementOptions = statementOptions == null ? StatementOptions.defaults() : statementOptions;
        this.typeRouting = typeRouting;
        this.planDefinition = new ExecutionPlan.Definition(
            statementId, ExecutionPlan.StatementType.BATCH, sourceType, null,
            this.parameterBinders, null, this.statementOptions, typeRouting);
    }

    /** Binds one invocation's parameter sets; the returned plan owns defensive copies. */
    public BatchExecutionPlan bind(List<Object[]> parameterSets) {
        return new BatchExecutionPlan(planDefinition, sql, parameterSets);
    }
}
