package io.github.lynxus.api;

import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds one dynamic SQL invocation while keeping SQL placeholders and parameter routing aligned.
 *
 * <p>This mutable builder is intended for generated Mapper code. It is confined to one Mapper
 * invocation and is not thread-safe. {@link #build()} returns an immutable {@link BoundSql}.</p>
 */
public final class BoundSqlBuilder {

    private static final char PARAMETER_MARKER = '\uE000';
    private static final char LITERAL_MARKER_CODE = 'L';
    private static final char PARAMETER_MARKER_CODE = 'P';
    private static final String PARAMETER_TOKEN = "" + PARAMETER_MARKER + PARAMETER_MARKER_CODE;

    private final String statementId;
    private final StringBuilder sql = new StringBuilder();
    private final List<BoundParameter<?>> parameters = new ArrayList<>();

    private BoundSqlBuilder(String statementId) {
        this.statementId = Objects.requireNonNull(statementId, "statementId");
        if (statementId.isBlank()) {
            throw new IllegalArgumentException("statementId must not be blank");
        }
    }

    /**
     * Creates an empty builder whose failures include the owning Mapper statement ID.
     *
     * @param statementId non-null, non-blank owning Mapper statement ID
     */
    public static BoundSqlBuilder create(String statementId) {
        return new BoundSqlBuilder(statementId);
    }

    /** Creates an empty child fragment carrying the same statement context. */
    public BoundSqlBuilder fragment() {
        return new BoundSqlBuilder(statementId);
    }

    /**
     * Appends a SQL fragment with deterministic separator handling.
     *
     * <p>Null and blank fragments are ignored. Question marks in fragments remain ordinary SQL
     * text; generated parameter placeholders are emitted only through
     * {@link #parameter(Object, ParameterBinder, Class, JDBCType)}.</p>
     */
    public BoundSqlBuilder append(String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return this;
        }
        appendEncodedFragment(encodeSqlText(fragment));
        return this;
    }

    private void appendEncodedFragment(String fragment) {
        String normalized = fragment.trim();
        if (!sql.isEmpty() && needsSeparator(sql.charAt(sql.length() - 1), normalized.charAt(0))) {
            sql.append(' ');
        }
        sql.append(normalized);
    }

    /**
     * Appends one placeholder and its complete routing metadata as one atomic operation.
     *
     * <p>The binder, Java type, and JDBC type may be null. When both the binder and Java type are
     * null, direct JDBC {@code setObject} binding is installed so compile-time-unknown values remain
     * bindable. A custom binder is retained even when the value is null.</p>
     */
    public BoundSqlBuilder parameter(
            Object value, ParameterBinder<?> binder, Class<?> javaType, JDBCType jdbcType) {
        BoundParameter<?> parameter = BoundSql.generatedParameter(value, binder, javaType, jdbcType);
        appendEncodedFragment(PARAMETER_TOKEN);
        parameters.add(parameter);
        return this;
    }

    /**
     * Appends a non-empty same-statement child fragment as a normalized SQL {@code WHERE} clause.
     */
    public BoundSqlBuilder where(BoundSqlBuilder clause) {
        BoundSqlBuilder requiredClause = requireCompatible(clause);
        String normalized = requiredClause.sql.toString().trim()
            .replaceFirst("(?i)^(AND|OR)\\s+", "")
            .trim();
        if (!normalized.isBlank()) {
            appendEncodedFragment("WHERE");
            appendEncodedFragment(normalized);
            parameters.addAll(requiredClause.parameters);
        }
        return this;
    }

    /**
     * Appends a same-statement child as a normalized SQL {@code SET} clause, or fails when it has
     * no assignment.
     */
    public BoundSqlBuilder set(BoundSqlBuilder clause) {
        BoundSqlBuilder requiredClause = requireCompatible(clause);
        String normalized = requiredClause.sql.toString().trim();
        while (normalized.endsWith(",")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        if (normalized.isBlank()) {
            throw ConfigurationException.forStatement(
                "Dynamic <set> produced no assignments", statementId);
        }
        appendEncodedFragment("SET");
        appendEncodedFragment(normalized);
        parameters.addAll(requiredClause.parameters);
        return this;
    }

    /**
     * Appends a non-empty same-statement child after applying compile-time {@code trim}
     * configuration.
     *
     * <p>Null or blank prefixes, suffixes, and override lists are ignored. Generated parameter
     * placeholders use a collision-free internal encoding while trimming, so SQL text cannot add
     * or remove their routing entries.</p>
     */
    public BoundSqlBuilder trim(
            BoundSqlBuilder clause,
            String prefix,
            String suffix,
            String prefixOverrides,
            String suffixOverrides) {
        BoundSqlBuilder requiredClause = requireCompatible(clause);
        String normalized = requiredClause.sql.toString().trim();
        normalized = stripOverrides(normalized, encodeSqlText(prefixOverrides), true);
        normalized = stripOverrides(normalized, encodeSqlText(suffixOverrides), false);
        if (!normalized.isBlank()) {
            if (prefix != null && !prefix.isBlank()) {
                appendEncodedFragment(encodeSqlText(prefix));
            }
            appendEncodedFragment(normalized);
            if (suffix != null && !suffix.isBlank()) {
                appendEncodedFragment(encodeSqlText(suffix));
            }
            parameters.addAll(requiredClause.parameters);
        }
        return this;
    }

    /**
     * Produces immutable SQL and parameter data for one definition binding.
     *
     * @throws ConfigurationException when no non-blank SQL was produced
     */
    public BoundSql build() {
        if (sql.toString().isBlank()) {
            throw ConfigurationException.forStatement(
                "Dynamic SQL produced blank SQL", statementId);
        }
        return new BoundSql(renderSql(), parameters);
    }

    private boolean needsSeparator(char previous, char next) {
        return !Character.isWhitespace(previous)
            && !Character.isWhitespace(next)
            && previous != '('
            && next != ')'
            && next != ','
            && previous != ',';
    }

    private BoundSqlBuilder requireCompatible(BoundSqlBuilder fragment) {
        if (fragment == null) {
            throw ConfigurationException.forStatement("SQL fragment must not be null", statementId);
        }
        if (fragment == this) {
            throw ConfigurationException.forStatement(
                "SQL builder cannot compose itself as a fragment", statementId);
        }
        if (!statementId.equals(fragment.statementId)) {
            throw ConfigurationException.forStatement(
                "SQL fragment belongs to a different statement", statementId);
        }
        return fragment;
    }

    private String encodeSqlText(String fragment) {
        if (fragment == null || fragment.indexOf(PARAMETER_MARKER) < 0) {
            return fragment;
        }
        return fragment.replace(
            String.valueOf(PARAMETER_MARKER),
            "" + PARAMETER_MARKER + LITERAL_MARKER_CODE);
    }

    private String renderSql() {
        StringBuilder rendered = new StringBuilder(sql.length());
        for (int index = 0; index < sql.length(); index++) {
            char character = sql.charAt(index);
            if (character != PARAMETER_MARKER) {
                rendered.append(character);
                continue;
            }
            if (++index >= sql.length()) {
                throw ConfigurationException.forStatement(
                    "SQL contains an incomplete internal parameter marker", statementId);
            }
            char markerCode = sql.charAt(index);
            if (markerCode == LITERAL_MARKER_CODE) {
                rendered.append(PARAMETER_MARKER);
            } else if (markerCode == PARAMETER_MARKER_CODE) {
                rendered.append('?');
            } else {
                throw ConfigurationException.forStatement(
                    "SQL contains an invalid internal parameter marker", statementId);
            }
        }
        return rendered.toString();
    }

    private String stripOverrides(String clause, String overrides, boolean prefix) {
        if (overrides == null || overrides.isBlank()) {
            return clause.trim();
        }
        String normalized = clause.trim();
        for (String token : overrides.split("\\|")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (prefix
                    && normalized.regionMatches(true, 0, trimmed, 0, trimmed.length())
                    && isTokenBoundary(normalized, trimmed.length())) {
                normalized = normalized.substring(trimmed.length()).trim();
            }
            int suffixStart = normalized.length() - trimmed.length();
            if (!prefix && suffixStart >= 0
                    && isTokenBoundary(normalized, suffixStart)
                    && normalized.regionMatches(
                        true, suffixStart, trimmed, 0, trimmed.length())) {
                normalized = normalized.substring(0, suffixStart).trim();
            }
        }
        return normalized;
    }

    private boolean isTokenBoundary(String encodedSql, int index) {
        return index == 0 || encodedSql.charAt(index - 1) != PARAMETER_MARKER;
    }
}
