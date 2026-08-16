package org.liteorm.spring.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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
        private String beanNamePrefix = "";

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

        public String getBeanNamePrefix() {
            return beanNamePrefix;
        }

        public void setBeanNamePrefix(String beanNamePrefix) {
            this.beanNamePrefix = beanNamePrefix;
        }
    }
}
