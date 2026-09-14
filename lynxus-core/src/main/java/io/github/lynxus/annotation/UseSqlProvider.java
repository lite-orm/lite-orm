package io.github.lynxus.annotation;

import io.github.lynxus.api.ExecutionPlan;
import io.github.lynxus.api.SqlProvider;

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
