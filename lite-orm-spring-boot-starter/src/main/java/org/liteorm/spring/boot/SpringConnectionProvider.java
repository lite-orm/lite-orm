package org.liteorm.spring.boot;

import org.liteorm.api.ConnectionProvider;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Objects;

/**
 * Adapts Spring-managed JDBC connections to LiteORM's connection contract.
 */
public final class SpringConnectionProvider implements ConnectionProvider {

    private final DataSource dataSource;

    public SpringConnectionProvider(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Connection acquire() {
        return DataSourceUtils.getConnection(dataSource);
    }

    @Override
    public void release(Connection connection) {
        DataSourceUtils.releaseConnection(connection, dataSource);
    }

    public DataSource getDataSource() {
        return dataSource;
    }
}
