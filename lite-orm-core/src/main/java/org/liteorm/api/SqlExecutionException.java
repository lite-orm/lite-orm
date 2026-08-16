package org.liteorm.api;

/**
 * SQL执行异常
 * 
 * SQL执行过程中发生的异常
 * 
 * @author lite-orm
 * @since 2024/11/15
 */
public class SqlExecutionException extends LiteOrmException {

    private final String statementId;
    private final ExecutionPlan.SqlSource sourceType;
    private final String sql;
    private final JdbcExecutionState executionState;

    public SqlExecutionException(
            ExecutionPlan plan, JdbcExecutionState executionState, Throwable cause) {
        super(buildMessage(plan, executionState), cause);
        this.statementId = plan.getStatementId();
        this.sourceType = plan.getSourceType();
        this.sql = plan.getSql();
        this.executionState = executionState;
    }

    private static String buildMessage(ExecutionPlan plan, JdbcExecutionState executionState) {
        return "SQL execution failed [statementId=" + plan.getStatementId()
            + ", source=" + plan.getSourceType()
            + ", executionState=" + executionState + "]\nSQL: " + plan.getSql();
    }

    public String getStatementId() {
        return statementId;
    }

    public ExecutionPlan.SqlSource getSourceType() {
        return sourceType;
    }

    public String getSql() {
        return sql;
    }

    public JdbcExecutionState getExecutionState() {
        return executionState;
    }
}
