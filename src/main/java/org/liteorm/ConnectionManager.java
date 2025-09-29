package org.liteorm;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 连接管理器
 * 负责数据库连接的获取和管理
 * 
 * 设计原则：
 * - 简单的连接管理
 * - 支持事务
 * - 支持连接池
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public interface ConnectionManager {

    /**
     * 获取数据库连接
     * 支持事务连接复用，这是唯一推荐的获取连接方式
     * 
     * @return 数据库连接
     */
    Connection getConnection();

    /**
     * 释放连接
     * 
     * @param connection 要释放的连接
     */
    void releaseConnection(Connection connection);

    /**
     * 开始事务
     * 
     * @return 事务连接
     */
    Connection beginTransaction();

    /**
     * 提交事务
     * 
     * @param connection 事务连接
     */
    void commitTransaction(Connection connection);

    /**
     * 回滚事务
     * 
     * @param connection 事务连接
     */
    void rollbackTransaction(Connection connection);

    /**
     * 设置数据源
     * 
     * @param dataSource 数据源
     */
    void setDataSource(DataSource dataSource);
}
