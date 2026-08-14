package org.liteorm.api;

import java.util.List;

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
public class SqlResult {
    
    private final List<Object[]> queryResults;   // 查询结果的原始数据
    private final int updateCount;               // 更新行数
    private final boolean isQuery;               // 是否为查询操作
    private final int[] batchUpdateCounts;
    private final Object generatedKey;
    
    // 查询结果构造器
    public static SqlResult forQuery(List<Object[]> results) {
        return new SqlResult(results, 0, true, null, null);
    }
    
    // 更新结果构造器
    public static SqlResult forUpdate(int updateCount) {
        return new SqlResult(null, updateCount, false, null, null);
    }

    public static SqlResult forGeneratedKey(int updateCount, Object generatedKey) {
        return new SqlResult(null, updateCount, false, null, generatedKey);
    }

    public static SqlResult forBatch(int[] updateCounts) {
        return new SqlResult(null, 0, false, updateCounts.clone(), null);
    }
    
    // 兼容方法：success for query
    public static SqlResult success(List<Object[]> results) {
        return forQuery(results);
    }
    
    // 兼容方法：success for update
    public static SqlResult success(int updateCount) {
        return forUpdate(updateCount);
    }
    
    private SqlResult(
            List<Object[]> queryResults,
            int updateCount,
            boolean isQuery,
            int[] batchUpdateCounts,
            Object generatedKey) {
        this.queryResults = queryResults;
        this.updateCount = updateCount;
        this.isQuery = isQuery;
        this.batchUpdateCounts = batchUpdateCounts;
        this.generatedKey = generatedKey;
    }
    
    // Getters
    public List<Object[]> getQueryResults() {
        return queryResults;
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
    
}
