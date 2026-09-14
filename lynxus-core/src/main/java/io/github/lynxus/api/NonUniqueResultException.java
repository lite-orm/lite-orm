package io.github.lynxus.api;

/**
 * Thrown when a Mapper method declared as a single result receives multiple rows.
 */
public final class NonUniqueResultException extends LynxusException {

    private final String statementId;
    private final int rowCount;

    public NonUniqueResultException(String statementId, int rowCount) {
        super("Expected at most one row [statementId=" + statementId
            + ", phase=" + ExecutionPhase.MAPPING + ", rowCount=" + rowCount + "]");
        this.statementId = statementId;
        this.rowCount = rowCount;
    }

    public String getStatementId() {
        return statementId;
    }

    public int getRowCount() {
        return rowCount;
    }

    public ExecutionPhase getPhase() {
        return ExecutionPhase.MAPPING;
    }
}
