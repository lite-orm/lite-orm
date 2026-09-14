package io.github.kervix.api;

@FunctionalInterface
public interface SqlProvider<P> {

    BoundSql provide(P parameter);
}
