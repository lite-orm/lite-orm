package io.github.lynxus;

import io.github.lynxus.api.ConfigurationException;

import javax.sql.DataSource;

/**
 * Entry point for assembling Lynxus JDBC components.
 */
public final class Lynxus {

    private Lynxus() {
    }

    public static JdbcAssembly.Builder jdbc(DataSource dataSource) {
        if (dataSource == null) {
            throw new ConfigurationException("dataSource must not be null");
        }
        return new JdbcAssembly.Builder(dataSource);
    }
}
