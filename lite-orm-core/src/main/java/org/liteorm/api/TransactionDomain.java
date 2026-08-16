package org.liteorm.api;

/**
 * Immutable identity of one DataSource and transaction ownership domain.
 */
public record TransactionDomain(String key) {

    public TransactionDomain {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
    }
}
