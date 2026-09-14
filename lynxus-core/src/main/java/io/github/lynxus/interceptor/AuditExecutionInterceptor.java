package io.github.lynxus.interceptor;

import io.github.lynxus.api.ExecutionInterceptor;
import io.github.lynxus.api.ExecutionOutcome;

import java.util.Objects;
import java.util.function.Consumer;

/** Publishes immutable terminal outcomes to an application-provided thread-safe audit sink. */
public final class AuditExecutionInterceptor implements ExecutionInterceptor {

    private final Consumer<ExecutionOutcome> auditSink;

    public AuditExecutionInterceptor(Consumer<ExecutionOutcome> auditSink) {
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
    }

    @Override
    public void afterSuccess(ExecutionOutcome outcome) {
        auditSink.accept(outcome);
    }

    @Override
    public void afterFailure(ExecutionOutcome outcome) {
        auditSink.accept(outcome);
    }
}
