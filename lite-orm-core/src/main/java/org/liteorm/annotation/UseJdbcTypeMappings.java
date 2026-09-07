package org.liteorm.annotation;

import org.liteorm.api.JdbcTypeMappings;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects the base JDBC type mappings collection and optional application override for one Mapper package.
 */
@Documented
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.CLASS)
public @interface UseJdbcTypeMappings {

    /**
     * Returns the selected complete base JDBC type mappings collection.
     */
    Class<? extends JdbcTypeMappings> value();

    /**
     * Returns the optional application mapping collection that overrides the selected base collection.
     *
     * <p>At most one override collection may be selected.</p>
     */
    Class<? extends JdbcTypeMappings>[] overrides() default {};
}
