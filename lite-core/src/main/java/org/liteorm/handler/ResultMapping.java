package org.liteorm.handler;

import org.liteorm.handler.resolver.BaseTypeResolver;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

/**
 * @author qingbozhang
 * @since 2024/10/27 21:12
 */
public interface ResultMapping {
    <T> T mapRow(ResultSet resultSet, ResultSetMetaData metaData,
                 Class<T> type, BaseTypeResolver<?> baseTypeResolver) throws Exception;
}
