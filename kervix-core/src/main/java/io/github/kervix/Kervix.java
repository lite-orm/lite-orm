package io.github.kervix;

import io.github.kervix.api.ConfigurationException;

import javax.sql.DataSource;

/**
 * Entry point for assembling Kervix JDBC components.
 */
public final class Kervix {

    private Kervix() {
    }

    public static JdbcAssembly.Builder jdbc(DataSource dataSource) {
        if (dataSource == null) {
            throw new ConfigurationException("dataSource must not be null");
        }
        return new JdbcAssembly.Builder(dataSource);
    }
}
