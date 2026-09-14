package io.github.lynxus.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Mapper method as a DELETE statement.
 * 
 * @author lynxus
 * @since 2024/09/29
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Delete {
    
    /**
     * SQL statement with supported parameter placeholders.
     * For example: {@code DELETE FROM user WHERE id = #{id}}.
     */
    String[] value();
}
