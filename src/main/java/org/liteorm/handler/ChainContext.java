package org.liteorm.handler;

import lombok.Data;

import javax.sql.DataSource;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;

/**
 * @author 张庆波
 * @since 创建于 2024/10/26 19:31
 */
@Data
public class ChainContext<T> {
    private Connection connection;
    private DataSource dataSource;

    private String sql;
    private Object[] params;
    // 批量参数集合
    private List<Object[]> batchParams;
    private final Class<T> resultClazz;

    private Object result;
    private int updateCount;
    private ResultSet resultSet;
    // 是否开启事务标记
    private boolean transactionActive;
    private boolean autoCommit;
    private boolean batchMode;

    public ChainContext(Class<T> resultClazz) {
        this.resultClazz = resultClazz;
    }
}
