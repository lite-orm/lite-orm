package org.liteorm.annotation;

import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.SqlProvider;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface UseSqlProvider {

    Class<? extends SqlProvider<?>> value();

    ExecutionPlan.StatementType statementType();
}
