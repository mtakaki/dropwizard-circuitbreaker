package com.github.mtakaki.dropwizard.circuitbreaker;

/**
 * An exception that wraps errors that happens inside a circuit breaker
 * operation.
 *
 * @author mtakaki
 *
 */
public class OperationException extends Exception {
    private static final long serialVersionUID = 5913294638186584663L;

    public OperationException(final String message) {
        super(message);
    }

    public OperationException(final Exception exception) {
        super(exception);
    }

    /**
     * Empty constructor used when we don't have any message or we're not
     * wrapping any exception.
     */
    public OperationException() {
    }
}