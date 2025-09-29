package org.liteorm;

import java.sql.Connection;

/**
 * 事务上下文
 * 封装事务相关的状态和连接
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class TransactionContext {
    
    private final Connection connection;
    private final String transactionId;
    private boolean committed;
    private boolean rolledBack;
    
    public TransactionContext(Connection connection, String transactionId) {
        this.connection = connection;
        this.transactionId = transactionId;
        this.committed = false;
        this.rolledBack = false;
    }
    
    public Connection getConnection() {
        return connection;
    }
    
    public String getTransactionId() {
        return transactionId;
    }
    
    public boolean isCommitted() {
        return committed;
    }
    
    public void setCommitted(boolean committed) {
        this.committed = committed;
    }
    
    public boolean isRolledBack() {
        return rolledBack;
    }
    
    public void setRolledBack(boolean rolledBack) {
        this.rolledBack = rolledBack;
    }
    
    public boolean isActive() {
        return !committed && !rolledBack;
    }
}
