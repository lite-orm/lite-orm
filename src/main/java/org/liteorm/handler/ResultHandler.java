package org.liteorm.handler;

import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author 张庆波
 * @since 创建于 2024/10/26 20:00
 */
@Slf4j
public class ResultHandler extends AbstractBaseHandler {

    @Override
    public void handle(ChainContext context) throws Exception {
        try (ResultSet resultSet = context.getResultSet()) {
            if (resultSet != null) {
                context.setResult(processResultSet(resultSet));
            }
            getNext().handle(context);
        }
    }

    private static List<Map<String, Object>> processResultSet(ResultSet resultSet) throws SQLException {
        List<Map<String, Object>> resultList = new LinkedList<>();
        int columnCount = resultSet.getMetaData().getColumnCount();
        while (resultSet.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(resultSet.getMetaData().getColumnName(i), resultSet.getObject(i));
            }
            resultList.add(row);
        }
        return resultList;
    }
}
