package com.github.mtakaki.dropwizard.circuitbreaker.jersey;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates a resource method that will use a circuit breaker to prevent issues
 * from escalating.
 *
 * @author mtakaki
 *
 */
@Target(METHOD)
@Retention(RUNTIME)
@Documented
public @interface CircuitBreaker {
    String name() default "";
}