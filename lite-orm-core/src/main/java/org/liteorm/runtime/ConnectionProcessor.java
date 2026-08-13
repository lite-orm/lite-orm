package org.liteorm.runtime;

import org.liteorm.ExecutionContext;
import org.liteorm.api.ConnectionProvider;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.TransactionCoordinator;

import java.sql.Connection;
import java.util.Objects;

/**
 * 连接处理器
 * 职责：获取数据库连接
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class ConnectionProcessor implements SqlProcessor {
    
    private final ConnectionProvider connectionProvider;
    private final TransactionCoordinator transactionCoordinator;
    
    public ConnectionProcessor(ConnectionProvider connectionProvider) {
        this(connectionProvider, () -> null);
    }

    public ConnectionProcessor(ConnectionProvider connectionProvider, TransactionCoordinator transactionCoordinator) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
        this.transactionCoordinator = Objects.requireNonNull(transactionCoordinator, "transactionCoordinator");
    }
    
    @Override
    public void process(ExecutionPlan plan, ExecutionContext context) {
        if (context.getConnection() == null) {
            Connection transactionConnection = transactionCoordinator.currentConnection();
            if (transactionConnection != null) {
                context.setConnection(transactionConnection);
                context.setInTransaction(true);
            } else {
                context.setConnection(connectionProvider.acquire());
            }
        }
    }
}
