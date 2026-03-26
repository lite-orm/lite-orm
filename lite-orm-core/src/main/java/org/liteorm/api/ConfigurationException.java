package org.liteorm.api;

/**
 * 配置异常
 * 
 * LiteORM配置错误时抛出的异常
 * 
 * @author lite-orm
 * @since 2024/11/15
 */
public class ConfigurationException extends LiteOrmException {
    
    private final String configKey;
    private final String configValue;
    
    public ConfigurationException(String message) {
        this(message, null, null);
    }
    
    public ConfigurationException(String message, String configKey, String configValue) {
        super(buildMessage(message, configKey, configValue));
        this.configKey = configKey;
        this.configValue = configValue;
    }
    
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
        this.configKey = null;
        this.configValue = null;
    }
    
    private static String buildMessage(String message, String configKey, String configValue) {
        if (configKey == null) {
            return message;
        }
        StringBuilder sb = new StringBuilder(message);
        sb.append("\nConfig Key: ").append(configKey);
        if (configValue != null) {
            sb.append("\nConfig Value: ").append(configValue);
        }
        return sb.toString();
    }
    
    public String getConfigKey() {
        return configKey;
    }
    
    public String getConfigValue() {
        return configValue;
    }
}

