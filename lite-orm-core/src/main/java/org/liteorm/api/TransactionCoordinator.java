package org.liteorm.api;

import java.sql.Connection;

/**
 * Exposes the connection owned by the current transaction, if any.
 */
public interface TransactionCoordinator {

    Connection currentConnection();

    default boolean isActive() {
        return currentConnection() != null;
    }
}
