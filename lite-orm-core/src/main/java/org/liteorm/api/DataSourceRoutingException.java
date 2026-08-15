package org.liteorm.api;

/**
 * Reports invalid or unavailable DataSource selection without exposing Mapper arguments.
 */
public final class DataSourceRoutingException extends LiteOrmException {

    private final String statementId;
    private final String dataSourceKey;

    public DataSourceRoutingException(String statementId, String dataSourceKey, String message) {
        super(buildMessage(statementId, dataSourceKey, message));
        this.statementId = requireText(statementId, "statementId");
        this.dataSourceKey = requireText(dataSourceKey, "dataSourceKey");
    }

    public String getStatementId() {
        return statementId;
    }

    public String getDataSourceKey() {
        return dataSourceKey;
    }

    private static String buildMessage(String statementId, String dataSourceKey, String message) {
        return requireText(message, "message")
            + " [statementId=" + requireText(statementId, "statementId")
            + ", dataSourceKey=" + requireText(dataSourceKey, "dataSourceKey") + ']';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
