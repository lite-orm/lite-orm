package io.github.lynxus.interceptor;

import io.github.lynxus.api.ExecutionInterceptor;
import io.github.lynxus.api.ExecutionOutcome;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Objects;

/** Logs terminal execution metadata when the configured duration threshold is reached. */
public final class SlowQueryExecutionInterceptor implements ExecutionInterceptor {

    private final Logger logger;
    private final long thresholdNanos;

    public SlowQueryExecutionInterceptor(Logger logger, Duration threshold) {
        this.logger = Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(threshold, "threshold");
        if (threshold.isNegative()) {
            throw new IllegalArgumentException("threshold must not be negative");
        }
        this.thresholdNanos = threshold.toNanos();
    }

    @Override
    public void afterSuccess(ExecutionOutcome outcome) {
        reportIfSlow(outcome);
    }

    @Override
    public void afterFailure(ExecutionOutcome outcome) {
        reportIfSlow(outcome);
    }

    private void reportIfSlow(ExecutionOutcome outcome) {
        if (outcome.durationNanos() >= thresholdNanos) {
            logger.warn(
                "Lynxus slow execution statementId={} durationNanos={} failed={}",
                outcome.plan().getStatementId(), outcome.durationNanos(), outcome.failed()
            );
        }
    }
}
