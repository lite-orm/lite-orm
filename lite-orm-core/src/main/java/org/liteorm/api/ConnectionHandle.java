package org.liteorm.api;

import java.sql.Connection;

/**
 * Provides one execution-scoped JDBC connection without transaction completion authority.
 */
public interface ConnectionHandle extends AutoCloseable {

    Connection connection();

    @Override
    void close();
}
