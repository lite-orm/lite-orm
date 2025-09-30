package org.liteorm.api;

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
    
    private final String sql;                           // SQL模板（可能包含#{param}）
    private final Object[] parameters;                  // 参数值数组（兼容模式）
    private final java.util.Map<String, Object> parameterMap; // 参数映射（#{param}模式）
    private final SqlType sqlType;                      // SQL类型
    private final boolean requiresTransaction;          // 是否需要事务
    
    /**
     * 构造函数 - 使用Object[]参数（兼容模式）
     */
    public SqlTask(String sql, Object[] parameters, SqlType sqlType, boolean requiresTransaction) {
        this.sql = sql;
        this.parameters = parameters;
        this.parameterMap = null;
        this.sqlType = sqlType;
        this.requiresTransaction = requiresTransaction;
    }
    
    /**
     * 构造函数 - 使用Map参数（#{param}模式）
     */
    public SqlTask(String sql, java.util.Map<String, Object> parameterMap, SqlType sqlType, boolean requiresTransaction) {
        this.sql = sql;
        this.parameters = null;
        this.parameterMap = parameterMap;
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
    
    public java.util.Map<String, Object> getParameterMap() {
        return parameterMap;
    }
    
    /**
     * 检查是否使用Map参数模式
     */
    public boolean usesParameterMap() {
        return parameterMap != null;
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
