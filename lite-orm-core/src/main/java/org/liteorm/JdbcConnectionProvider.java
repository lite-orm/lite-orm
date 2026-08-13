package org.liteorm;

import org.liteorm.api.ConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 简单的连接管理器实现
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public final class JdbcConnectionProvider implements ConnectionProvider {

    private final DataSource dataSource;
    
    public JdbcConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public Connection acquire() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get connection", e);
        }
    }
    

    @Override
    public void release(Connection connection) {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close connection", e);
        }
    }
}
