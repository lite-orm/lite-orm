package org.liteorm.test.interceptor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.liteorm.api.ExecutionOutcome;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.JdbcExecutionState;
import org.liteorm.interceptor.AuditExecutionInterceptor;
import org.liteorm.interceptor.LoggingExecutionInterceptor;
import org.liteorm.interceptor.SlowQueryExecutionInterceptor;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExecutionInterceptorImplementationsTest {

    @Test
    void loggingReportsMetadataWithoutParameterValues() {
        ListAppender<ILoggingEvent> events = appender("logging");
        LoggingExecutionInterceptor interceptor = new LoggingExecutionInterceptor(logger("logging"));
        ExecutionPlan plan = plan();

        interceptor.beforeExecution(plan);
        interceptor.afterSuccess(ExecutionOutcome.success(
            plan, JdbcExecutionState.EXECUTED, 12_000_000L, 2, 0));
        interceptor.afterFailure(ExecutionOutcome.failure(
            plan, JdbcExecutionState.OUTCOME_UNKNOWN,
            15_000_000L, 0, 0, new IllegalStateException("failed")));

        assertEquals(List.of(Level.DEBUG, Level.DEBUG, Level.ERROR),
            events.list.stream().map(ILoggingEvent::getLevel).toList());
        assertFalse(events.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .anyMatch(message -> message.contains("customer-secret")));
    }

    @Test
    void slowQueryReportsOnlyOutcomesAtOrAboveThreshold() {
        ListAppender<ILoggingEvent> events = appender("slow");
        SlowQueryExecutionInterceptor interceptor = new SlowQueryExecutionInterceptor(
            logger("slow"), Duration.ofMillis(10));
        ExecutionPlan plan = plan();

        interceptor.afterSuccess(ExecutionOutcome.success(
            plan, JdbcExecutionState.EXECUTED, 9_999_999L, 0, 1));
        interceptor.afterSuccess(ExecutionOutcome.success(
            plan, JdbcExecutionState.EXECUTED, 10_000_000L, 0, 1));

        assertEquals(1, events.list.size());
        assertEquals(Level.WARN, events.list.getFirst().getLevel());
        assertFalse(events.list.getFirst().getFormattedMessage().contains("customer-secret"));
    }

    @Test
    void auditPublishesImmutableTerminalOutcomes() {
        List<ExecutionOutcome> events = new ArrayList<>();
        AuditExecutionInterceptor interceptor = new AuditExecutionInterceptor(events::add);
        ExecutionPlan plan = plan();
        ExecutionOutcome success = ExecutionOutcome.success(
            plan, JdbcExecutionState.EXECUTED, 5L, 0, 1);
        ExecutionOutcome failure = ExecutionOutcome.failure(
            plan, JdbcExecutionState.NOT_EXECUTED,
            7L, 0, 0, new IllegalStateException("failed"));

        interceptor.afterSuccess(success);
        interceptor.afterFailure(failure);
        Object[] returnedParameters = events.getFirst().plan().getParameters();
        returnedParameters[0] = "changed";

        assertEquals(2, events.size());
        assertSame(success, events.get(0));
        assertSame(failure, events.get(1));
        assertEquals("customer-secret", events.getFirst().plan().getParameters()[0]);
    }

    private Logger logger(String suffix) {
        return (Logger) LoggerFactory.getLogger(getClass().getName() + '.' + suffix);
    }

    private ListAppender<ILoggingEvent> appender(String suffix) {
        Logger logger = logger(suffix);
        logger.detachAndStopAllAppenders();
        logger.setAdditive(false);
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private ExecutionPlan plan() {
        return new ExecutionPlan(
            "test.Mapper.find",
            "SELECT name FROM users WHERE id = ?",
            new Object[]{"customer-secret"},
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.XML
        );
    }
}
