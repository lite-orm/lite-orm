package org.liteorm.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the flat result mapping for one Mapper SQL method.
 * This annotation requires a SELECT method with a mapped result type, cannot be combined with
 * {@link UseRowMapper}, and does not support nested object or collection mappings.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface Results {

    /** One or more column-to-property mappings for the method result. */
    Result[] value();
}
