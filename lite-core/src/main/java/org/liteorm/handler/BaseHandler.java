package org.liteorm.handler;

import java.util.List;

/**
 * @author qingbozhang
 * @since 2024/10/26 19:28
 */
public interface BaseHandler {

    <T> List<T> selectList(ChainContext<T> context) throws Exception;

    default <T> T selectSingle(ChainContext<T> context) throws Exception {
        return selectList(context).get(0);
    }

    BaseHandler getNext();

    void setNext(BaseHandler next);
}
