package org.liteorm.handler.resolver;

import java.sql.ResultSet;

/**
 * @author qingbozhang
 * @since 2024/11/2 21:58
 */
public class IntegerTypeResolver implements BaseTypeResolver<Integer> {

    @Override
    public Integer getResult(ResultSet resultSet, int index) throws Exception {
        int result = resultSet.getInt(index);
        return result == 0 && resultSet.wasNull() ? null : result;
    }

    @Override
    public Class<Integer> getResolvedType() {
        return Integer.class;
    }
}
