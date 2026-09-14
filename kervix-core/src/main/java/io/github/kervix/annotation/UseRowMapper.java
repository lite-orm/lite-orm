package io.github.kervix.annotation;

import io.github.kervix.api.RowMapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface UseRowMapper {

    Class<? extends RowMapper<?>> value();
}
