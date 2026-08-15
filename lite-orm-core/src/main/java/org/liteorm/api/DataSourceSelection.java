package org.liteorm.api;

import java.util.Objects;

/**
 * Immutable typed input for dynamic DataSource selection.
 */
public record DataSourceSelection<P>(
        String statementId,
        ExecutionPlan.StatementType statementType,
        P parameter) {

    public DataSourceSelection {
        if (statementId == null || statementId.isBlank()) {
            throw new IllegalArgumentException("statementId must not be blank");
        }
        Objects.requireNonNull(statementType, "statementType");
    }
}
