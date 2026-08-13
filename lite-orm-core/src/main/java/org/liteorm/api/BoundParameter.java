package org.liteorm.api;

public record BoundParameter(Object value) {

    public static BoundParameter of(Object value) {
        return new BoundParameter(value);
    }
}
