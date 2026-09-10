package org.liteorm.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable successful SQL execution result consumed by generated Mapper code.
 * Mapping logic remains in generated code, while execution failures are raised as
 * {@link SqlExecutionException}.
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public final class SqlResult<T> {
    
    private final List<T> queryResults;
    private final List<ResultColumn> resultColumns;
    private final Map<String, Integer> columnIndexes;
    private final int updateCount;
    private final boolean isQuery;
    private final int[] batchUpdateCounts;
    private final Object generatedKey;
    private final boolean rawRows;
    
    // Creates a query result.
    public static SqlResult<Object[]> forQuery(List<Object[]> results) {
        return forQuery(List.of(), results);
    }

    public static SqlResult<Object[]> forQuery(List<ResultColumn> columns, List<Object[]> results) {
        return new SqlResult<>(copyRows(results), columns, 0, true, null, null, true);
    }

    /** Creates a query result whose rows have already been assembled to their Java target type. */
    public static <T> SqlResult<T> forMappedQuery(List<ResultColumn> columns, List<T> results) {
        return new SqlResult<>(results, columns, 0, true, null, null, false);
    }
    
    // Creates an update result.
    public static SqlResult<Void> forUpdate(int updateCount) {
        return new SqlResult<>(null, List.of(), updateCount, false, null, null, false);
    }

    public static SqlResult<Void> forGeneratedKey(int updateCount, Object generatedKey) {
        return new SqlResult<>(null, List.of(), updateCount, false, null, generatedKey, false);
    }

    public static SqlResult<Void> forBatch(int[] updateCounts) {
        return new SqlResult<>(null, List.of(), 0, false, updateCounts, null, false);
    }
    
    private SqlResult(
            List<T> queryResults,
            List<ResultColumn> resultColumns,
            int updateCount,
            boolean isQuery,
            int[] batchUpdateCounts,
            Object generatedKey,
            boolean rawRows) {
        this.queryResults = queryResults == null
            ? null : Collections.unmodifiableList(new ArrayList<>(queryResults));
        this.resultColumns = List.copyOf(Objects.requireNonNull(resultColumns, "resultColumns"));
        this.columnIndexes = indexColumns(this.resultColumns);
        if (rawRows) {
            validateRowWidths(copyRowsForValidation(this.queryResults), this.resultColumns);
        }
        this.updateCount = updateCount;
        this.isQuery = isQuery;
        this.batchUpdateCounts = batchUpdateCounts == null ? null : batchUpdateCounts.clone();
        this.generatedKey = generatedKey;
        this.rawRows = rawRows;
    }
    
    // Getters
    @SuppressWarnings("unchecked")
    public List<T> getQueryResults() {
        return rawRows && queryResults != null
            ? (List<T>) copyRows((List<Object[]>) queryResults)
            : queryResults;
    }

    public List<ResultColumn> getResultColumns() {
        return resultColumns;
    }

    public int requireColumnIndex(String label) {
        Objects.requireNonNull(label, "label");
        Integer index = columnIndexes.get(normalizeLabel(label));
        if (index == null) {
            throw new IllegalArgumentException("Required result column is missing: " + label);
        }
        return index;
    }
    
    public int getUpdateCount() {
        return updateCount;
    }
    
    public boolean isQuery() {
        return isQuery;
    }

    public int[] getBatchUpdateCounts() {
        return batchUpdateCounts == null ? null : batchUpdateCounts.clone();
    }

    public Object getGeneratedKey() {
        return generatedKey;
    }

    private static List<Object[]> copyRows(List<Object[]> rows) {
        if (rows == null) {
            return null;
        }
        return rows.stream()
            .map(row -> Objects.requireNonNull(row, "query row").clone())
            .toList();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<Object[]> copyRowsForValidation(List<T> rows) {
        return (List<Object[]>) rows;
    }

    private static Map<String, Integer> indexColumns(List<ResultColumn> columns) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (ResultColumn column : columns) {
            String normalizedLabel = normalizeLabel(column.label());
            Integer existing = indexes.putIfAbsent(normalizedLabel, column.index());
            if (existing != null) {
                throw new IllegalArgumentException("Duplicate result column label: " + column.label());
            }
        }
        return Map.copyOf(indexes);
    }

    private static void validateRowWidths(List<Object[]> rows, List<ResultColumn> columns) {
        if (rows == null || columns.isEmpty()) {
            return;
        }
        int requiredWidth = columns.stream().mapToInt(ResultColumn::index).max().orElse(-1) + 1;
        if (rows.stream().anyMatch(row -> row.length < requiredWidth)) {
            throw new IllegalArgumentException("Query row is shorter than result column metadata");
        }
    }

    private static String normalizeLabel(String label) {
        return label.trim().toLowerCase(Locale.ROOT);
    }
}
