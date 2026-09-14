package io.github.lynxus.api;

@FunctionalInterface
public interface SqlProvider<P> {

    BoundSql provide(P parameter);
}
