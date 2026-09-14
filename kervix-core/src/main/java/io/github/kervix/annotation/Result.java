package io.github.kervix.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps one result-set column to one Java property or record component.
 * The column label must not be blank. Object mappings require a property name,
 * while scalar mappings leave the property blank. When declared, the Java type
 * must exactly match the scalar, record-component, or setter-parameter type.
 */
@Documented
@Target({})
@Retention(RetentionPolicy.SOURCE)
public @interface Result {

    /** Nonblank result-set column label. */
    String column();

    /** Java property or record-component name, or blank for a scalar result. */
    String property() default "";

    /** Optional exact Java target type used for compile-time validation. */
    Class<?> javaType() default void.class;
}
