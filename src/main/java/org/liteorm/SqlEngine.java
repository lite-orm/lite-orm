package org.liteorm;

/**
 * SQL执行引擎
 * 这是Core包的任务提交入口，由Compiler生成的MapperImpl调用
 * 
 * 设计原则：
 * - 这是唯一的外部接口
 * - 内部组织责任链完成SQL执行
 * - 返回原始结果，不涉及映射
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public interface SqlEngine {
    
    /**
     * 执行SQL任务
     * 这是Compiler生成代码调用Core的唯一入口
     * 
     * @param task SQL执行任务（包含SQL、参数、类型等）
     * @return SQL执行结果（原始数据，不包含映射）
     */
    SqlResult execute(SqlTask task);
    
    /**
     * 开始手动事务
     * @return 事务上下文
     */
    TransactionContext beginTransaction();
    
    /**
     * 提交事务
     * @param txContext 事务上下文
     */
    void commitTransaction(TransactionContext txContext);
    
    /**
     * 回滚事务
     * @param txContext 事务上下文
     */
    void rollbackTransaction(TransactionContext txContext);
}
