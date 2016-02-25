package com.github.mtakaki.dropwizard.circuitbreaker;

import java.util.concurrent.ConcurrentHashMap;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.github.mtakaki.dropwizard.circuitbreaker.jersey.CircuitBreaker;

import lombok.Getter;

/**
 * Following circuit breaker design pattern, this class will wrap a given code
 * block into a {@link Meter}. Once that meter reaches a given threshold, our
 * circuit gets open and it will consider it unavailable until the rate goes
 * below the given threshold.
 *
 * This class can be used stand-alone, without the usage of
 * {@link CircuitBreaker} annotation in jersey resources.
 *
 * @author mtakaki
 *
 */
public class CircuitBreakerManager {
    /**
     * The rate used to determine if the circuit is opened or not.
     */
    public static enum RateType {
        MEAN, ONE_MINUTE, FIVE_MINUTES, FIFTEEN_MINUTES;
    }

    /**
     * An operation block that will be wrapped by {@link CircuitBreakerManager}
     * and will catch any exception. In case of an exception, it will increase
     * the meter and, eventually, can cause the circuit to open.
     */
    public static interface Operation {
        public void accept(final Meter meter) throws Exception;
    }

    private final ConcurrentHashMap<String, Meter> circuitBreakerMap;
    private final MetricRegistry metricRegistry;
    @Getter
    private final double threshold;
    @Getter
    private final RateType rateType;

    /**
     * Build a new instance of {@link CircuitBreakerManager}. This instance will
     * be thread-safe, so it should be shared among different threads.
     *
     * @param threshold
     *            The threshold that will determine if a circuit should be
     *            opened or not. This threshold unit is requests per second.
     * @param metricRegistry
     *            The {@link MetricRegistry} that will be used to build, and
     *            register, our {@link Meter}.
     * @param rateType
     *            The rate unit used to determining if the circuit is opened or
     *            not.
     */
    public CircuitBreakerManager(final MetricRegistry metricRegistry, final double threshold,
            final RateType rateType) {
        this.circuitBreakerMap = new ConcurrentHashMap<>();
        this.threshold = threshold;
        this.metricRegistry = metricRegistry;
        this.rateType = rateType;
    }

    /**
     * Will retrieve, or build if it doesn't exist yet, the {@link Meter} that
     * backs the circuit with the given name.
     *
     * @param name
     *            The circuit name.
     * @return The meter that belongs to the circuit with the given name.
     */
    public Meter getMeter(final String name) {
        Meter meter = this.circuitBreakerMap.get(name);

        if (meter == null) {
            meter = this.metricRegistry.meter(name);
            this.circuitBreakerMap.put(name, meter);
        }

        return meter;
    }

    /**
     * Executes the given {@link Operation} and if it throw any exception the
     * meter will be increased, which can cause the circuit to be opened.
     *
     * @param name
     *            The circuit name.
     * @param codeBlock
     *            The code block that will be executed.
     * @throws Exception
     *             The exception thrown from the given code block.
     */
    public void wrapCodeBlock(final String name, final Operation codeBlock) throws Exception {
        final Meter exceptionMeter = this.getMeter(name);

        try {
            codeBlock.accept(exceptionMeter);
        } catch (final Exception e) {
            exceptionMeter.mark();
            throw e;
        }
    }

    /**
     * Verifies if the circuit breaker is not opened before trying to execute
     * the given code block. If it's not opened it will execute the given
     * {@link Operation} and if it throw any exception the meter will be
     * increased, which can cause the circuit to be opened.
     *
     * @param name
     *            The circuit name.
     * @param codeBlock
     *            The code block that will be executed.
     * @throws CircuitBreakerOpenedException
     *             Thrown if the circuit breaker is opened and the block can't
     *             be executed.
     * @throws Exception
     *             The exception thrown from the given code block.
     */
    public void wrapCodeBlockWithCircuitBreaker(final String name, final Operation codeBlock)
            throws CircuitBreakerOpenedException, Exception {
        if (this.isCircuitOpen(name)) {
            throw new CircuitBreakerOpenedException(name);
        }

        this.wrapCodeBlock(name, codeBlock);
    }

    /**
     * Verifies if the circuit is opened for a given circuit name. It will use
     * the given {@link RateType} to determine if the circuit is opened or not.
     *
     * @param name
     *            The circuit name.
     * @return {@code true} if the circuit is open, which means the service is
     *         unavailable, or {@code false} if the circuit is closed, meaning
     *         the service is healthy again.
     */
    public boolean isCircuitOpen(final String name) {
        final Meter exceptionMeter = this.getMeter(name);

        switch (this.rateType) {
        case MEAN:
            return exceptionMeter.getMeanRate() >= this.threshold;
        case ONE_MINUTE:
            return exceptionMeter.getOneMinuteRate() >= this.threshold;
        case FIVE_MINUTES:
            return exceptionMeter.getFiveMinuteRate() >= this.threshold;
        case FIFTEEN_MINUTES:
            return exceptionMeter.getFifteenMinuteRate() >= this.threshold;
        default:
            return false;
        }
    }
}