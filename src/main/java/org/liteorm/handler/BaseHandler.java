package org.liteorm.handler;

import java.util.List;

/**
 * @author 张庆波
 * @since 创建于 2024/10/26 19:28
 */
public interface BaseHandler {

    <T> List<T> selectList(ChainContext<T> context) throws Exception;

    default <T> T selectSingle(ChainContext<T> context) throws Exception {
        return selectList(context).get(0);
    }

    BaseHandler getNext();

    void setNext(BaseHandler next);
}
