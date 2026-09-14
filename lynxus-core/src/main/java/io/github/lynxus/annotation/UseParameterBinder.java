package io.github.lynxus.annotation;

import io.github.lynxus.api.ParameterBinder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface UseParameterBinder {

    Class<? extends ParameterBinder<?>> value();
}
