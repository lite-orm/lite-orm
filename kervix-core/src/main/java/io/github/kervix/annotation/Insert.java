package io.github.kervix.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Mapper method as an INSERT statement.
 * 
 * @author kervix
 * @since 2024/09/29
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Insert {
    
    /**
     * SQL statement with supported parameter placeholders.
     * For example: {@code INSERT INTO user (name, email) VALUES (#{name}, #{email})}.
     */
    String[] value();
}
