package io.github.lynxus.api;

/**
 * Opens a connection handle for one SQL execution.
 */
@FunctionalInterface
public interface ConnectionHandleFactory {

    ConnectionHandle openHandle();
}
