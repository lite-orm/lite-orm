package org.liteorm.api;

import java.util.Objects;

/**
 * Immutable terminal state observed after one SQL execution attempt.
 */
public final class ExecutionOutcome {

    private final ExecutionPlan plan;
    private final long durationNanos;
    private final int affectedRows;
    private final int resultCount;
    private final Throwable failure;

    private ExecutionOutcome(
            ExecutionPlan plan,
            long durationNanos,
            int affectedRows,
            int resultCount,
            Throwable failure) {
        this.plan = Objects.requireNonNull(plan, "plan");
        if (durationNanos < 0) {
            throw new IllegalArgumentException("durationNanos must not be negative");
        }
        this.durationNanos = durationNanos;
        this.affectedRows = affectedRows;
        this.resultCount = resultCount;
        this.failure = failure;
    }

    public static ExecutionOutcome success(
            ExecutionPlan plan, long durationNanos, int affectedRows, int resultCount) {
        return new ExecutionOutcome(plan, durationNanos, affectedRows, resultCount, null);
    }

    public static ExecutionOutcome failure(
            ExecutionPlan plan,
            long durationNanos,
            int affectedRows,
            int resultCount,
            Throwable failure) {
        return new ExecutionOutcome(
            plan,
            durationNanos,
            affectedRows,
            resultCount,
            Objects.requireNonNull(failure, "failure")
        );
    }

    public ExecutionPlan plan() {
        return plan;
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
