package org.liteorm.annotation;

import org.liteorm.api.TypeHandler;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.JDBCType;

/**
 * Declares one Java-type and JDBC-type mapping in a JDBC mapping collection.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Repeatable(JdbcTypeMapping.List.class)
public @interface JdbcTypeMapping {

    /**
     * Returns the Java value type handled by this declaration.
     */
    Class<?> javaType();

    /**
     * Returns the JDBC type used for binding nulls and selecting this declaration.
     */
    JDBCType jdbcType();

    /**
     * Returns the optional driver-reported type name that narrows result routing.
     */
    String vendorTypeName() default "";

    /**
     * Returns the concrete type handler for the declared Java value type.
     */
    Class<? extends TypeHandler<?>> handler();

    /**
     * Contains repeated JDBC type mapping declarations.
     */
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @interface List {

        /**
         * Returns the repeated mapping declarations.
         */
        JdbcTypeMapping[] value();
    }
}
