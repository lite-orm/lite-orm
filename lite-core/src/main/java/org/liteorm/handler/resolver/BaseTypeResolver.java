package org.liteorm.handler.resolver;

import java.sql.ResultSet;

/**
 * @author qingbozhang
 * @since 创建于 2024/11/2 21:56
 */
public interface BaseTypeResolver<T> {
    T getResult(ResultSet resultSet, int index) throws Exception;

    Class<T> getResolvedType();
}
