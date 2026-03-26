package org.liteorm.runtime;

import org.liteorm.ExecutionContext;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.TransactionContext;
import org.liteorm.api.TransactionManager;

import java.sql.Connection;

/**
 * 事务处理器 - 基于物理必需性的事务状态管理
 * 
 * 物理职责：
 * 1. 检测现有事务上下文（物理必需：避免冲突）
 * 2. 管理事务状态传递（物理必需：上下文连续性）
 * 3. 设置连接事务属性（物理必需：数据库事务控制）
 * 4. 支持外部事务管理器（扩展必需：框架集成）
 * 
 * 设计原则：项目初期，不支持兼容模式，TransactionManager必需
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class TransactionProcessor implements SqlProcessor {
    
    private final TransactionManager transactionManager;
    
    /**
     * 构造器 - TransactionManager必需，不允许为null
     */
    public TransactionProcessor(TransactionManager transactionManager) {
        this.transactionManager = java.util.Objects.requireNonNull(
            transactionManager, "TransactionManager cannot be null in LiteORM");
    }
    
    @Override
    public void process(ExecutionPlan plan, ExecutionContext context) {
        Connection connection = context.getConnection();
        if (connection == null) {
            throw new IllegalStateException("Connection is null, ConnectionProcessor should run first");
        }
        
        // 检查是否已在事务中
        TransactionContext txContext = transactionManager.getCurrentTransaction();
        
        if (txContext != null) {
            // 已在事务中，复用事务状态
            context.setInTransaction(true);
            context.setTransactionOwner(false); // 不拥有事务
            
            // 确保使用事务管理器的连接
            if (txContext.getConnection() != null) {
                context.setConnection(txContext.getConnection());
            }
        } else if (plan.requiresTransaction()) {
            // 需要事务但没有外部事务，标记需要创建事务
            // 注意：这里不创建事务，由上层代码负责
            context.setInTransaction(false);
            context.setTransactionOwner(false);
        }
    }
}
