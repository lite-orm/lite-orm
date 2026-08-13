package org.liteorm.api;

import java.sql.Connection;

/**
 * Provides JDBC connections and releases them according to the hosting environment.
 * Implementations must be thread-safe.
 */
public interface ConnectionProvider {

    Connection acquire();

    void release(Connection connection);
}
