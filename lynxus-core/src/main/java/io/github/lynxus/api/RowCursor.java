package io.github.lynxus.api;

public interface RowCursor<T> {

    boolean next();

    T current();
}
