package io.github.lynxus.api;

@FunctionalInterface
public interface CursorCallback<T, R> {

    R consume(RowCursor<T> cursor);
}
