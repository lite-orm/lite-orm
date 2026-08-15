package org.liteorm.api;

/**
 * Creates a transaction handle for one SQL execution.
 *
 * <p>Implementations must be thread-safe. Returned handles are not shared between concurrent
 * calls and own their participation or connection-release semantics.</p>
 */
@FunctionalInterface
public interface TransactionFactory {

    Transaction openTransaction();
}
