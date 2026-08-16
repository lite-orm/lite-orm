package org.liteorm.api;

public interface ExecutionInterceptor {

    /**
     * Called in registration order before transaction or connection acquisition.
     */
    default void beforeExecution(ExecutionPlan plan) {
    }

    /**
     * Called in reverse registration order after successful result extraction. Runtime failures
     * are isolated by the executor and do not change the SQL result.
     */
    default void afterSuccess(ExecutionOutcome outcome) {
    }

    /**
     * Called in reverse registration order after failure. Runtime failures are isolated by the
     * executor and do not modify the earlier execution failure.
     */
    default void afterFailure(ExecutionOutcome outcome) {
    }
}
