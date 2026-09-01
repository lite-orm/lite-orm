package org.liteorm.annotation;

import org.liteorm.api.JdbcValueAdapter;

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
     * Returns the concrete adapter for the declared Java value type.
     */
    Class<? extends JdbcValueAdapter<?>> adapter();

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
