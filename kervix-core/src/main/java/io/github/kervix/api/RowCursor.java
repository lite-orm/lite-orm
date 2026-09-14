package io.github.kervix.api;

public interface RowCursor<T> {

    boolean next();

    T current();
}
