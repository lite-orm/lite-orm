package org.liteorm.spring.boot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.liteorm.ExecutionContext;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.SqlTask;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpringTransactionProcessorTest {

    private final SpringTransactionProcessor processor = new SpringTransactionProcessor();

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void marksExecutionContextWhenSpringTransactionIsActive() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        ExecutionContext context = new ExecutionContext();
        processor.process(plan(), context);

        assertEquals(Boolean.TRUE, context.getAttribute("spring.transaction.active"));
    }

    @Test
    void leavesExecutionContextUntouchedWhenNoSpringTransactionExists() {
        ExecutionContext context = new ExecutionContext();
        processor.process(plan(), context);

        assertNull(context.getAttribute("spring.transaction.active"));
    }

    private ExecutionPlan plan() {
        return new SqlTask(
            "org.liteorm.test.UserMapper.findById",
            "SELECT * FROM users WHERE id = ?",
            new Object[]{1L},
            SqlTask.SqlType.SELECT,
            false,
            "org.liteorm.test.User",
            ExecutionPlan.SqlSource.ANNOTATION
        );
    }
}
