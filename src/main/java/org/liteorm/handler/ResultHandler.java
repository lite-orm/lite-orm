package org.liteorm.handler;

import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * @author 张庆波
 * @since 创建于 2024/10/26 20:00
 */
@Slf4j
public class ResultHandler extends AbstractBaseHandler {

    private ResultMapping resultMapping;

    public ResultHandler() {
        // 结果集映射开放SPI
        ServiceLoader<ResultMapping> loader = ServiceLoader.load(ResultMapping.class);
        for (ResultMapping resultMapping : loader) {
            if (resultMapping != null) {
                this.resultMapping = resultMapping;
            }
            if (this.resultMapping != null) {
                log.debug("loaded resultMapping: {}", resultMapping.getClass().getName());
                break;
            }
        }
    }

    @Override
    public void handle(ChainContext<?> context) throws Exception {
        try (ResultSet resultSet = context.getResultSet()) {
            if (resultSet != null) {
                context.setResult(processResultSet(resultSet, context.getResultClazz()));
            }
            getNext().handle(context);
        }
    }

    private <T> List<T> processResultSet(ResultSet resultSet, Class<T> type) throws Exception {
        List<T> resultList = new LinkedList<>();
        ResultSetMetaData metaData = resultSet.getMetaData();
        while (resultSet.next()) {
            T t = resultMapping.mapRow(resultSet, metaData, type);
            resultList.add(t);
        }
        return resultList;
    }

}
