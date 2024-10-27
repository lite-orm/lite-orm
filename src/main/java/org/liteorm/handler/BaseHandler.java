package org.liteorm.handler;

/**
 * @author 张庆波
 * @since 创建于 2024/10/26 19:28
 */
public interface BaseHandler {

    void handle(ChainContext<?> context) throws Exception;

    BaseHandler getNext();

    void setNext(BaseHandler next);
}
