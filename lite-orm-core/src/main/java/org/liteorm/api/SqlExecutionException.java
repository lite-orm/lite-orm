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

    public SqlExecutionException(ExecutionPlan plan, Throwable cause) {
        super(buildMessage(plan), cause);
        this.statementId = plan.getStatementId();
        this.sourceType = plan.getSourceType();
        this.sql = plan.getSql();
    }

    private static String buildMessage(ExecutionPlan plan) {
        return "SQL execution failed [statementId=" + plan.getStatementId()
            + ", source=" + plan.getSourceType() + "]\nSQL: " + plan.getSql();
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
}
