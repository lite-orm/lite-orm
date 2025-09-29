package org.liteorm;

import org.liteorm.processor.*;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 默认SQL执行引擎实现
 * 组织责任链完成SQL执行
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class DefaultSqlEngine implements SqlEngine {
    
    private final List<SqlProcessor> processors;
    private final ConnectionManager connectionManager;
    
    public DefaultSqlEngine(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.processors = Arrays.asList(
            new ConnectionProcessor(connectionManager),  // 1. 获取连接
            new TransactionProcessor(),                  // 2. 事务管理
            new ParameterProcessor(),                    // 3. 参数绑定
            new ExecutionProcessor(),                    // 4. SQL执行
            new ResultProcessor()                        // 5. 结果提取
        );
    }
    
    @Override
    public SqlResult execute(SqlTask task) {
        ExecutionContext context = new ExecutionContext();
        
        try {
            // 依次执行责任链
            for (SqlProcessor processor : processors) {
                processor.process(task, context);
            }
            
            // 构建结果
            if (task.getSqlType() == SqlTask.SqlType.SELECT) {
                return SqlResult.forQuery(context.getQueryResults());
            } else {
                return SqlResult.forUpdate(context.getUpdateCount());
            }
            
        } catch (Exception e) {
            return SqlResult.forError(e);
        } finally {
            // 资源清理
            cleanupResources(context);
        }
    }
    
    @Override
    public TransactionContext beginTransaction() {
        try {
            Connection connection = connectionManager.beginTransaction();
            String txId = UUID.randomUUID().toString();
            return new TransactionContext(connection, txId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to begin transaction", e);
        }
    }
    
    @Override
    public void commitTransaction(TransactionContext txContext) {
        try {
            connectionManager.commitTransaction(txContext.getConnection());
            txContext.setCommitted(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to commit transaction", e);
        }
    }
    
    @Override
    public void rollbackTransaction(TransactionContext txContext) {
        try {
            connectionManager.rollbackTransaction(txContext.getConnection());
            txContext.setRolledBack(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to rollback transaction", e);
        }
    }
    
    /**
     * 资源清理
     */
    private void cleanupResources(ExecutionContext context) {
        // 关闭PreparedStatement
        if (context.getPreparedStatement() != null) {
            try {
                context.getPreparedStatement().close();
            } catch (Exception e) {
                System.err.println("Failed to close PreparedStatement: " + e.getMessage());
            }
        }
        
        // 关闭ResultSet
        if (context.getResultSet() != null) {
            try {
                context.getResultSet().close();
            } catch (Exception e) {
                System.err.println("Failed to close ResultSet: " + e.getMessage());
            }
        }
        
        // 释放连接（如果不在事务中）
        if (!context.isInTransaction() && context.getConnection() != null) {
            connectionManager.releaseConnection(context.getConnection());
        }
    }
}
