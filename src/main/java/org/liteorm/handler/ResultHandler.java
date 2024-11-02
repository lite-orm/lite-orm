package org.liteorm.handler;

import lombok.extern.slf4j.Slf4j;
import org.liteorm.handler.resolver.BaseTypeResolver;
import org.liteorm.handler.resolver.IntegerTypeResolver;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * @author 张庆波
 * @since 创建于 2024/10/26 20:00
 */
@Slf4j
public class ResultHandler extends AbstractBaseHandler {

    private ResultMapping resultMapping;
    private Map<Class<?>, BaseTypeResolver<?>> typeResolverMap = new HashMap<>();

    public ResultHandler() {
        // SPI
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
        IntegerTypeResolver value = new IntegerTypeResolver();
        typeResolverMap.put(Integer.class, value);
        typeResolverMap.put(int.class, value);
    }

    @Override
    public <T> List<T> selectList(ChainContext<T> context) throws Exception {
        try (ResultSet resultSet = context.getResultSet()) {
            if (resultSet != null) {
                context.setResult(processResultSet(resultSet, context.getResultClazz()));
            }
            return getNext().selectList(context);
        }
    }

    private <T> List<T> processResultSet(ResultSet resultSet, Class<T> type) throws Exception {
        List<T> resultList = new LinkedList<>();
        ResultSetMetaData metaData = resultSet.getMetaData();
        BaseTypeResolver<?> baseTypeResolver = typeResolverMap.get(type);
        while (resultSet.next()) {
            T t = resultMapping.mapRow(resultSet, metaData, type, baseTypeResolver);
            resultList.add(t);
        }
        return resultList;
    }

//    private <T> T processSingleResult(ResultSet resultSet) throws Exception {
//
//    }
}
