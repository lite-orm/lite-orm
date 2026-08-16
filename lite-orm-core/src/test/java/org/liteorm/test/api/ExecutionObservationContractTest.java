package org.liteorm.test.api;

import org.junit.jupiter.api.Test;
import org.liteorm.api.ExecutionInterceptor;
import org.liteorm.api.ExecutionOutcome;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.JdbcExecutionState;
import org.liteorm.api.SqlExecutionException;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionObservationContractTest {

    @Test
    void interceptorReceivesTheExistingImmutablePlanBeforeExecution() throws Exception {
        Object[] parameters = {7L, "secret"};
        ExecutionPlan plan = plan(parameters);
        parameters[0] = 9L;
        Object[] returnedParameters = plan.getParameters();
        returnedParameters[1] = "changed";

        assertArrayEquals(new Object[]{7L, "secret"}, plan.getParameters());
        assertEquals(
            ExecutionPlan.class,
            ExecutionInterceptor.class.getMethod("beforeExecution", ExecutionPlan.class).getParameterTypes()[0]
        );
    }

    @Test
    void outcomeSeparatesTerminalStateFromInvocation() {
        ExecutionPlan plan = plan(new Object[0]);
        IllegalStateException failure = new IllegalStateException("failed");

        ExecutionOutcome success = ExecutionOutcome.success(
            plan, JdbcExecutionState.EXECUTED, 12L, 3, 0);
        ExecutionOutcome failed = ExecutionOutcome.failure(
            plan, JdbcExecutionState.OUTCOME_UNKNOWN, 15L, 0, 0, failure);

        assertSame(plan, success.plan());
        assertEquals(3, success.affectedRows());
        assertEquals(0, success.resultCount());
        assertEquals(12L, success.durationNanos());
        assertEquals(JdbcExecutionState.EXECUTED, success.executionState());
        assertFalse(success.failed());
        assertSame(failure, failed.failure());
        assertEquals(JdbcExecutionState.OUTCOME_UNKNOWN, failed.executionState());
        assertTrue(failed.failed());
        assertTrue(Arrays.stream(ExecutionOutcome.class.getDeclaredFields())
            .allMatch(field -> Modifier.isFinal(field.getModifiers())));
    }

    @Test
    void interceptorUsesOutcomeAfterExecution() throws Exception {
        assertEquals(
            ExecutionOutcome.class,
            ExecutionInterceptor.class.getMethod("afterSuccess", ExecutionOutcome.class).getParameterTypes()[0]
        );
        assertEquals(
            ExecutionOutcome.class,
            ExecutionInterceptor.class.getMethod("afterFailure", ExecutionOutcome.class).getParameterTypes()[0]
        );
    }

    @Test
    void sqlFailureMetadataDoesNotExposeParameterValues() {
        ExecutionPlan plan = plan(new Object[]{"sensitive-value"});

        SqlExecutionException failure = new SqlExecutionException(
            plan, JdbcExecutionState.NOT_EXECUTED, new IllegalStateException("failed"));

        assertEquals("test.Mapper.find", failure.getStatementId());
        assertEquals(ExecutionPlan.SqlSource.XML, failure.getSourceType());
        assertEquals("SELECT name FROM users WHERE id = ?", failure.getSql());
        assertEquals(JdbcExecutionState.NOT_EXECUTED, failure.getExecutionState());
        assertFalse(failure.getMessage().contains("sensitive-value"));
    }

    private ExecutionPlan plan(Object[] parameters) {
        return new ExecutionPlan(
            "test.Mapper.find",
            "SELECT name FROM users WHERE id = ?",
            parameters,
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.XML
        );
    }
}
