package org.liteorm.api;

import java.util.Objects;

/**
 * Immutable result-set column metadata using a zero-based row-array index.
 */
public record ResultColumn(String label, int index) {

    public ResultColumn {
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
    }
}
