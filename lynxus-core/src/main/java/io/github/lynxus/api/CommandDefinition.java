package io.github.lynxus.api;

import java.util.Objects;

/**
 * Immutable compile-time definition of one insert, update, or delete statement.
 *
 * <p>Fixed definitions retain SQL and routing metadata and accept only invocation parameter values.
 * Dynamic definitions retain stable statement metadata and accept one {@link BoundSql} per
 * invocation. Definitions are thread-safe when their configured extension is thread-safe.</p>
 */
public final class CommandDefinition {

    private final String statementId;
    private final String sql;
    private final ExecutionPlan.StatementType statementType;
    private final ExecutionPlan.SqlSource sourceType;
    private final String generatedKeyColumn;
    private final ParameterBinder<?>[] parameterBinders;
    private final RowMapper<?> rowMapper;
    private final StatementOptions statementOptions;
    private final ExecutionPlan.TypeRouting typeRouting;
    private final ExecutionPlan.Definition planDefinition;

    /** Creates a fixed-SQL insert, update, or delete definition. */
    public static CommandDefinition command(
            String statementId, String sql, ExecutionPlan.StatementType statementType,
            ExecutionPlan.SqlSource sourceType, ParameterBinder<?>[] parameterBinders,
            StatementOptions statementOptions, ExecutionPlan.TypeRouting typeRouting) {
        return new CommandDefinition(statementId, sql, statementType, sourceType, null,
            parameterBinders, null, statementOptions, typeRouting);
    }

    /** Creates an insert, update, or delete definition whose SQL is bound per invocation. */
    public static CommandDefinition command(
            String statementId, ExecutionPlan.StatementType statementType,
            ExecutionPlan.SqlSource sourceType, StatementOptions statementOptions,
            ExecutionPlan.TypeRouting resultRouting) {
        return new CommandDefinition(statementId, null, statementType, sourceType, null,
            null, null, statementOptions, resultRouting);
    }

    /** Creates a fixed-SQL insert definition that returns a generated key through Core routing. */
    public static CommandDefinition generatedKey(
            String statementId, String sql, ExecutionPlan.SqlSource sourceType,
            String generatedKeyColumn, ParameterBinder<?>[] parameterBinders,
            StatementOptions statementOptions, ExecutionPlan.TypeRouting typeRouting) {
        return new CommandDefinition(statementId, sql, ExecutionPlan.StatementType.INSERT,
            sourceType, generatedKeyColumn, parameterBinders, null, statementOptions, typeRouting);
    }

    /** Creates a dynamic-SQL insert definition that returns a generated key through Core routing. */
    public static CommandDefinition generatedKey(
            String statementId, ExecutionPlan.SqlSource sourceType, String generatedKeyColumn,
            StatementOptions statementOptions, ExecutionPlan.TypeRouting resultRouting) {
        return new CommandDefinition(statementId, null, ExecutionPlan.StatementType.INSERT,
            sourceType, generatedKeyColumn, null, null, statementOptions, resultRouting);
    }

    /** Creates a fixed-SQL insert definition with a custom generated-key row mapper. */
    public static CommandDefinition rowMappedGeneratedKey(
            String statementId, String sql, ExecutionPlan.SqlSource sourceType,
            String generatedKeyColumn, RowMapper<?> rowMapper, ParameterBinder<?>[] parameterBinders,
            StatementOptions statementOptions, ExecutionPlan.TypeRouting typeRouting) {
        return new CommandDefinition(statementId, sql, ExecutionPlan.StatementType.INSERT,
            sourceType, generatedKeyColumn, parameterBinders,
            Objects.requireNonNull(rowMapper, "rowMapper"), statementOptions, typeRouting);
    }

    /** Creates a dynamic-SQL insert definition with a custom generated-key row mapper. */
    public static CommandDefinition rowMappedGeneratedKey(
            String statementId, ExecutionPlan.SqlSource sourceType, String generatedKeyColumn,
            RowMapper<?> rowMapper, StatementOptions statementOptions,
            ExecutionPlan.TypeRouting resultRouting) {
        return new CommandDefinition(statementId, null, ExecutionPlan.StatementType.INSERT,
            sourceType, generatedKeyColumn, null, Objects.requireNonNull(rowMapper, "rowMapper"),
            statementOptions, resultRouting);
    }

    private CommandDefinition(
            String statementId, String sql, ExecutionPlan.StatementType statementType,
            ExecutionPlan.SqlSource sourceType, String generatedKeyColumn,
            ParameterBinder<?>[] parameterBinders, RowMapper<?> rowMapper,
            StatementOptions statementOptions, ExecutionPlan.TypeRouting typeRouting) {
        if (statementType == ExecutionPlan.StatementType.SELECT
                || statementType == ExecutionPlan.StatementType.BATCH) {
            throw new IllegalArgumentException("command definition requires insert, update, or delete");
        }
        this.statementId = Objects.requireNonNull(statementId, "statementId");
        this.sql = sql;
        this.statementType = Objects.requireNonNull(statementType, "statementType");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.generatedKeyColumn = generatedKeyColumn;
        this.parameterBinders = parameterBinders == null ? null : parameterBinders.clone();
        this.rowMapper = rowMapper;
        this.statementOptions = statementOptions == null ? StatementOptions.defaults() : statementOptions;
        this.typeRouting = typeRouting;
        this.planDefinition = new ExecutionPlan.Definition(
            statementId, statementType, sourceType, generatedKeyColumn,
            this.parameterBinders, rowMapper, this.statementOptions, typeRouting);
    }

    /** Binds ordered invocation values to a fixed-SQL definition. */
    public ExecutionPlan bind(Object... parameters) {
        if (sql == null) {
            throw new IllegalStateException("dynamic command definition requires BoundSql");
        }
        return plan(sql, parameters, parameterBinders, typeRouting);
    }

    /** Binds invocation-specific SQL and aligned parameter metadata to a dynamic definition. */
    public ExecutionPlan bind(BoundSql boundSql) {
        if (sql != null) {
            throw new IllegalStateException("fixed command definition accepts parameter values only");
        }
        Objects.requireNonNull(boundSql, "boundSql");
        ExecutionPlan.TypeRouting routing =
            ExecutionPlan.TypeRouting.bindParameters(typeRouting, boundSql);
        return plan(boundSql.sql(), boundSql.parameterValues(), boundSql.parameterBinders(), routing);
    }

    private ExecutionPlan plan(
            String boundSql, Object[] parameters, ParameterBinder<?>[] binders,
            ExecutionPlan.TypeRouting routing) {
        ExecutionPlan.Definition boundDefinition = sql == null
            ? new ExecutionPlan.Definition(statementId, statementType, sourceType,
                generatedKeyColumn, binders, rowMapper, statementOptions, routing)
            : planDefinition;
        return new ExecutionPlan(boundDefinition, boundSql, parameters);
    }
}
