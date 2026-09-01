package org.liteorm.annotation;

import org.liteorm.api.JdbcTypeMappings;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects the JDBC type mappings collection for one Mapper package.
 */
@Documented
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.CLASS)
public @interface UseJdbcTypeMappings {

    /**
     * Returns the selected JDBC type mappings collection.
     */
    Class<? extends JdbcTypeMappings> value();
}
