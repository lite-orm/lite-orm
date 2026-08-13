package org.liteorm.api;

import java.util.Arrays;
import java.util.Map;

public final class ExecutionInvocation {

    private final String statementId;
    private final String sql;
    private final Object[] parameters;
    private final ExecutionPlan.StatementType statementType;
    private final ExecutionPlan.SqlSource sourceType;
    private final long startNanos;
    private final Map<String, Object> routingMetadata;
    private long durationNanos;
    private int affectedRows;
    private int resultCount;
    private Throwable failure;

    public ExecutionInvocation(ExecutionPlan plan) {
        this.statementId = plan.getStatementId();
        this.sql = plan.getSql();
        this.parameters = plan.getParameters() == null ? new Object[0] : plan.getParameters().clone();
        this.statementType = plan.getStatementType();
        this.sourceType = plan.getSourceType();
        this.startNanos = System.nanoTime();
        this.routingMetadata = Map.of();
    }

    public String statementId() {
        return statementId;
    }

    public String sql() {
        return sql;
    }

    public Object[] parameters() {
        return parameters.clone();
    }

    public ExecutionPlan.StatementType statementType() {
        return statementType;
    }

    public ExecutionPlan.SqlSource sourceType() {
        return sourceType;
    }

    public long startNanos() {
        return startNanos;
    }

    public long durationNanos() {
        return durationNanos;
    }

    public int affectedRows() {
        return affectedRows;
    }

    public int resultCount() {
        return resultCount;
    }

    public Throwable failure() {
        return failure;
    }

    public Map<String, Object> routingMetadata() {
        return routingMetadata;
    }

    public void complete(int affectedRows, int resultCount, Throwable failure) {
        this.durationNanos = Math.max(0L, System.nanoTime() - startNanos);
        this.affectedRows = affectedRows;
        this.resultCount = resultCount;
        this.failure = failure;
    }

    @Override
    public String toString() {
        return "ExecutionInvocation{" + statementId + ", parameters=" + Arrays.toString(parameters) + '}';
    }
}
