package org.liteorm.api;

/**
 * Opens a connection handle for one SQL execution.
 */
@FunctionalInterface
public interface ConnectionHandleFactory {

    ConnectionHandle openHandle();
}
