package com.mtakaki.dropwizard.circuitbreaker.jersey;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(METHOD)
@Retention(RUNTIME)
@Documented
public @interface CircuitBreaker {
    String name() default "";

    double threshold() default 0D;
}