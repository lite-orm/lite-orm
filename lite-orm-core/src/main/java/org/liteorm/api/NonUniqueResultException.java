package org.liteorm.api;

/**
 * Thrown when a Mapper method declared as a single result receives multiple rows.
 */
public final class NonUniqueResultException extends RuntimeException {

    private final String statementId;
    private final int rowCount;

    public NonUniqueResultException(String statementId, int rowCount) {
        super("Expected at most one row for " + statementId + " but found " + rowCount);
        this.statementId = statementId;
        this.rowCount = rowCount;
    }

    public String getStatementId() {
        return statementId;
    }

    public int getRowCount() {
        return rowCount;
    }
}
