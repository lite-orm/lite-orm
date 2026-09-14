package io.github.kervix.api;

import java.sql.Connection;

/**
 * Provides one execution-scoped JDBC connection without transaction completion authority.
 */
public interface ConnectionHandle extends AutoCloseable {

    Connection connection();

    /**
     * Releases this execution's connection participation. This method does not grant the executor
     * authority to commit or roll back a surrounding standalone or hosted transaction.
     */
    @Override
    void close();
}
