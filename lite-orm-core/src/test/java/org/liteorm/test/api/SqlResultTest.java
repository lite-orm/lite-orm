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
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlResultTest {

    @Test
    void queryResultsAreDeeplyImmutable() {
        Object[] sourceRow = {1L, "Alice"};
        List<Object[]> sourceRows = new ArrayList<>();
        sourceRows.add(sourceRow);
        SqlResult result = SqlResult.forQuery(sourceRows);

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
        SqlResult result = SqlResult.forBatch(sourceCounts);

        sourceCounts[0] = 9;
        int[] returnedCounts = result.getBatchUpdateCounts();
        returnedCounts[1] = 8;

        assertArrayEquals(new int[]{1, 2}, result.getBatchUpdateCounts());
    }

    @Test
    void compatibilitySuccessFactoriesAreNotPublicApi() {
        assertFalse(Arrays.stream(SqlResult.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("success")));
    }

    @Test
    void columnLabelsAreCaseInsensitiveAndUnique() {
        SqlResult result = SqlResult.forQuery(
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
