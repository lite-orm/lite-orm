package org.liteorm.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * SQL执行结果
 * Core包返回给Compiler生成代码的结果封装
 * 
 * 设计原则：
 * - 包含原始的执行结果
 * - 不包含任何映射逻辑（映射由Compiler生成代码完成）
 * - 只表示成功结果；执行失败由 SqlExecutionException 直接抛出
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public final class SqlResult {
    
    private final List<Object[]> queryResults;   // 查询结果的原始数据
    private final List<ResultColumn> resultColumns;
    private final Map<String, Integer> columnIndexes;
    private final int updateCount;               // 更新行数
    private final boolean isQuery;               // 是否为查询操作
    private final int[] batchUpdateCounts;
    private final Object generatedKey;
    
    // 查询结果构造器
    public static SqlResult forQuery(List<Object[]> results) {
        return forQuery(List.of(), results);
    }

    public static SqlResult forQuery(List<ResultColumn> columns, List<Object[]> results) {
        return new SqlResult(results, columns, 0, true, null, null);
    }
    
    // 更新结果构造器
    public static SqlResult forUpdate(int updateCount) {
        return new SqlResult(null, List.of(), updateCount, false, null, null);
    }

    public static SqlResult forGeneratedKey(int updateCount, Object generatedKey) {
        return new SqlResult(null, List.of(), updateCount, false, null, generatedKey);
    }

    public static SqlResult forBatch(int[] updateCounts) {
        return new SqlResult(null, List.of(), 0, false, updateCounts, null);
    }
    
    private SqlResult(
            List<Object[]> queryResults,
            List<ResultColumn> resultColumns,
            int updateCount,
            boolean isQuery,
            int[] batchUpdateCounts,
            Object generatedKey) {
        this.queryResults = copyRows(queryResults);
        this.resultColumns = List.copyOf(Objects.requireNonNull(resultColumns, "resultColumns"));
        this.columnIndexes = indexColumns(this.resultColumns);
        validateRowWidths(this.queryResults, this.resultColumns);
        this.updateCount = updateCount;
        this.isQuery = isQuery;
        this.batchUpdateCounts = batchUpdateCounts == null ? null : batchUpdateCounts.clone();
        this.generatedKey = generatedKey;
    }
    
    // Getters
    public List<Object[]> getQueryResults() {
        return copyRows(queryResults);
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
