package org.liteorm;

import org.liteorm.api.ConnectionManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 简单的连接管理器实现
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class SimpleConnectionManager implements ConnectionManager {

    private DataSource dataSource;
    private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();
    
    public SimpleConnectionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public Connection getConnection() {
        // 如果当前线程有事务连接，返回事务连接
        Connection txConnection = transactionConnection.get();
        if (txConnection != null) {
            return txConnection;
        }

        // 否则从数据源获取新连接
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get connection", e);
        }
    }
    

    @Override
    public void releaseConnection(Connection connection) {
        // 如果是事务连接，不释放
        if (transactionConnection.get() == connection) {
            return;
        }

        // 释放普通连接
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Failed to close connection: " + e.getMessage());
        }
    }

    @Override
    public Connection beginTransaction() {
        try {
            Connection connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            transactionConnection.set(connection);
            return connection;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to begin transaction", e);
        }
    }

    @Override
    public void commitTransaction(Connection connection) {
        try {
            if (connection != null) {
                connection.commit();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to commit transaction", e);
        } finally {
            transactionConnection.remove();
            releaseConnection(connection);
        }
    }

    @Override
    public void rollbackTransaction(Connection connection) {
        try {
            if (connection != null) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Failed to rollback transaction: " + e.getMessage());
        } finally {
            transactionConnection.remove();
            releaseConnection(connection);
        }
    }

    @Override
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }
}
