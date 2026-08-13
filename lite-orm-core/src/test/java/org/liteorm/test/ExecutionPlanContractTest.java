package org.liteorm.test;

import org.junit.jupiter.api.Test;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.SqlTask;


import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionPlanContractTest {

    @Test
    void sqlTaskImplementsExecutionPlanMetadata() {
        SqlTask task = new SqlTask(
            "org.liteorm.test.UserMapper.findById",
            "SELECT * FROM users WHERE id = ?",
            new Object[]{1L},
            SqlTask.SqlType.SELECT,
            false,
            "org.liteorm.test.User",
            ExecutionPlan.SqlSource.ANNOTATION
        );

        assertEquals("org.liteorm.test.UserMapper.findById", task.getStatementId());
        assertEquals(ExecutionPlan.StatementType.SELECT, task.getStatementType());
        assertEquals(ExecutionPlan.SqlSource.ANNOTATION, task.getSourceType());
        assertEquals("org.liteorm.test.User", task.getResultType());
    }

}
