package org.liteorm.handler;

import lombok.extern.slf4j.Slf4j;
import org.liteorm.handler.resolver.BaseTypeResolver;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

/**
 * @author qingbozhang
 * @since 创建于 2024/10/27 21:13
 */
@Slf4j
public class ReflectResultMapping implements ResultMapping {

    @Override
    @SuppressWarnings("unchecked")
    public <T> T mapRow(ResultSet resultSet, ResultSetMetaData metaData, Class<T> type,
                        BaseTypeResolver<?> baseTypeResolver) throws Exception {
        if (type.isPrimitive() || isWrapperType(type)) {
            if (baseTypeResolver != null) {
                return (T) baseTypeResolver.getResult(resultSet, 1);
            }
            return type.cast(resultSet.getObject(1));
        }

        int columnCount = metaData.getColumnCount();
        T instance = type.getDeclaredConstructor().newInstance();
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


    private boolean isWrapperType(Class<?> type) {
        return type == Boolean.class || type == Character.class ||
                type == Byte.class || type == Short.class ||
                type == Integer.class || type == Long.class ||
                type == Float.class || type == Double.class;
    }


}
