package io.github.lynxus.api;

/**
 * Verifies that an executor belongs to the currently active transaction domain.
 */
@FunctionalInterface
public interface TransactionDomainGuard {

    void verify(TransactionDomain domain);
}
