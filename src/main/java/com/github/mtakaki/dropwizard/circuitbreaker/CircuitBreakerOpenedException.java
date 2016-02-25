package com.github.mtakaki.dropwizard.circuitbreaker;

/**
 * Exception thrown when a code block tries to execute and the circuit breaker
 * is currently opened.
 *
 * @author mtakaki
 *
 */
public class CircuitBreakerOpenedException extends Exception {
    /**
     *
     */
    private static final long serialVersionUID = 8305486346292285763L;

    public CircuitBreakerOpenedException(final String circuitName) {
        super("Circuit breaker is currently opened: " + circuitName);
    }
}