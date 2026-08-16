package org.liteorm.api;

public interface RowCursor<T> {

    boolean next();

    T current();
}
