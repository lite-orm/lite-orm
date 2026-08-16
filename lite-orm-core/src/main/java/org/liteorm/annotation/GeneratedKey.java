package org.liteorm.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requests one named JDBC-generated key column as the Mapper method return value.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface GeneratedKey {

    /**
     * Generated key column passed to JDBC when preparing the insert statement.
     */
    String value();
}
