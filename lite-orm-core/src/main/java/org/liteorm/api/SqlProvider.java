package org.liteorm.api;

@FunctionalInterface
public interface SqlProvider<P> {

    BoundSql provide(P parameter);
}
