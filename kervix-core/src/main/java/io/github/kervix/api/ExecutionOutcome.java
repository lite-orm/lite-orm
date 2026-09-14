package io.github.kervix.api;

import java.util.Objects;

/**
 * Immutable terminal state observed after one SQL execution attempt and its executor-owned cleanup.
 * The duration includes cleanup but excludes terminal interceptor callbacks. This outcome does not
 * describe transaction commit or rollback.
 */
public final class ExecutionOutcome {

    private final ExecutionPlan plan;
    private final JdbcExecutionState executionState;
    private final long durationNanos;
    private final int affectedRows;
    private final int resultCount;
    private final Throwable failure;

    private ExecutionOutcome(
            ExecutionPlan plan,
            JdbcExecutionState executionState,
            long durationNanos,
            int affectedRows,
            int resultCount,
            Throwable failure) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.executionState = Objects.requireNonNull(executionState, "executionState");
        if (durationNanos < 0) {
            throw new IllegalArgumentException("durationNanos must not be negative");
        }
        this.durationNanos = durationNanos;
        this.affectedRows = affectedRows;
        this.resultCount = resultCount;
        this.failure = failure;
    }

    public static ExecutionOutcome success(
            ExecutionPlan plan,
            JdbcExecutionState executionState,
            long durationNanos,
            int affectedRows,
            int resultCount) {
        return new ExecutionOutcome(plan, executionState, durationNanos, affectedRows, resultCount, null);
    }

    public static ExecutionOutcome failure(
            ExecutionPlan plan,
            JdbcExecutionState executionState,
            long durationNanos,
            int affectedRows,
            int resultCount,
            Throwable failure) {
        return new ExecutionOutcome(
            plan,
            executionState,
            durationNanos,
            affectedRows,
            resultCount,
            Objects.requireNonNull(failure, "failure")
        );
    }

    public ExecutionPlan plan() {
        return plan;
    }

    public JdbcExecutionState executionState() {
        return executionState;
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

    public boolean failed() {
        return failure != null;
    }
}
