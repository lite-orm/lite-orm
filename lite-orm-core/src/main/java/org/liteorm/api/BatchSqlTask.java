package org.liteorm.api;

import java.util.List;
import java.util.Map;

/**
 * Immutable JDBC batch task. Each list entry represents one statement execution.
 */
public final class BatchSqlTask implements BatchExecutionPlan {

    private final String statementId;
    private final String sql;
    private final List<Object[]> batchParameters;
    private final SqlSource sourceType;
    private final ParameterBinder<?>[] parameterBinders;

    public BatchSqlTask(String statementId, String sql, List<Object[]> batchParameters, SqlSource sourceType) {
        this(statementId, sql, batchParameters, sourceType, null);
    }

    public BatchSqlTask(
            String statementId,
            String sql,
            List<Object[]> batchParameters,
            SqlSource sourceType,
            ParameterBinder<?>[] parameterBinders) {
        this.statementId = statementId;
        this.sql = sql;
        this.batchParameters = batchParameters.stream().map(Object[]::clone).toList();
        this.sourceType = sourceType;
        this.parameterBinders = parameterBinders == null ? null : parameterBinders.clone();
    }

    @Override
    public String getStatementId() {
        return statementId;
    }

    @Override
    public String getSql() {
        return sql;
    }

    @Override
    public Object[] getParameters() {
        return new Object[0];
    }

    @Override
    public Map<String, Object> getParameterMap() {
        return Map.of();
    }

    @Override
    public boolean usesParameterMap() {
        return false;
    }

    @Override
    public StatementType getStatementType() {
        return StatementType.BATCH;
    }

    @Override
    public boolean requiresTransaction() {
        return true;
    }

    @Override
    public String getResultType() {
        return "int[]";
    }

    @Override
    public SqlSource getSourceType() {
        return sourceType;
    }

    @Override
    public ParameterBinder<?>[] getParameterBinders() {
        return parameterBinders == null ? null : parameterBinders.clone();
    }

    @Override
    public List<Object[]> getBatchParameters() {
        return batchParameters.stream().map(Object[]::clone).toList();
    }
}
