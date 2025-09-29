package org.liteorm.processor;

import org.liteorm.*;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 事务处理器
 * 职责：事务管理（开启、状态检查）
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class TransactionProcessor implements SqlProcessor {
    
    @Override
    public void process(SqlTask task, ExecutionContext context) {
        Connection connection = context.getConnection();
        if (connection == null) {
            throw new IllegalStateException("Connection is null, ConnectionProcessor should run first");
        }
        
        try {
            // 如果任务需要事务且当前不在事务中
            if (task.requiresTransaction()) {
                if (connection.getAutoCommit()) {
                    connection.setAutoCommit(false);
                    context.setInTransaction(true);
                    context.setTransactionOwner(true); // 标记当前任务拥有事务
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to manage transaction", e);
        }
    }
}
