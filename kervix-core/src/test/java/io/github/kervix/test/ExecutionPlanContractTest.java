package io.github.kervix.test;

import org.junit.jupiter.api.Test;
import io.github.kervix.api.BatchExecutionPlan;
import io.github.kervix.api.BatchDefinition;
import io.github.kervix.api.CommandDefinition;
import io.github.kervix.api.ExecutionPlan;
import io.github.kervix.api.QueryDefinition;
import io.github.kervix.api.QueryExecutionPlan;
import io.github.kervix.api.StatementOptions;

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
            "io.github.kervix.test.UserMapper.findById",
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
            "io.github.kervix.test.UserMapper.insertBatch",
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
            "io.github.kervix.test.UserMapper.findAll",
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
            "io.github.kervix.test.UserMapper.insertBatch",
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
            "io.github.kervix.test.UserMapper.insert",
            "INSERT INTO users(name) VALUES (?)",
            new Object[]{"Alice"},
            ExecutionPlan.StatementType.INSERT,
            ExecutionPlan.SqlSource.ANNOTATION,
            " ",
            null,
            null));
    }

    @Test
    void typedQueryPlansRequireExactlyOneMappingStrategy() {
        assertThrows(IllegalArgumentException.class, () -> new QueryExecutionPlan<>(
            "io.github.kervix.test.UserMapper.find", "SELECT id FROM users", new Object[0],
            ExecutionPlan.StatementType.SELECT, ExecutionPlan.SqlSource.ANNOTATION,
            null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new QueryExecutionPlan<>(
            "io.github.kervix.test.UserMapper.find", "SELECT id FROM users", new Object[0],
            ExecutionPlan.StatementType.SELECT, ExecutionPlan.SqlSource.ANNOTATION,
            null, null, resultSet -> 1L, row -> 1L, null, null));
    }

    @Test
    void queryDefinitionBindsInvocationValuesWithoutRetainingThem() {
        ExecutionPlan.TypeRouting routing = new ExecutionPlan.TypeRouting(
            new Class<?>[]{Long.class}, new JDBCType[]{JDBCType.BIGINT},
            new Class<?>[]{Long.class}, null);
        QueryDefinition<Long> definition = QueryDefinition.assembled(
            "io.github.kervix.test.UserMapper.findById",
            "SELECT id FROM users WHERE id = ?",
            ExecutionPlan.SqlSource.ANNOTATION,
            row -> (Long) row.get(0),
            null,
            StatementOptions.defaults(),
            routing);

        Object[] parameters = {1L};
        QueryExecutionPlan<Long> first = definition.bind(parameters);
        parameters[0] = 2L;
        QueryExecutionPlan<Long> second = definition.bind(3L);

        assertArrayEquals(new Object[]{1L}, first.getParameters());
        assertArrayEquals(new Object[]{3L}, second.getParameters());
        assertEquals("SELECT id FROM users WHERE id = ?", first.getSql());
        assertEquals(routing, first.getTypeRouting());
        assertThrows(IllegalStateException.class, () -> definition.bind(
            new io.github.kervix.api.BoundSql("SELECT 2", List.of())));
        assertEquals(1L, first.getResultAssembler().assemble(
            new io.github.kervix.api.ResultRow(new Object[]{1L}, new int[]{0})));
    }

    @Test
    void commandAndBatchDefinitionsBindOnlyInvocationValues() {
        ExecutionPlan.TypeRouting routing = new ExecutionPlan.TypeRouting(
            new Class<?>[]{Long.class}, new JDBCType[]{JDBCType.BIGINT},
            new Class<?>[0], null);
        CommandDefinition command = CommandDefinition.command(
            "io.github.kervix.test.UserMapper.deleteById",
            "DELETE FROM users WHERE id = ?",
            ExecutionPlan.StatementType.DELETE,
            ExecutionPlan.SqlSource.ANNOTATION,
            null,
            StatementOptions.defaults(),
            routing);
        BatchDefinition batch = new BatchDefinition(
            "io.github.kervix.test.UserMapper.deleteBatch",
            "DELETE FROM users WHERE id = ?",
            ExecutionPlan.SqlSource.ANNOTATION,
            null,
            StatementOptions.defaults(),
            routing);

        ExecutionPlan commandPlan = command.bind(1L);
        BatchExecutionPlan batchPlan = batch.bind(List.<Object[]>of(new Object[]{2L}));

        assertArrayEquals(new Object[]{1L}, commandPlan.getParameters());
        assertEquals(ExecutionPlan.StatementType.DELETE, commandPlan.getStatementType());
        assertThrows(IllegalStateException.class, () -> command.bind(
            new io.github.kervix.api.BoundSql("DELETE FROM users", List.of())));
        assertArrayEquals(new Object[]{2L}, batchPlan.getBatchParameters().getFirst());
    }

    @Test
    void typeRoutingDefensivelyCopiesGeneratedTypeInformation() {
        Class<?>[] parameterTypes = {String.class};
        JDBCType[] jdbcTypes = {JDBCType.VARCHAR};
        Class<?>[] resultTypes = {Long.class};
        String[] labels = {"id"};
        ExecutionPlan.TypeRouting routing = new ExecutionPlan.TypeRouting(
            parameterTypes, jdbcTypes, resultTypes, labels);

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
            new Class<?>[0], null, new Class<?>[]{Long.class, String.class}, null));
    }
}
