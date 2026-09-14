package io.github.lynxus.api;

import java.util.Objects;

/**
 * Immutable result of an insert, update, or delete that does not return a generated key.
 */
public final class UpdateResult {

    private final String statementId;
    private final int count;

    private UpdateResult(String statementId, int count) {
        this.statementId = statementId;
        this.count = count;
    }

    public static UpdateResult of(String statementId, int count) {
        return new UpdateResult(Objects.requireNonNull(statementId, "statementId"), count);
    }

    static UpdateResult from(SqlResult<?> result, String statementId) {
        Objects.requireNonNull(result, "result");
        if (result.resultKind() != SqlResult.ResultKind.UPDATE) {
            throw new IllegalArgumentException("SQL result is not an update result");
        }
        return new UpdateResult(statementId, result.getUpdateCount());
    }

    public String statementId() {
        return statementId;
    }

    public int count() {
        return count;
    }
}
