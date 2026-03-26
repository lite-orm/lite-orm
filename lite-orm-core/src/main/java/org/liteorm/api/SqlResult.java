package org.liteorm.api;

import java.util.List;

/**
 * SQL执行结果
 * Core包返回给Compiler生成代码的结果封装
 * 
 * 设计原则：
 * - 包含原始的执行结果
 * - 不包含任何映射逻辑（映射由Compiler生成代码完成）
 * - 简单的数据容器
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class SqlResult {
    
    private final List<Object[]> queryResults;   // 查询结果的原始数据
    private final int updateCount;               // 更新行数
    private final boolean isQuery;               // 是否为查询操作
    private final Exception exception;           // 执行异常（如果有）
    
    // 查询结果构造器
    public static SqlResult forQuery(List<Object[]> results) {
        return new SqlResult(results, 0, true, null);
    }
    
    // 更新结果构造器
    public static SqlResult forUpdate(int updateCount) {
        return new SqlResult(null, updateCount, false, null);
    }
    
    // 异常结果构造器
    public static SqlResult forError(Exception exception) {
        return new SqlResult(null, 0, false, exception);
    }
    
    // 兼容方法：success for query
    public static SqlResult success(List<Object[]> results) {
        return forQuery(results);
    }
    
    // 兼容方法：success for update
    public static SqlResult success(int updateCount) {
        return forUpdate(updateCount);
    }
    
    // 兼容方法：error
    public static SqlResult error(Exception exception) {
        return forError(exception);
    }
    
    private SqlResult(List<Object[]> queryResults, int updateCount, boolean isQuery, Exception exception) {
        this.queryResults = queryResults;
        this.updateCount = updateCount;
        this.isQuery = isQuery;
        this.exception = exception;
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
    
    public boolean hasError() {
        return exception != null;
    }
    
    public Exception getException() {
        return exception;
    }
}
