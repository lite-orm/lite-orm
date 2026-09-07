package org.liteorm.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.JDBCType;

/**
 * Selects the declared JDBC representation for a scalar Mapper result.
 *
 * <p>On a method, the declaration applies only to SELECT methods with direct scalar results and scalar
 * elements returned through {@link java.util.List} or {@link java.util.Optional}. On an enum record
 * component or JavaBean field, it selects name or ordinal conversion for that property. The compiler
 * rejects unsupported result shapes and combinations with a cursor or custom row mapper.</p>
 */
@Documented
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.CLASS)
public @interface ResultJdbcType {

    /** Returns the exact JDBC type mapping selected for the result. */
    JDBCType value();
}
