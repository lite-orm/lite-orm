package org.liteorm.handler;

import lombok.extern.slf4j.Slf4j;

/**
 * @author 张庆波
 * @since 创建于 2024/10/26 20:06
 */
@Slf4j
public class HandlerChain {

    private final BaseHandler HEAD = new AbstractBaseHandler() {
        @Override
        public void handle(ChainContext context) throws Exception {
            log.debug("head");
            getNext().handle(context);
        }
    };

    private final BaseHandler TAIL = new AbstractBaseHandler() {
        @Override
        public void handle(ChainContext context) throws Exception {
            log.debug("tail");
        }
    };

    public HandlerChain(BaseHandler... handlers) {
        assert handlers != null;
        BaseHandler tmp = HEAD;
        for (BaseHandler handler : handlers) {
            tmp.setNext(handler);
            tmp = handler;
        }
        tmp.setNext(TAIL);
    }

    public void execute(ChainContext context) throws Exception {
        HEAD.handle(context);
    }
}
