package io.github.kervix.annotation;

import io.github.kervix.api.ExecutionPlan;
import io.github.kervix.api.SqlProvider;

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
