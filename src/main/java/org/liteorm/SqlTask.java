package org.liteorm;

/**
 * SQL执行任务
 * 这是Compiler生成的MapperImpl提交给Core的任务封装
 * 
 * 设计原则：
 * - 包含SQL执行所需的所有信息
 * - 不包含业务逻辑，只是数据容器
 * - 由Compiler生成的代码创建
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class SqlTask {
    
    private final String sql;                    // 硬编码的SQL
    private final Object[] parameters;          // 参数值
    private final SqlType sqlType;              // SQL类型
    private final boolean requiresTransaction;   // 是否需要事务
    
    public SqlTask(String sql, Object[] parameters, SqlType sqlType, boolean requiresTransaction) {
        this.sql = sql;
        this.parameters = parameters;
        this.sqlType = sqlType;
        this.requiresTransaction = requiresTransaction;
    }
    
    // Getters
    public String getSql() {
        return sql;
    }
    
    public Object[] getParameters() {
        return parameters;
    }
    
    public SqlType getSqlType() {
        return sqlType;
    }
    
    public boolean requiresTransaction() {
        return requiresTransaction;
    }
    
    /**
     * SQL类型枚举
     */
    public enum SqlType {
        SELECT,    // 查询
        INSERT,    // 插入
        UPDATE,    // 更新
        DELETE,    // 删除
        BATCH      // 批量操作
    }
}
