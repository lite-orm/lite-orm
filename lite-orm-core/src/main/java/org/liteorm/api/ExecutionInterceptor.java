package org.liteorm.api;

public interface ExecutionInterceptor {

    /**
     * Called in registration order before transaction or connection acquisition.
     */
    default void beforeExecution(ExecutionPlan plan) {
    }

    /**
     * Called in reverse registration order after successful result extraction.
     */
    default void afterSuccess(ExecutionOutcome outcome) {
    }

    /**
     * Called in reverse registration order after failure. Callback failures must be suppressed
     * onto the earlier execution failure rather than replace it.
     */
    default void afterFailure(ExecutionOutcome outcome) {
    }
}
