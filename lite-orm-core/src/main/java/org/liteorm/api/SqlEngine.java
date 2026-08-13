package org.liteorm.api;

/**
 * SQL执行引擎 - 基于物理必需性的核心接口
 * 
 * 物理对应：
 * - 这是编译期生成代码调用运行时核心的唯一接口
 * - 内部组织责任链完成SQL的物理执行步骤
 * - 返回原始数据，不涉及业务映射
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public interface SqlEngine {
    
    /**
     * 执行标准执行计划 - 核心物理入口
     * 
     * @param plan SQL执行计划（包含SQL、参数、类型等）
     * @return SQL执行结果（原始数据，不包含映射）
     */
    SqlResult execute(ExecutionPlan plan);

    /**
     * 兼容旧的SqlTask调用入口。
     */
    default SqlResult execute(SqlTask task) {
        return execute((ExecutionPlan) task);
    }
    
}
