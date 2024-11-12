package org.liteorm.handler;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.List;

/**
 * @author qingbozhang
 * @since 创建于 2024/10/26 20:06
 */
@Slf4j
public class HandlerChain {

    private static final BaseHandler HEAD = new AbstractBaseHandler() {
        @Override
        public <T> List<T> selectList(ChainContext<T> context) throws Exception {
            log.debug("head");
            return getNext().selectList(context);
        }
    };


    private static final BaseHandler TAIL = new AbstractBaseHandler() {
        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> selectList(ChainContext<T> context) throws Exception {
            log.debug("tail");
            // TODO
            return (List<T>) context.getResult();
        }
    };

    private static final HandlerChain INSTANCE = new HandlerChain();

    public static HandlerChain getDefaultInstance(DataSource dataSource) {
        ConnectionHandler connectionHandler = new ConnectionHandler(dataSource);
        PreparedStatementHandler statementHandler = new PreparedStatementHandler();
        ResultHandler resultHandler = new ResultHandler();
        TransactionHandler transactionHandler = new TransactionHandler();
        return assemblyChain(connectionHandler, transactionHandler, statementHandler, resultHandler);
    }

    public static HandlerChain assemblyChain(BaseHandler... handlers) {
        assert handlers != null;
        BaseHandler tmp = HEAD;
        for (BaseHandler handler : handlers) {
            tmp.setNext(handler);
            tmp = handler;
        }
        tmp.setNext(TAIL);
        return INSTANCE;
    }


    public <T> List<T> execute(ChainContext<T> context, Object... params) throws Exception {
        context.setParams(params);
        return HEAD.selectList(context);
    }

    public <T> T executeSingle(ChainContext<T> context, Object... params) throws Exception {
        context.setParams(params);
        return HEAD.selectSingle(context);
    }

    private HandlerChain() {
    }
}
