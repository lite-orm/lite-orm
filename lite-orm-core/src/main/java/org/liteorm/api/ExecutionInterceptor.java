package org.liteorm.api;

public interface ExecutionInterceptor {

    default void beforeExecution(ExecutionInvocation invocation) {
    }

    default void afterSuccess(ExecutionInvocation invocation) {
    }

    default void afterFailure(ExecutionInvocation invocation) {
    }
}
