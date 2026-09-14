package io.github.lynxus.api;

/**
 * Reports a physical JDBC lifecycle failure with statement, phase, source, and execution-state
 * context. Cleanup-only failures use {@link ExecutionPhase#CLEANUP}; when an earlier failure exists,
 * cleanup failures remain suppressed on its cause.
 */
public class SqlExecutionException extends LynxusException {

    private final String statementId;
    private final ExecutionPhase phase;
    private final Diagnostics diagnostics;

    public SqlExecutionException(
            ExecutionPlan plan,
            ExecutionPhase phase,
            JdbcExecutionState executionState,
            Throwable cause) {
        super(buildMessage(plan, phase, executionState), cause);
        this.statementId = plan.getStatementId();
        this.phase = phase;
        this.diagnostics = new Diagnostics(
            plan.getStatementId(), plan.getSourceType(), plan.getSql(), executionState);
    }

    public String getStatementId() {
        return statementId;
    }

    public ExecutionPhase getPhase() {
        return phase;
    }

    public ExecutionPlan.SqlSource getSourceType() {
        return diagnostics.sourceType();
    }

    public JdbcExecutionState getExecutionState() {
        return diagnostics.executionState();
    }

    public Diagnostics diagnostics() {
        return diagnostics;
    }

    private static String buildMessage(
            ExecutionPlan plan, ExecutionPhase phase, JdbcExecutionState executionState) {
        return "SQL execution failed [statementId=" + plan.getStatementId()
            + ", phase=" + phase
            + ", source=" + plan.getSourceType()
            + ", executionState=" + executionState + "]";
    }

    public record Diagnostics(
        String statementId,
        ExecutionPlan.SqlSource sourceType,
        String sql,
        JdbcExecutionState executionState) {
    }
}
