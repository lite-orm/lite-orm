package org.liteorm.api;

public class ConfigurationException extends LiteOrmException {

    private final String configKey;
    private final String statementId;

    public ConfigurationException(String message) {
        this(message, null, null, null);
    }

    public ConfigurationException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    public static ConfigurationException forConfigKey(String message, String configKey) {
        return new ConfigurationException(message, configKey, null, null);
    }

    public static ConfigurationException forStatement(String message, String statementId) {
        return new ConfigurationException(message, null, statementId, null);
    }

    private ConfigurationException(
            String message, String configKey, String statementId, Throwable cause) {
        super(buildMessage(message, configKey, statementId), cause);
        this.configKey = configKey;
        this.statementId = statementId;
    }

    public ExecutionPhase getPhase() {
        return ExecutionPhase.CONFIGURATION;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getStatementId() {
        return statementId;
    }

    private static String buildMessage(String message, String configKey, String statementId) {
        StringBuilder result = new StringBuilder(message)
            .append(" [phase=").append(ExecutionPhase.CONFIGURATION);
        if (configKey != null) {
            result.append(", configKey=").append(configKey);
        }
        if (statementId != null) {
            result.append(", statementId=").append(statementId);
        }
        return result.append(']').toString();
    }
}
