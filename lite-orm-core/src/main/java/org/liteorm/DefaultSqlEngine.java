package org.liteorm;

import org.liteorm.api.ConnectionManager;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.SqlEngine;
import org.liteorm.api.SqlResult;
import org.liteorm.api.TransactionContext;
import org.liteorm.api.TransactionException;
import org.liteorm.api.TransactionManager;
import org.liteorm.runtime.ConnectionProcessor;
import org.liteorm.runtime.ExecutionProcessor;
import org.liteorm.runtime.ParameterProcessor;
import org.liteorm.runtime.ResultProcessor;
import org.liteorm.runtime.SqlProcessor;
import org.liteorm.runtime.TransactionProcessor;

import java.util.Arrays;
import java.util.List;

/**
 * 默认SQL执行引擎实现 - 基于物理必需性的可配置责任链
 * 
 * 核心改进（基于第一性原理）：
 * 1. 处理器列表可外部传入 - 支持扩展
 * 2. 事务管理支持自管理和外部管理
 * 3. 每个处理器都有明确的物理职责
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class DefaultSqlEngine implements SqlEngine {
    
    private final List<SqlProcessor> processors;
    private final ConnectionManager connectionManager;
    
    /**
     * 默认构造器 - 使用标准的5个物理必需处理器
     */
    public DefaultSqlEngine(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.processors = createDefaultProcessors(connectionManager);
    }
    
    /**
     * 可配置构造器 - 支持外部传入处理器列表
     * 这满足了"不写死"的需求，支持扩展
     */
    public DefaultSqlEngine(ConnectionManager connectionManager, List<SqlProcessor> processors) {
        this.connectionManager = connectionManager;
        this.processors = processors;
    }
    
    /**
     * 创建默认的5个物理必需处理器
     * 基于SQL执行的物理步骤：连接→事务→参数→执行→结果
     */
    private List<SqlProcessor> createDefaultProcessors(ConnectionManager connectionManager) {
        // 创建默认的事务管理器
        TransactionManager defaultTransactionManager = new org.liteorm.DefaultTransactionManager(connectionManager);
        
        return Arrays.asList(
            new ConnectionProcessor(connectionManager),              // 1. 获取连接（物理必需）
            new TransactionProcessor(defaultTransactionManager),     // 2. 事务管理（一致性必需）
            new ParameterProcessor(),                                // 3. 参数绑定（安全性必需）  
            new ExecutionProcessor(),                                // 4. SQL执行（核心必需）
            new ResultProcessor()                                    // 5. 结果提取（数据获取必需）
        );
    }
    
    @Override
    public SqlResult execute(ExecutionPlan plan) {
        // 创建执行上下文
        ExecutionContext context = new ExecutionContext();
        
        try {
            // 按顺序执行所有处理器 - 这是物理执行链
            for (SqlProcessor processor : processors) {
                processor.process(plan, context);
            }
            
            // 构造成功结果
            return createSuccessResult(context, plan);
            
        } catch (Exception e) {
            // 构造错误结果
            return SqlResult.error(e);
        } finally {
            closeExecutionResources(context);
        }
    }

    private void closeExecutionResources(ExecutionContext context) {
        try {
            if (context.getResultSet() != null) {
                context.getResultSet().close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (context.getPreparedStatement() != null) {
                context.getPreparedStatement().close();
            }
        } catch (Exception ignored) {
        }
        if (context.getConnection() != null) {
            connectionManager.releaseConnection(context.getConnection());
        }
    }
    
    /**
     * 构造成功结果
     */
    private SqlResult createSuccessResult(ExecutionContext context, ExecutionPlan plan) {
        switch (plan.getStatementType()) {
            case SELECT:
                return SqlResult.success(context.getQueryResults());
            case INSERT:
            case UPDATE:
            case DELETE:
                return SqlResult.success(context.getUpdateCount());
            default:
                throw new UnsupportedOperationException("Unsupported SQL type: " + plan.getStatementType());
        }
    }
    
    @Override
    public TransactionContext beginTransaction() {
        // 委托给TransactionManager
        TransactionManager txManager = new DefaultTransactionManager(connectionManager);
        try {
            return txManager.begin();
        } catch (TransactionException e) {
            throw new RuntimeException("Failed to begin transaction", e);
        }
    }
    
    @Override
    public void commitTransaction(TransactionContext txContext) {
        if (txContext == null) {
            throw new IllegalArgumentException("TransactionContext cannot be null");
        }
        
        TransactionManager txManager = new DefaultTransactionManager(connectionManager);
        try {
            txManager.commit(txContext);
        } catch (TransactionException e) {
            throw new RuntimeException("Failed to commit transaction", e);
        }
    }
    
    @Override
    public void rollbackTransaction(TransactionContext txContext) {
        if (txContext == null) {
            throw new IllegalArgumentException("TransactionContext cannot be null");
        }
        
        TransactionManager txManager = new DefaultTransactionManager(connectionManager);
        try {
            txManager.rollback(txContext);
        } catch (TransactionException e) {
            throw new RuntimeException("Failed to rollback transaction", e);
        }
    }
}
