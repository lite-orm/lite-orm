package io.github.lynxus.api;

/**
 * Describes how far one JDBC statement execution progressed without claiming transaction completion.
 */
public enum JdbcExecutionState {
    NOT_EXECUTED,
    OUTCOME_UNKNOWN,
    EXECUTED
}
