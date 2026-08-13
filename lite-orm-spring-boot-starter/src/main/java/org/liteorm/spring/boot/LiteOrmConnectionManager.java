package org.liteorm.spring.boot;

import org.liteorm.api.ConnectionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;

public class LiteOrmConnectionManager implements ConnectionManager {

    private final DataSource dataSource;

    public LiteOrmConnectionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getConnection() {
        return DataSourceUtils.getConnection(dataSource);
    }

    @Override
    public void releaseConnection(Connection connection) {
        DataSourceUtils.releaseConnection(connection, dataSource);
    }

    @Override
    public Connection beginTransaction() {
        throw new UnsupportedOperationException("Use Spring @Transactional for transaction management");
    }

    @Override
    public void commitTransaction(Connection connection) {
        throw new UnsupportedOperationException("Use Spring @Transactional for transaction management");
    }

    @Override
    public void rollbackTransaction(Connection connection) {
        throw new UnsupportedOperationException("Use Spring @Transactional for transaction management");
    }

    @Override
    public void setDataSource(DataSource dataSource) {
        throw new UnsupportedOperationException("LiteOrmConnectionManager DataSource is immutable");
    }

    public DataSource getDataSource() {
        return dataSource;
    }
}
