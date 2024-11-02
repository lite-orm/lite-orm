package org.liteorm.handler;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @author 张庆波
 * @since 创建于 2024/10/26 20:06
 */
@Slf4j
public class HandlerChain {

    private final BaseHandler HEAD = new AbstractBaseHandler() {
        @Override
        public <T> List<T> selectList(ChainContext<T> context) throws Exception {
            log.debug("head");
            return getNext().selectList(context);
        }
    };

    private final BaseHandler TAIL = new AbstractBaseHandler() {
        @Override
        public <T> List<T> selectList(ChainContext<T> context) throws Exception {
            log.debug("tail");
            // TODO
            return (List<T>) context.getResult();
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

    public <T> List<T> execute(ChainContext<T> context) throws Exception {
        return HEAD.selectList(context);
    }

    public <T> T executeSingle(ChainContext<T> context) throws Exception {
        return HEAD.selectSingle(context);
    }
}
