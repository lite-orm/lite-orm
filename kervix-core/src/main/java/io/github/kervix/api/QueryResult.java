package io.github.kervix.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Immutable result of a query execution.
 *
 * @param <T> mapped row type
 */
public final class QueryResult<T> {

    private final String statementId;
    private final List<T> rows;

    private QueryResult(String statementId, List<T> rows) {
        this.statementId = statementId;
        this.rows = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(rows, "rows")));
    }

    /** Creates a query result without statement context for missing-row diagnostics. */
    public static <T> QueryResult<T> of(List<T> rows) {
        return new QueryResult<>(null, rows);
    }

    /** Creates a query result with statement context for missing-row diagnostics. */
    public static <T> QueryResult<T> of(String statementId, List<T> rows) {
        return new QueryResult<>(statementId, rows);
    }

    static <T> QueryResult<T> from(SqlResult<T> result, String statementId) {
        Objects.requireNonNull(result, "result");
        if (result.resultKind() != SqlResult.ResultKind.QUERY) {
            throw new IllegalArgumentException("SQL result is not a query result");
        }
        return new QueryResult<>(statementId, result.getQueryResults());
    }

    /** Returns the immutable, never-null rows returned by the query. */
    public List<T> rows() {
        return rows;
    }

    /** Returns the only row, or {@code null} when no row exists. */
    public T oneOrNull() {
        requireAtMostOne();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** Returns the only row as an Optional, or empty when no row exists. */
    public Optional<T> optional() {
        return Optional.ofNullable(oneOrNull());
    }

    /** Returns the only row, or throws a mapping failure when no row exists. */
    public T required() {
        if (rows.isEmpty()) {
            throw new MappingException(
                "No row returned for " + statementId,
                statementId,
                null,
                null,
                null,
                null);
        }
        requireAtMostOne();
        return rows.getFirst();
    }

    private void requireAtMostOne() {
        if (rows.size() > 1) {
            throw new NonUniqueResultException(statementId, rows.size());
        }
    }
}
