package io.github.kervix.api;

@FunctionalInterface
public interface CursorCallback<T, R> {

    R consume(RowCursor<T> cursor);
}
