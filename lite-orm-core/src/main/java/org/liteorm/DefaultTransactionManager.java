package org.liteorm;

import org.liteorm.api.ConnectionManager;
import org.liteorm.api.TransactionContext;
import org.liteorm.api.TransactionException;
import org.liteorm.api.TransactionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * 默认事务管理器实现 - 基于物理必需性的自管理事务
 * 
 * 物理实现原理：
 * 1. ThreadLocal管理事务上下文（物理必需：线程隔离）
 * 2. Connection级别的事务控制（物理必需：数据库事务）
 * 3. 自动回滚异常处理（物理必需：一致性保证）
 * 4. 嵌套事务检测（物理必需：防止冲突）
 * 
 * 设计特点：
 * - 基于ThreadLocal确保线程安全
 * - 支持嵌套事务检测和拒绝
 * - 自动异常回滚
 * - 资源清理保证
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class DefaultTransactionManager implements TransactionManager {
    
    private final ConnectionManager connectionManager;
    private final ThreadLocal<TransactionContext> currentTransaction = new ThreadLocal<>();
    
    public DefaultTransactionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
    
    @Override
    public TransactionContext begin() throws TransactionException {
        // 检查是否已经在事务中（物理必需：防止嵌套事务冲突）
        if (isInTransaction()) {
            throw new TransactionException(
                TransactionException.Type.BEGIN_FAILED,
                "Already in transaction: " + getCurrentTransaction().getTransactionId()
            );
        }
        
        try {
            // 获取连接（物理必需：事务载体）
            Connection connection = connectionManager.getConnection();
            
            // 关闭自动提交（物理必需：事务控制）
            connection.setAutoCommit(false);
            
            // 创建事务上下文
            String transactionId = "tx-" + UUID.randomUUID().toString().substring(0, 8);
            TransactionContext context = new TransactionContext(connection, transactionId);
            
            // 绑定到当前线程（物理必需：线程隔离）
            currentTransaction.set(context);
            
            return context;
            
        } catch (SQLException e) {
            throw new TransactionException(
                TransactionException.Type.BEGIN_FAILED,
                "Failed to begin transaction: " + e.getMessage(),
                e
            );
        }
    }
    
    @Override
    public void commit(TransactionContext context) throws TransactionException {
        validateTransaction(context);
        
        try {
            // 执行提交（物理必需：持久化更改）
            context.getConnection().commit();
            context.setCommitted(true);
            
            // 恢复自动提交（物理必需：连接状态恢复）
            context.getConnection().setAutoCommit(true);
            
        } catch (SQLException e) {
            // 提交失败，尝试回滚
            try {
                context.getConnection().rollback();
                context.setRolledBack(true);
            } catch (SQLException rollbackEx) {
                // 回滚也失败，记录两个异常
                TransactionException te = new TransactionException(
                    TransactionException.Type.ROLLBACK_FAILED,
                    "Rollback failed after commit failure",
                    rollbackEx
                );
                te.addSuppressed(e);
                throw te;
            }
            
            throw new TransactionException(
                TransactionException.Type.COMMIT_FAILED,
                "Transaction commit failed: " + e.getMessage(),
                e
            );
        } finally {
            // 清理线程上下文（物理必需：资源释放）
            cleanup();
        }
    }
    
    @Override
    public void rollback(TransactionContext context) throws TransactionException {
        validateTransaction(context);
        
        try {
            // 执行回滚（物理必需：撤销更改）
            context.getConnection().rollback();
            context.setRolledBack(true);
            
            // 恢复自动提交（物理必需：连接状态恢复）
            context.getConnection().setAutoCommit(true);
            
        } catch (SQLException e) {
            throw new TransactionException(
                TransactionException.Type.ROLLBACK_FAILED,
                "Transaction rollback failed: " + e.getMessage(),
                e
            );
        } finally {
            // 清理线程上下文（物理必需：资源释放）
            cleanup();
        }
    }
    
    @Override
    public TransactionContext getCurrentTransaction() {
        return currentTransaction.get();
    }
    
    @Override
    public boolean isInTransaction() {
        TransactionContext context = getCurrentTransaction();
        return context != null && context.isActive();
    }
    
    @Override
    public <T> T executeInTransaction(TransactionalOperation<T> operation) throws TransactionException {
        TransactionContext context = begin();
        
        try {
            // 执行业务操作
            T result = operation.execute(context);
            
            // 成功则提交
            commit(context);
            return result;
            
        } catch (Exception e) {
            // 失败则回滚
            try {
                rollback(context);
            } catch (TransactionException rollbackEx) {
                // 回滚失败，添加到异常链
                e.addSuppressed(rollbackEx);
            }
            
            // 重新抛出原始异常
            if (e instanceof TransactionException) {
                throw (TransactionException) e;
            } else {
                throw new TransactionException(
                    TransactionException.Type.COMMIT_FAILED,
                    "Operation failed in transaction: " + e.getMessage(),
                    e
                );
            }
        }
    }
    
    /**
     * 验证事务上下文
     */
    private void validateTransaction(TransactionContext context) throws TransactionException {
        if (context == null) {
            throw new TransactionException(
                TransactionException.Type.BEGIN_FAILED,
                "Transaction context is null"
            );
        }
        
        if (!context.isActive()) {
            throw new TransactionException(
                TransactionException.Type.BEGIN_FAILED,
                "Transaction is not active: " + context.getTransactionId()
            );
        }
        
        TransactionContext current = getCurrentTransaction();
        if (current == null || !current.getTransactionId().equals(context.getTransactionId())) {
            throw new TransactionException(
                TransactionException.Type.BEGIN_FAILED,
                "Transaction context mismatch"
            );
        }
    }
    
    /**
     * 清理线程上下文
     */
    private void cleanup() {
        currentTransaction.remove();
    }
}
