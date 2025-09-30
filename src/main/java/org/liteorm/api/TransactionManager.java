package org.liteorm.api;

/**
 * 事务管理器接口 - 基于物理必需性设计
 * 
 * 物理本质分析：
 * 1. 事务边界管理（物理必需：数据一致性）
 * 2. 连接复用管理（物理必需：同一事务同一连接）
 * 3. 异常回滚处理（物理必需：故障恢复）
 * 4. 外部集成支持（扩展必需：与框架集成）
 * 
 * 设计原则：
 * - 支持自管理事务（LiteORM控制）
 * - 支持外部管理事务（Spring等框架控制）
 * - 基于ThreadLocal保证线程安全
 * - 最小化API，只包含物理必需操作
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public interface TransactionManager {
    
    /**
     * 开始事务 - 物理必需：建立事务边界
     * 
     * @return 事务上下文（包含连接和状态）
     * @throws TransactionException 开始事务失败时抛出
     */
    TransactionContext begin() throws TransactionException;
    
    /**
     * 提交事务 - 物理必需：持久化更改
     * 
     * @param context 事务上下文
     * @throws TransactionException 提交失败时抛出
     */
    void commit(TransactionContext context) throws TransactionException;
    
    /**
     * 回滚事务 - 物理必需：撤销更改
     * 
     * @param context 事务上下文
     * @throws TransactionException 回滚失败时抛出
     */
    void rollback(TransactionContext context) throws TransactionException;
    
    /**
     * 获取当前事务上下文 - 物理必需：连接复用
     * 
     * @return 当前线程的事务上下文，如果没有则返回null
     */
    TransactionContext getCurrentTransaction();
    
    /**
     * 检查是否在事务中 - 物理必需：状态判断
     * 
     * @return true如果当前线程有活跃事务
     */
    boolean isInTransaction();
    
    /**
     * 执行事务性操作 - 便利方法：自动管理事务边界
     * 
     * @param operation 事务性操作
     * @param <T> 返回值类型
     * @return 操作结果
     * @throws TransactionException 事务执行失败时抛出
     */
    <T> T executeInTransaction(TransactionalOperation<T> operation) throws TransactionException;
    
    /**
     * 事务性操作函数式接口
     */
    @FunctionalInterface
    interface TransactionalOperation<T> {
        T execute(TransactionContext context) throws Exception;
    }
}

