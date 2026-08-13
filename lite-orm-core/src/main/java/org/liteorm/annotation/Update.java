package org.liteorm.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记更新方法的注解
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Update {
    
    /**
     * SQL语句，支持参数占位符
     * 例如：UPDATE user SET name = #{name} WHERE id = #{id}
     */
    String[] value();
}
