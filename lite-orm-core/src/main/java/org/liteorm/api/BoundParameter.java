package org.liteorm.api;

public record BoundParameter<T>(T value, ParameterBinder<? super T> binder) {

    public static <T> BoundParameter<T> of(T value) {
        return new BoundParameter<>(value, null);
    }

    public static <T> BoundParameter<T> bound(T value, ParameterBinder<? super T> binder) {
        if (binder == null) {
            throw new ConfigurationException("Bound parameter binder must not be null");
        }
        return new BoundParameter<>(value, binder);
    }
}
