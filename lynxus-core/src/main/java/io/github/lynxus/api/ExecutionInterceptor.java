package io.github.lynxus.api;

public interface ExecutionInterceptor {

    /**
     * Called in registration order before transaction or connection acquisition.
     */
    default void beforeExecution(ExecutionPlan plan) {
    }

    /**
     * Called in reverse registration order after successful result extraction and executor-owned
     * cleanup. Runtime failures are isolated by the executor and do not change the SQL result.
     */
    default void afterSuccess(ExecutionOutcome outcome) {
    }

    /**
     * Called in reverse registration order with the final failure after executor-owned cleanup.
     * For ordinary runtime failures, {@link ExecutionOutcome#failure()} is the same throwable
     * delivered to the Mapper caller. Runtime failures from this callback are isolated by the
     * executor and do not modify the final failure.
     */
    default void afterFailure(ExecutionOutcome outcome) {
    }
}
