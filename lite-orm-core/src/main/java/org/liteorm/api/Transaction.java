package org.liteorm.api;

import java.sql.Connection;

/**
 * Owns the JDBC connection and completion semantics for one transaction handle.
 *
 * <p>A handle returned by {@link TransactionFactory} is confined to one execution call and
 * implements the correct close behavior for either an owned connection or participation in an
 * existing transaction.</p>
 */
public interface Transaction extends AutoCloseable {

    Connection getConnection();

    void commit();

    void rollback();

    @Override
    void close();

    default Integer getTimeoutSeconds() {
        return null;
    }
}
