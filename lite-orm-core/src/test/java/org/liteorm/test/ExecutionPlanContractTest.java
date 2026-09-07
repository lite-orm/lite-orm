package org.liteorm.test;

import org.junit.jupiter.api.Test;
import org.liteorm.api.BatchExecutionPlan;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.StatementOptions;
import org.liteorm.jdbc.JdbcTypeRouter;

import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void executionPlansNormalizeAndExposeImmutableStatementOptions() {
        ExecutionPlan defaultPlan = new ExecutionPlan(
            "org.liteorm.test.UserMapper.findAll",
            "SELECT * FROM users",
            new Object[0],
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.ANNOTATION,
            null,
            null,
            null,
            null
        );
        StatementOptions batchOptions = new StatementOptions(5, 200, 50);
        BatchExecutionPlan batchPlan = new BatchExecutionPlan(
            "org.liteorm.test.UserMapper.insertBatch",
            "INSERT INTO users(id) VALUES (?)",
            List.<Object[]>of(new Object[]{1L}),
            ExecutionPlan.SqlSource.XML,
            null,
            batchOptions
        );

        assertEquals(StatementOptions.defaults(), defaultPlan.getStatementOptions());
        assertEquals(batchOptions, batchPlan.getStatementOptions());
    }

    @Test
    void rejectsBlankGeneratedKeyColumns() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutionPlan(
            "org.liteorm.test.UserMapper.insert",
            "INSERT INTO users(name) VALUES (?)",
            new Object[]{"Alice"},
            ExecutionPlan.StatementType.INSERT,
            ExecutionPlan.SqlSource.ANNOTATION,
            " ",
            null,
            null));
    }

    @Test
    void typeRoutingDefensivelyCopiesGeneratedTypeInformation() {
        Class<?>[] parameterTypes = {String.class};
        JDBCType[] jdbcTypes = {JDBCType.VARCHAR};
        Class<?>[] resultTypes = {Long.class};
        String[] labels = {"id"};
        ExecutionPlan.TypeRouting routing = new ExecutionPlan.TypeRouting(
            new JdbcTypeRouter(List.of()), parameterTypes, jdbcTypes, resultTypes, labels);

        parameterTypes[0] = Object.class;
        jdbcTypes[0] = JDBCType.OTHER;
        resultTypes[0] = Object.class;
        labels[0] = "other";

        assertArrayEquals(new Class<?>[]{String.class}, routing.parameterTypes());
        assertArrayEquals(new JDBCType[]{JDBCType.VARCHAR}, routing.parameterJdbcTypes());
        assertArrayEquals(new Class<?>[]{Long.class}, routing.resultTypes());
        assertArrayEquals(new String[]{"id"}, routing.resultColumnLabels());
    }

    @Test
    void typeRoutingRequiresLabelsForCompositeResults() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutionPlan.TypeRouting(
            new JdbcTypeRouter(List.of()), new Class<?>[0], null,
            new Class<?>[]{Long.class, String.class}, null));
    }
}
