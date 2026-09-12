package org.liteorm.test.api;

import org.junit.jupiter.api.Test;
import org.liteorm.api.ResultColumn;
import org.liteorm.api.SqlResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlResultTest {

    @Test
    void queryFactoryRepresentsEmptySingleAndMultipleRowsAsOneQueryShape() {
        SqlResult<Object[]> empty = SqlResult.forQuery(List.of());
        SqlResult<Object[]> single = SqlResult.forQuery(
            List.<Object[]>of(new Object[]{"Alice"}));
        SqlResult<Object[]> multiple = SqlResult.forQuery(
            List.of(new Object[]{"Alice"}, new Object[]{"Bob"}));

        for (SqlResult<Object[]> result : List.of(empty, single, multiple)) {
            assertTrue(result.isQuery());
            assertEquals(0, result.getUpdateCount());
            assertNull(result.getGeneratedKey());
            assertNull(result.getBatchUpdateCounts());
        }
        assertEquals(0, empty.getQueryResults().size());
        assertEquals(1, single.getQueryResults().size());
        assertEquals(2, multiple.getQueryResults().size());
    }

    @Test
    void updateFactoryPreservesZeroAndPositiveUpdateCounts() {
        SqlResult<Void> noRows = SqlResult.forUpdate(0);
        SqlResult<Void> rows = SqlResult.forUpdate(3);

        for (SqlResult<Void> result : List.of(noRows, rows)) {
            assertFalse(result.isQuery());
            assertNull(result.getQueryResults());
            assertNull(result.getGeneratedKey());
            assertNull(result.getBatchUpdateCounts());
        }
        assertEquals(0, noRows.getUpdateCount());
        assertEquals(3, rows.getUpdateCount());
    }

    @Test
    void generatedKeyFactoryPreservesUpdateCountAndNullableKey() {
        SqlResult<Void> missingKey = SqlResult.forGeneratedKey(0, null);
        SqlResult<Void> generatedKey = SqlResult.forGeneratedKey(1, 42L);

        for (SqlResult<Void> result : List.of(missingKey, generatedKey)) {
            assertFalse(result.isQuery());
            assertNull(result.getQueryResults());
            assertNull(result.getBatchUpdateCounts());
        }
        assertEquals(0, missingKey.getUpdateCount());
        assertNull(missingKey.getGeneratedKey());
        assertEquals(1, generatedKey.getUpdateCount());
        assertEquals(42L, generatedKey.getGeneratedKey());
    }

    @Test
    void batchFactoryRepresentsEmptySingleAndMultipleDriverCounts() {
        SqlResult<Void> empty = SqlResult.forBatch(new int[0]);
        SqlResult<Void> single = SqlResult.forBatch(new int[]{1});
        SqlResult<Void> multiple = SqlResult.forBatch(new int[]{1, 0, -3});

        for (SqlResult<Void> result : List.of(empty, single, multiple)) {
            assertFalse(result.isQuery());
            assertNull(result.getQueryResults());
            assertEquals(0, result.getUpdateCount());
            assertNull(result.getGeneratedKey());
        }
        assertArrayEquals(new int[0], empty.getBatchUpdateCounts());
        assertArrayEquals(new int[]{1}, single.getBatchUpdateCounts());
        assertArrayEquals(new int[]{1, 0, -3}, multiple.getBatchUpdateCounts());
    }

    @Test
    void queryResultsAreDeeplyImmutable() {
        Object[] sourceRow = {1L, "Alice"};
        List<Object[]> sourceRows = new ArrayList<>();
        sourceRows.add(sourceRow);
        SqlResult<Object[]> result = SqlResult.forQuery(sourceRows);

        sourceRow[0] = 2L;
        sourceRows.add(new Object[]{3L, "Bob"});
        List<Object[]> returnedRows = result.getQueryResults();
        returnedRows.getFirst()[1] = "Changed";

        assertEquals(1, result.getQueryResults().size());
        assertArrayEquals(new Object[]{1L, "Alice"}, result.getQueryResults().getFirst());
        assertThrows(UnsupportedOperationException.class,
            () -> returnedRows.add(new Object[]{4L, "Carol"}));
    }

    @Test
    void batchCountsAreDefensivelyCopied() {
        int[] sourceCounts = {1, 2};
        SqlResult<Void> result = SqlResult.forBatch(sourceCounts);

        sourceCounts[0] = 9;
        int[] returnedCounts = result.getBatchUpdateCounts();
        returnedCounts[1] = 8;

        assertArrayEquals(new int[]{1, 2}, result.getBatchUpdateCounts());
    }

    @Test
    void mappedQueryResultsAreImmutableAndMayContainNull() {
        List<String> sourceRows = new ArrayList<>();
        sourceRows.add(null);
        SqlResult<String> result = SqlResult.forMappedQuery(List.of(), sourceRows);

        sourceRows.add("Alice");

        assertEquals(1, result.getQueryResults().size());
        assertNull(result.getQueryResults().getFirst());
        assertThrows(UnsupportedOperationException.class,
            () -> result.getQueryResults().add("Bob"));
    }

    @Test
    void compatibilitySuccessFactoriesAreNotPublicApi() {
        assertFalse(Arrays.stream(SqlResult.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("success")));
    }

    @Test
    void columnLabelsAreCaseInsensitiveAndUnique() {
        SqlResult<Object[]> result = SqlResult.forQuery(
            List.of(new ResultColumn("user_name", 0), new ResultColumn("ID", 1)),
            List.<Object[]>of(new Object[]{"Alice", 7L}));

        assertEquals(0, result.requireColumnIndex("USER_NAME"));
        assertEquals(1, result.requireColumnIndex("id"));
        assertThrows(IllegalArgumentException.class, () -> result.requireColumnIndex("missing"));
        assertThrows(IllegalArgumentException.class, () -> SqlResult.forQuery(
            List.of(new ResultColumn("id", 0), new ResultColumn("ID", 1)),
            List.<Object[]>of(new Object[]{7L, 8L})));
    }
}
