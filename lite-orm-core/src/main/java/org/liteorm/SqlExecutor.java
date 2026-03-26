package org.liteorm;

import java.sql.Connection;
import java.util.List;

/**
 * SQL执行器核心接口
 * 这是运行时的最小抽象，只负责纯粹的SQL执行
 * 
 * 设计原则：
 * - 不包含任何反射逻辑
 * - 不包含任何映射逻辑  
 * - 不包含复杂的上下文
 * - 就是简单的JDBC封装
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public interface SqlExecutor {

    /**
     * 执行查询SQL，返回ResultSet的原始数据
     * 
     * @param connection 数据库连接
     * @param sql SQL语句（编译期确定）
     * @param params 参数（运行时传入）
     * @return 查询结果的原始数据（List<Object[]>）
     */
    List<Object[]> executeQuery(Connection connection, String sql, Object... params);

    /**
     * 执行更新SQL（INSERT/UPDATE/DELETE）
     * 
     * @param connection 数据库连接
     * @param sql SQL语句（编译期确定）
     * @param params 参数（运行时传入）
     * @return 影响的行数
     */
    int executeUpdate(Connection connection, String sql, Object... params);

    /**
     * 执行批量更新
     * 
     * @param connection 数据库连接
     * @param sql SQL语句（编译期确定）
     * @param batchParams 批量参数
     * @return 每个语句影响的行数
     */
    int[] executeBatch(Connection connection, String sql, List<Object[]> batchParams);
}
