package org.liteorm.handler;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

/**
 * @author 张庆波
 * @since 创建于 2024/10/27 21:13
 */
@Slf4j
public class ReflectResultMapping implements ResultMapping {
    @Override
    public <T> T mapRow(ResultSet resultSet, ResultSetMetaData metaData, Class<T> type) throws Exception {
        T instance = type.getDeclaredConstructor().newInstance();
        int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnLabel(i);
            Object columnValue = resultSet.getObject(i);
            // 将列名映射到实体类字段
            try {
                Field field = type.getDeclaredField(columnName);
                field.setAccessible(true);
                field.set(instance, columnValue);
            } catch (NoSuchFieldException ignored) {
                // 如果字段不存在，跳过
                log.error("not found field {}", columnName);
            }
        }
        return instance;
    }
}
