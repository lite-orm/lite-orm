package io.github.lynxus.api;

import java.util.Objects;

/**
 * Immutable compile-time definition of one query with fixed SQL and mapping metadata.
 *
 * <p>Generated Mapper implementations may retain a definition in a static field. Invocation
 * parameter values are supplied only to {@link #bind(Object...)}, and are never retained by the
 * definition. Definitions are thread-safe when their configured mapping strategy is thread-safe.</p>
 *
 * @param <T> mapped query result type
 */
public final class QueryDefinition<T> {

    private final String statementId;
    private final String sql;
    private final ExecutionPlan.SqlSource sourceType;
    private final ParameterBinder<?>[] parameterBinders;
    private final RowMapper<T> rowMapper;
    private final ResultAssembler<T> resultAssembler;
    private final StatementOptions statementOptions;
    private final ExecutionPlan.TypeRouting typeRouting;
    private final ExecutionPlan.Definition planDefinition;

    /** Creates a definition using values converted by Core and a generated result assembler. */
    public static <T> QueryDefinition<T> assembled(
            String statementId,
            String sql,
            ExecutionPlan.SqlSource sourceType,
            ResultAssembler<T> resultAssembler,
            ParameterBinder<?>[] parameterBinders,
            StatementOptions statementOptions,
            ExecutionPlan.TypeRouting typeRouting) {
        return new QueryDefinition<>(statementId, sql, sourceType, parameterBinders, null,
            Objects.requireNonNull(resultAssembler, "resultAssembler"), statementOptions, typeRouting);
    }

    /** Creates an assembled definition whose SQL and parameter routing are bound per invocation. */
    public static <T> QueryDefinition<T> assembled(
            String statementId,
            ExecutionPlan.SqlSource sourceType,
            ResultAssembler<T> resultAssembler,
            StatementOptions statementOptions,
            ExecutionPlan.TypeRouting resultRouting) {
        return new QueryDefinition<>(statementId, null, sourceType, null, null,
            Objects.requireNonNull(resultAssembler, "resultAssembler"), statementOptions, resultRouting);
    }

    /** Creates a definition using a custom row mapper that reads directly from the result set. */
    public static <T> QueryDefinition<T> rowMapped(
            String statementId,
            String sql,
            ExecutionPlan.SqlSource sourceType,
            RowMapper<T> rowMapper,
            ParameterBinder<?>[] parameterBinders,
            StatementOptions statementOptions,
            ExecutionPlan.TypeRouting typeRouting) {
        return new QueryDefinition<>(statementId, sql, sourceType, parameterBinders,
            Objects.requireNonNull(rowMapper, "rowMapper"), null, statementOptions, typeRouting);
    }

    /** Creates a custom-row-mapped definition whose SQL is bound per invocation. */
    public static <T> QueryDefinition<T> rowMapped(
            String statementId,
            ExecutionPlan.SqlSource sourceType,
            RowMapper<T> rowMapper,
            StatementOptions statementOptions,
            ExecutionPlan.TypeRouting resultRouting) {
        return new QueryDefinition<>(statementId, null, sourceType, null,
            Objects.requireNonNull(rowMapper, "rowMapper"), null, statementOptions, resultRouting);
    }

    private QueryDefinition(
            String statementId,
            String sql,
            ExecutionPlan.SqlSource sourceType,
            ParameterBinder<?>[] parameterBinders,
            RowMapper<T> rowMapper,
            ResultAssembler<T> resultAssembler,
            StatementOptions statementOptions,
            ExecutionPlan.TypeRouting typeRouting) {
        this.statementId = Objects.requireNonNull(statementId, "statementId");
        this.sql = sql;
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.parameterBinders = parameterBinders == null ? null : parameterBinders.clone();
        this.rowMapper = rowMapper;
        this.resultAssembler = resultAssembler;
        this.statementOptions = statementOptions == null ? StatementOptions.defaults() : statementOptions;
        this.typeRouting = typeRouting;
        this.planDefinition = new ExecutionPlan.Definition(
            statementId, ExecutionPlan.StatementType.SELECT, sourceType, null,
            this.parameterBinders, rowMapper, this.statementOptions, typeRouting);
    }

    /** Binds one invocation's ordered parameter values to this fixed definition. */
    public QueryExecutionPlan<T> bind(Object... parameters) {
        if (sql == null) {
            throw new IllegalStateException("dynamic query definition requires BoundSql");
        }
        return new QueryExecutionPlan<>(this, parameters);
    }

    /** Binds invocation-specific SQL and aligned parameters to this definition. */
    public QueryExecutionPlan<T> bind(BoundSql boundSql) {
        if (sql != null) {
            throw new IllegalStateException("fixed query definition accepts parameter values only");
        }
        return new QueryExecutionPlan<>(this, Objects.requireNonNull(boundSql, "boundSql"));
    }

    String statementId() {
        return statementId;
    }

    String sql() {
        return sql;
    }

    ExecutionPlan.SqlSource sourceType() {
        return sourceType;
    }

    ParameterBinder<?>[] parameterBinders() {
        return parameterBinders;
    }

    RowMapper<T> rowMapper() {
        return rowMapper;
    }

    ResultAssembler<T> resultAssembler() {
        return resultAssembler;
    }

    StatementOptions statementOptions() {
        return statementOptions;
    }

    ExecutionPlan.TypeRouting typeRouting() {
        return typeRouting;
    }

    ExecutionPlan.Definition planDefinition() {
        return planDefinition;
    }

    ExecutionPlan.TypeRouting routingFor(BoundSql boundSql) {
        return ExecutionPlan.TypeRouting.bindParameters(typeRouting, boundSql);
    }
}
