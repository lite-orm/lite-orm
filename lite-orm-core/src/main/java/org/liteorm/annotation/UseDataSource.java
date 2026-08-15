package org.liteorm.annotation;

import org.liteorm.api.DataSourceKeyProvider;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects a fixed executor key or a compile-time-validated typed provider.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface UseDataSource {

    String value() default "";

    Class<? extends DataSourceKeyProvider<?>> provider() default DataSourceKeyProvider.None.class;
}
