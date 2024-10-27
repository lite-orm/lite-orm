package org.liteorm.handler;

/**
 * @author 张庆波
 * @since 创建于 2024/10/26 19:32
 */
public abstract class AbstractBaseHandler implements BaseHandler {

    private BaseHandler next;

    @Override
    public BaseHandler getNext() {
        return this.next;
    }

    @Override
    public void setNext(BaseHandler next) {
        this.next = next;
    }
}
