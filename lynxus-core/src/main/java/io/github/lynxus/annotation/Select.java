package io.github.lynxus.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Mapper method as a SELECT statement.
 * 
 * @author lynxus
 * @since 2024/09/29
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Select {
    
    /**
     * SQL statement with supported parameter placeholders.
     * For example: {@code SELECT * FROM user WHERE name = #{name}}.
     */
    String[] value();
}
