package org.liteorm.test;

import org.junit.jupiter.api.Test;
import org.liteorm.api.BatchExecutionPlan;
import org.liteorm.api.ExecutionPlan;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExecutionPlanContractTest {

    @Test
    void executionPlanDefensivelyCopiesOrderedParameters() {
        Object[] parameters = {1L, "Alice"};
        ExecutionPlan plan = new ExecutionPlan(
            "org.liteorm.test.UserMapper.findById",
            "SELECT * FROM users WHERE id = ? AND name = ?",
            parameters,
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.ANNOTATION
        );

        parameters[0] = 2L;
        Object[] returned = plan.getParameters();
        returned[1] = "Bob";

        assertArrayEquals(new Object[]{1L, "Alice"}, plan.getParameters());
        assertEquals(ExecutionPlan.StatementType.SELECT, plan.getStatementType());
        assertFalse(plan.returnsGeneratedKey());
    }

    @Test
    void batchExecutionPlanDefensivelyCopiesEveryParameterSet() {
        Object[] first = {1L};
        List<Object[]> parameters = new ArrayList<>();
        parameters.add(first);
        BatchExecutionPlan plan = new BatchExecutionPlan(
            "org.liteorm.test.UserMapper.insertBatch",
            "INSERT INTO users(id) VALUES (?)",
            parameters,
            ExecutionPlan.SqlSource.XML
        );

        first[0] = 2L;
        parameters.add(new Object[]{3L});
        List<Object[]> returned = plan.getBatchParameters();
        returned.get(0)[0] = 4L;

        assertEquals(1, plan.getBatchParameters().size());
        assertArrayEquals(new Object[]{1L}, plan.getBatchParameters().get(0));
        assertEquals(ExecutionPlan.StatementType.BATCH, plan.getStatementType());
    }
}
