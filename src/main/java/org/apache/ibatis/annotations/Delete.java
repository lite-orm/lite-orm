package org.apache.ibatis.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记删除方法的注解，兼容MyBatis
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Delete {
    
    /**
     * SQL语句，支持参数占位符
     * 例如：DELETE FROM user WHERE id = #{id}
     */
    String[] value();
}
