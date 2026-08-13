package org.liteorm;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行上下文
 * 责任链中各个处理器之间传递的数据容器
 * 
 * 设计原则：
 * - 包含SQL执行过程中的所有状态
 * - 不包含业务逻辑，只是数据容器
 * - 支持各个处理器的数据传递
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class ExecutionContext {
    
    // 连接相关
    private Connection connection;
    private boolean inTransaction;
    private boolean transactionOwner;  // 是否拥有事务
    
    // SQL执行相关
    private PreparedStatement preparedStatement;
    private ResultSet resultSet;
    
    // 结果相关
    private List<Object[]> queryResults;  // 查询结果
    private int updateCount;              // 更新行数
    private int[] batchUpdateCounts;
    
    // 自定义属性 - 用于Processor之间传递额外数据
    private Map<String, Object> attributes = new HashMap<>();
    
    // Getters and Setters
    public Connection getConnection() {
        return connection;
    }
    
    public void setConnection(Connection connection) {
        this.connection = connection;
    }
    
    public boolean isInTransaction() {
        return inTransaction;
    }
    
    public void setInTransaction(boolean inTransaction) {
        this.inTransaction = inTransaction;
    }
    
    public boolean isTransactionOwner() {
        return transactionOwner;
    }
    
    public void setTransactionOwner(boolean transactionOwner) {
        this.transactionOwner = transactionOwner;
    }
    
    public PreparedStatement getPreparedStatement() {
        return preparedStatement;
    }
    
    public void setPreparedStatement(PreparedStatement preparedStatement) {
        this.preparedStatement = preparedStatement;
    }
    
    public ResultSet getResultSet() {
        return resultSet;
    }
    
    public void setResultSet(ResultSet resultSet) {
        this.resultSet = resultSet;
    }
    
    public List<Object[]> getQueryResults() {
        return queryResults;
    }
    
    public void setQueryResults(List<Object[]> queryResults) {
        this.queryResults = queryResults;
    }
    
    public int getUpdateCount() {
        return updateCount;
    }
    
    public void setUpdateCount(int updateCount) {
        this.updateCount = updateCount;
    }

    public int[] getBatchUpdateCounts() {
        return batchUpdateCounts;
    }

    public void setBatchUpdateCounts(int[] batchUpdateCounts) {
        this.batchUpdateCounts = batchUpdateCounts;
    }
    
    /**
     * 设置自定义属性
     * 用于Processor之间传递额外数据（如时间戳、日志信息等）
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    /**
     * 获取自定义属性
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }
    
    /**
     * 移除自定义属性
     */
    public void removeAttribute(String key) {
        attributes.remove(key);
    }
    
    /**
     * 检查属性是否存在
     */
    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }
}
