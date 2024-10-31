package org.liteorm.handler;

import java.util.List;

/**
 * @author 张庆波
 * @since 创建于 2024/10/26 19:28
 */
public interface BaseHandler {

    <T> List<T> handle(ChainContext<T> context) throws Exception;

    BaseHandler getNext();

    void setNext(BaseHandler next);
}
