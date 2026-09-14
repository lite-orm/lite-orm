package io.github.kervix.interceptor;

import io.github.kervix.api.ExecutionInterceptor;
import io.github.kervix.api.ExecutionOutcome;
import io.github.kervix.api.ExecutionPlan;
import org.slf4j.Logger;

import java.util.Objects;

/** Logs execution metadata without logging parameter values. */
public final class LoggingExecutionInterceptor implements ExecutionInterceptor {

    private final Logger logger;

    public LoggingExecutionInterceptor(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void beforeExecution(ExecutionPlan plan) {
        logger.debug(
            "Kervix executing statementId={} type={} source={}",
            plan.getStatementId(), plan.getStatementType(), plan.getSourceType()
        );
    }

    @Override
    public void afterSuccess(ExecutionOutcome outcome) {
        logger.debug(
            "Kervix executed statementId={} durationNanos={} affectedRows={} resultCount={}",
            outcome.plan().getStatementId(), outcome.durationNanos(),
            outcome.affectedRows(), outcome.resultCount()
        );
    }

    @Override
    public void afterFailure(ExecutionOutcome outcome) {
        logger.error(
            "Kervix execution failed statementId={} durationNanos={} failureType={}",
            outcome.plan().getStatementId(), outcome.durationNanos(),
            outcome.failure().getClass().getName(), outcome.failure()
        );
    }
}
