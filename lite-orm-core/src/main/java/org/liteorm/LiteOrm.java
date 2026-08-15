package org.liteorm;

import org.liteorm.api.ConfigurationException;

import javax.sql.DataSource;

/**
 * Entry point for assembling LiteORM JDBC components.
 */
public final class LiteOrm {

    private LiteOrm() {
    }

    public static JdbcAssembly.Builder jdbc(DataSource dataSource) {
        if (dataSource == null) {
            throw new ConfigurationException("dataSource must not be null");
        }
        return new JdbcAssembly.Builder(dataSource);
    }
}
