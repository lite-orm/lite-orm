package io.github.lynxus.api;

/**
 * Immutable JDBC statement controls for one execution.
 */
public record StatementOptions(Integer timeoutSeconds, Integer fetchSize, Integer maxRows) {

    public StatementOptions {
        requirePositive(timeoutSeconds, "timeoutSeconds");
        requirePositive(fetchSize, "fetchSize");
        if (maxRows != null && maxRows < 0) {
            throw new IllegalArgumentException("maxRows must be non-negative");
        }
    }

    public static StatementOptions defaults() {
        return new StatementOptions(null, null, null);
    }

    private static void requirePositive(Integer value, String name) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
