package org.liteorm.spring.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LiteORM配置属性
 * 
 * @author lite-orm
 * @since 2024/11/15
 */
@ConfigurationProperties(prefix = "lite-orm")
public class LiteOrmProperties {
    
    /**
     * 是否启用LiteORM
     */
    private boolean enabled = true;
    
    /**
     * 慢查询阈值（毫秒）
     */
    private long slowQueryThreshold = 1000L;
    
    /**
     * 是否启用慢查询监控
     */
    private boolean slowQueryMonitoring = true;
    
    /**
     * 是否启用SQL日志
     */
    private boolean sqlLogging = true;
    
    /**
     * 是否记录参数值
     */
    private boolean logParameters = true;
    
    /**
     * 是否启用SQL审计
     */
    private boolean auditEnabled = false;
    
    /**
     * 审计是否异步
     */
    private boolean auditAsync = true;
    
    /**
     * Mapper扫描包路径
     */
    private String[] mapperPackages = {};

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

    public String[] getMapperPackages() {
        return mapperPackages;
    }

    public void setMapperPackages(String[] mapperPackages) {
        this.mapperPackages = mapperPackages;
    }
}

