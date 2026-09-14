package io.github.kervix.api;

/**
 * Work executed inside an explicit transaction boundary.
 */
@FunctionalInterface
public interface TransactionCallback<T> {

    T execute();
}
