package org.liteorm.runtime;

import org.liteorm.ExecutionContext;
import org.liteorm.api.ExecutionPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 慢查询监控处理器
 * 
 * 功能：
 * 1. 记录SQL执行开始时间
 * 2. 计算执行耗时
 * 3. 超过阈值时记录WARNING
 * 4. 可配置阈值（默认1000ms）
 * 
 * 使用场景：
 * - 生产环境性能监控
 * - 慢查询识别和优化
 * - 系统性能分析
 * 
 * @author lite-orm
 * @since 2024/11/15
 */
public class SlowQueryMonitorProcessor implements SqlProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(SlowQueryMonitorProcessor.class);
    private static final String START_TIME_KEY = "slowquery.startTime";
    private static final String MONITORED_KEY = "slowquery.monitored";
    
    private final long thresholdMillis;
    
    /**
     * 默认构造器 - 1000ms阈值
     */
    public SlowQueryMonitorProcessor() {
        this(1000);
    }
    
    /**
     * 自定义阈值构造器
     * 
     * @param thresholdMillis 慢查询阈值（毫秒）
     */
    public SlowQueryMonitorProcessor(long thresholdMillis) {
        this.thresholdMillis = thresholdMillis;
    }
    
    @Override
    public void process(ExecutionPlan plan, ExecutionContext context) {
        // 在SQL执行前记录开始时间
        if (!context.hasAttribute(MONITORED_KEY)) {
            recordStartTime(context);
        }
        
        // 在SQL执行后检查执行时间
        if (context.getQueryResults() != null || context.getUpdateCount() >= 0) {
            checkSlowQuery(plan, context);
        }
    }
    
    /**
     * 记录开始时间
     */
    private void recordStartTime(ExecutionContext context) {
        context.setAttribute(START_TIME_KEY, System.currentTimeMillis());
    }
    
    /**
     * 检查是否为慢查询
     */
    private void checkSlowQuery(ExecutionPlan plan, ExecutionContext context) {
        Long startTime = (Long) context.getAttribute(START_TIME_KEY);
        if (startTime == null) {
            return;
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        if (duration >= thresholdMillis) {
            // 记录慢查询警告
            logger.warn("\n" +
                "╔══════════════════════════════════════════════════════════╗\n" +
                "║  ⚠️  SLOW QUERY DETECTED                                 ║\n" +
                "╠══════════════════════════════════════════════════════════╣\n" +
                "║  Duration: {} ms (threshold: {} ms)                      ║\n" +
                "║  SQL Type: {}                                            ║\n" +
                "║  SQL: {}                                                 ║\n" +
                "╚══════════════════════════════════════════════════════════╝",
                duration, thresholdMillis, plan.getStatementType(), plan.getSql());
            
            // 标记为已监控，避免重复报警
            context.setAttribute(MONITORED_KEY, true);
            context.setAttribute("slowquery.duration", duration);
        }
    }
    
    /**
     * 获取配置的阈值
     */
    public long getThresholdMillis() {
        return thresholdMillis;
    }
}
