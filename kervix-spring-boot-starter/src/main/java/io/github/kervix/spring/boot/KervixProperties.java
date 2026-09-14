package io.github.kervix.spring.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Kervix Spring Boot configuration properties.
 * 
 * @author kervix
 * @since 2024/11/15
 */
@ConfigurationProperties(prefix = "kervix")
public class KervixProperties {
    
    /**
     * Whether Kervix auto-configuration is enabled.
     */
    private boolean enabled = true;
    
    /**
     * Slow-query threshold in milliseconds.
     */
    private long slowQueryThreshold = 1000L;
    
    /**
     * Whether slow-query monitoring is enabled.
     */
    private boolean slowQueryMonitoring = true;
    
    /**
     * Whether SQL logging is enabled.
     */
    private boolean sqlLogging = true;
    
    /**
     * Whether parameter values are logged.
     */
    private boolean logParameters = true;
    
    /**
     * Whether SQL auditing is enabled.
     */
    private boolean auditEnabled = false;
    
    /**
     * Whether auditing runs asynchronously.
     */
    private boolean auditAsync = true;
    
    private List<MapperBinding> mapperBindings = new ArrayList<>();

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getSlowQueryThreshold() {
        return slowQueryThreshold;
    }

    public void setSlowQueryThreshold(long slowQueryThreshold) {
        this.slowQueryThreshold = slowQueryThreshold;
    }

    public boolean isSlowQueryMonitoring() {
        return slowQueryMonitoring;
    }

    public void setSlowQueryMonitoring(boolean slowQueryMonitoring) {
        this.slowQueryMonitoring = slowQueryMonitoring;
    }

    public boolean isSqlLogging() {
        return sqlLogging;
    }

    public void setSqlLogging(boolean sqlLogging) {
        this.sqlLogging = sqlLogging;
    }

    public boolean isLogParameters() {
        return logParameters;
    }

    public void setLogParameters(boolean logParameters) {
        this.logParameters = logParameters;
    }

    public boolean isAuditEnabled() {
        return auditEnabled;
    }

    public void setAuditEnabled(boolean auditEnabled) {
        this.auditEnabled = auditEnabled;
    }

    public boolean isAuditAsync() {
        return auditAsync;
    }

    public void setAuditAsync(boolean auditAsync) {
        this.auditAsync = auditAsync;
    }

    public List<MapperBinding> getMapperBindings() {
        return mapperBindings;
    }

    public void setMapperBindings(List<MapperBinding> mapperBindings) {
        this.mapperBindings = mapperBindings;
    }

    public static class MapperBinding {

        private String packageName;
        private String dataSource;

        public String getPackageName() {
            return packageName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public String getDataSource() {
            return dataSource;
        }

        public void setDataSource(String dataSource) {
            this.dataSource = dataSource;
        }
    }
}
