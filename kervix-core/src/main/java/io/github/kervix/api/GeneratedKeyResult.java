package io.github.kervix.api;

import java.util.Objects;

/**
 * Immutable result of an insert that returned one generated key.
 *
 * @param <K> generated key type
 */
public final class GeneratedKeyResult<K> {

    private final String statementId;
    private final int affectedRows;
    private final K key;

    private GeneratedKeyResult(String statementId, int affectedRows, K key) {
        this.statementId = statementId;
        this.affectedRows = affectedRows;
        this.key = key;
    }

    public static <K> GeneratedKeyResult<K> of(String statementId, int affectedRows, K key) {
        return new GeneratedKeyResult<>(
            Objects.requireNonNull(statementId, "statementId"), affectedRows, key);
    }

    @SuppressWarnings("unchecked")
    static <K> GeneratedKeyResult<K> from(SqlResult<?> result, String statementId) {
        Objects.requireNonNull(result, "result");
        if (result.resultKind() != SqlResult.ResultKind.GENERATED_KEY) {
            throw new IllegalArgumentException("SQL result is not a generated-key result");
        }
        return new GeneratedKeyResult<>(
            statementId, result.getUpdateCount(), (K) result.getGeneratedKey());
    }

    public String statementId() {
        return statementId;
    }

    public int affectedRows() {
        return affectedRows;
    }

    public K key() {
        return key;
    }
}
