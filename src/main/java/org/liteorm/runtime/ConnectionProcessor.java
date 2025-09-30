package org.liteorm.runtime;

import org.liteorm.ExecutionContext;
import org.liteorm.api.ConnectionManager;
import org.liteorm.api.SqlTask;

/**
 * 连接处理器
 * 职责：获取数据库连接
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class ConnectionProcessor implements SqlProcessor {
    
    private final ConnectionManager connectionManager;
    
    public ConnectionProcessor(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
    
    @Override
    public void process(SqlTask task, ExecutionContext context) {
        // 获取连接（支持事务连接复用）
        context.setConnection(connectionManager.getConnection());
    }
}
