package io.github.kervix.test.api;

import org.junit.jupiter.api.Test;
import io.github.kervix.api.BatchExecutionPlan;
import io.github.kervix.api.BatchResult;
import io.github.kervix.api.ExecutionPlan;
import io.github.kervix.api.GeneratedKeyResult;
import io.github.kervix.api.MappingException;
import io.github.kervix.api.NonUniqueResultException;
import io.github.kervix.api.QueryResult;
import io.github.kervix.api.SqlExecutor;
import io.github.kervix.api.SqlResult;
import io.github.kervix.api.UpdateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedExecutionResultTest {

    @Test
    void queryResultOwnsNonNullRowsAndCardinalitySemantics() {
        QueryResult<String> empty = QueryResult.of("test.Mapper.find", List.of());
        QueryResult<String> single = QueryResult.of(
            "test.Mapper.find", List.of("Alice"));
        QueryResult<String> multiple = QueryResult.of(
            "test.Mapper.find", List.of("Alice", "Bob"));

        assertTrue(empty.rows().isEmpty());
        assertNull(empty.oneOrNull());
        assertEquals(Optional.empty(), empty.optional());
        assertEquals("Alice", single.oneOrNull());
        assertEquals(Optional.of("Alice"), single.optional());
        assertThrows(MappingException.class, empty::required);
        assertThrows(NonUniqueResultException.class, multiple::oneOrNull);
        assertThrows(NonUniqueResultException.class, multiple::optional);
        assertThrows(NonUniqueResultException.class, multiple::required);
    }

    @Test
    void queryResultCopiesRowsAndKeepsReturnedListImmutable() {
        List<String> sourceRows = new ArrayList<>(List.of("Alice"));
        QueryResult<String> result = QueryResult.of("test.Mapper.find", sourceRows);

        sourceRows.add("Bob");

        assertEquals(List.of("Alice"), result.rows());
        assertThrows(UnsupportedOperationException.class, () -> result.rows().add("Carol"));
    }

    @Test
    void updateGeneratedKeyAndBatchResultsExposeOnlyTheirOwnState() {
        UpdateResult update = UpdateResult.of("test.Mapper.update", 3);
        GeneratedKeyResult<Long> key = GeneratedKeyResult.of("test.Mapper.insert", 1, 42L);
        BatchResult batch = BatchResult.of("test.Mapper.batch", new int[]{1, 0});

        assertEquals(3, update.count());
        assertEquals(1, key.affectedRows());
        assertEquals(42L, key.key());
        assertArrayEquals(new int[]{1, 0}, batch.counts());
        assertFalse(update.equals(key));
    }

    @Test
    void batchCountsAreDefensivelyCopied() {
        int[] counts = {1, 2};
        BatchResult result = BatchResult.of("test.Mapper.batch", counts);

        counts[0] = 9;
        result.counts()[1] = 8;

        assertArrayEquals(new int[]{1, 2}, result.counts());
    }

    @Test
    void typedExecutorAdaptersPreserveLegacySqlExecutorImplementations() {
        SqlExecutor executor = plan -> switch (plan.getStatementType()) {
            case SELECT -> SqlResult.forQuery(List.<Object[]>of(new Object[]{"Alice"}));
            case BATCH -> SqlResult.forBatch(new int[]{1});
            case INSERT -> SqlResult.forGeneratedKey(1, 42L);
            case UPDATE, DELETE -> SqlResult.forUpdate(3);
        };

        assertEquals(List.of("Alice"), executor.query(queryPlan()).rows());
        assertEquals(3, executor.update(writePlan(ExecutionPlan.StatementType.UPDATE)).count());
        assertEquals(42L, executor.generatedKey(
            writePlan(ExecutionPlan.StatementType.INSERT)).key());
        assertArrayEquals(new int[]{1}, executor.batch(batchPlan()).counts());
    }

    @Test
    void typedExecutorAdaptersDoNotInferGeneratedKeyFromNullablePayload() {
        SqlExecutor executor = plan -> plan.getStatementType() == ExecutionPlan.StatementType.INSERT
            ? SqlResult.forGeneratedKey(1, null)
            : SqlResult.forUpdate(1);

        assertNull(executor.generatedKey(
            writePlan(ExecutionPlan.StatementType.INSERT)).key());
        assertThrows(IllegalArgumentException.class, () -> executor.update(
            writePlan(ExecutionPlan.StatementType.INSERT)));
        assertThrows(IllegalArgumentException.class, () -> executor.generatedKey(
            writePlan(ExecutionPlan.StatementType.UPDATE)));
    }

    @Test
    void requiredUsesStatementContextForMissingRows() {
        MappingException failure = assertThrows(
            MappingException.class,
            () -> QueryResult.of("test.Mapper.findRequired", List.<String>of()).required());

        assertTrue(failure.getMessage().contains("test.Mapper.findRequired"));
    }

    private io.github.kervix.api.QueryExecutionPlan<String> queryPlan() {
        return new io.github.kervix.api.QueryExecutionPlan<>(
            "test.Mapper.find", "SELECT value FROM values_table", new Object[0],
            ExecutionPlan.StatementType.SELECT, ExecutionPlan.SqlSource.GENERATED,
            null, null, null, row -> (String) row.get(0), null,
            new ExecutionPlan.TypeRouting(
                new Class<?>[0], null, new Class<?>[]{String.class}, null));
    }

    private ExecutionPlan writePlan(ExecutionPlan.StatementType type) {
        return new ExecutionPlan(
            "test.Mapper.write", "UPDATE values_table SET value = 'x'", new Object[0],
            type, ExecutionPlan.SqlSource.GENERATED);
    }

    private BatchExecutionPlan batchPlan() {
        return new BatchExecutionPlan(
            "test.Mapper.batch", "INSERT INTO values_table(value) VALUES (?)",
            List.<Object[]>of(new Object[]{"Alice"}), ExecutionPlan.SqlSource.GENERATED);
    }
}
