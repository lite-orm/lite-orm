package org.liteorm.api;

import java.util.Objects;

/**
 * Immutable input for one SQL execution.
 */
public class ExecutionPlan {

    private final String statementId;
    private final String sql;
    private final Object[] parameters;
    private final StatementType statementType;
    private final SqlSource sourceType;
    private final boolean returnsGeneratedKey;
    private final ParameterBinder<?>[] parameterBinders;
    private final RowMapper<?> rowMapper;

    public ExecutionPlan(
            String statementId,
            String sql,
            Object[] parameters,
            StatementType statementType,
            SqlSource sourceType) {
        this(statementId, sql, parameters, statementType, sourceType, false, null, null);
    }

    public ExecutionPlan(
            String statementId,
            String sql,
            Object[] parameters,
            StatementType statementType,
            SqlSource sourceType,
            boolean returnsGeneratedKey,
            ParameterBinder<?>[] parameterBinders,
            RowMapper<?> rowMapper) {
        this.statementId = Objects.requireNonNull(statementId, "statementId");
        this.sql = Objects.requireNonNull(sql, "sql");
        this.parameters = parameters == null ? new Object[0] : parameters.clone();
        this.statementType = Objects.requireNonNull(statementType, "statementType");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.returnsGeneratedKey = returnsGeneratedKey;
        this.parameterBinders = parameterBinders == null ? null : parameterBinders.clone();
        this.rowMapper = rowMapper;
    }

    public String getStatementId() {
        return statementId;
    }

    public String getSql() {
        return sql;
    }

    public Object[] getParameters() {
        return parameters.clone();
    }

    public StatementType getStatementType() {
        return statementType;
    }

    public SqlSource getSourceType() {
        return sourceType;
    }

    public boolean returnsGeneratedKey() {
        return returnsGeneratedKey;
    }

    public ParameterBinder<?>[] getParameterBinders() {
        return parameterBinders == null ? null : parameterBinders.clone();
    }

    public RowMapper<?> getRowMapper() {
        return rowMapper;
    }

    public enum StatementType {
        SELECT,
        INSERT,
        UPDATE,
        DELETE,
        BATCH
    }

    public enum SqlSource {
        XML,
        ANNOTATION,
        SCRIPT,
        GENERATED
    }
}
