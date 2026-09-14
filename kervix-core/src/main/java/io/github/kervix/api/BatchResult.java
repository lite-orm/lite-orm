package io.github.kervix.api;

import java.util.Objects;

/**
 * Immutable result of one JDBC batch execution.
 */
public final class BatchResult {

    private final String statementId;
    private final int[] counts;

    private BatchResult(String statementId, int[] counts) {
        this.statementId = statementId;
        this.counts = Objects.requireNonNull(counts, "counts").clone();
    }

    public static BatchResult of(String statementId, int[] counts) {
        return new BatchResult(Objects.requireNonNull(statementId, "statementId"), counts);
    }

    static BatchResult from(SqlResult<?> result, String statementId) {
        Objects.requireNonNull(result, "result");
        if (result.resultKind() != SqlResult.ResultKind.BATCH) {
            throw new IllegalArgumentException("SQL result is not a batch result");
        }
        return new BatchResult(statementId, result.getBatchUpdateCounts());
    }

    public String statementId() {
        return statementId;
    }

    public int[] counts() {
        return counts.clone();
    }
}
