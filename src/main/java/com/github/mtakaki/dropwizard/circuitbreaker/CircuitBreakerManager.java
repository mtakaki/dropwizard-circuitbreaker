package com.github.mtakaki.dropwizard.circuitbreaker;

import java.util.concurrent.ConcurrentHashMap;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.github.mtakaki.dropwizard.circuitbreaker.jersey.CircuitBreaker;

import lombok.AllArgsConstructor;
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
    private final ConcurrentHashMap<String, MeterThreshold> circuitBreakerMap;
    private final MetricRegistry metricRegistry;
    @Getter
    private final double defaultThreshold;
    @Getter
    private final RateType rateType;

    /**
     * Holds the {@link Meter} and the threshold. The threshold can either be a
     * custom threshold or the default one.
     */
    @AllArgsConstructor
    @Getter
    private static class MeterThreshold {
        private final Meter meter;
        private final double threshold;
    }

    /**
     * An operation block that will be wrapped by {@link CircuitBreakerManager}
     * and will catch any exception. In case of an exception, it will increase
     * the meter and, eventually, can cause the circuit to open.
     */
    @FunctionalInterface
    public static interface Operation {
        public void accept(final Meter meter) throws OperationException;
    }

    /**
     * Build a new instance of {@link CircuitBreakerManager}. This instance will
     * be thread-safe, so it should be shared among different threads.
     *
     * @param defaultThreshold
     *            The threshold that will determine if a circuit should be
     *            opened or not. This threshold unit is requests per second.
     * @param metricRegistry
     *            The {@link MetricRegistry} that will be used to build, and
     *            register, our {@link Meter}.
     * @param rateType
     *            The rate unit used to determining if the circuit is opened or
     *            not.
     */
    public CircuitBreakerManager(final MetricRegistry metricRegistry, final double defaultThreshold,
            final RateType rateType) {
        this.circuitBreakerMap = new ConcurrentHashMap<>();
        this.defaultThreshold = defaultThreshold;
        this.metricRegistry = metricRegistry;
        this.rateType = rateType;
    }

    /**
     * Will retrieve, or build if it doesn't exist yet, the {@link Meter} that
     * backs the circuit with the given name. If the circuit breaker doesn't
     * exist yet, it will use the default threshold. If you want to use a custom
     * threshold, use the other {@code getMeter()} method.
     *
     * @param name
     *            The circuit name.
     * @return The meter that belongs to the circuit with the given name.
     */
    public Meter getMeter(final String name) {
        return this.getMeter(name, this.defaultThreshold);
    }

    /**
     * Will retrieve, or build if it doesn't exist yet, the {@link Meter} that
     * backs the circuit with the given name.
     *
     * @param name
     *            The circuit name.
     * @param threshold
     *            The circuit breaker custom threshold, so it won't use the
     *            default one.
     * @return The meter that belongs to the circuit with the given name.
     */
    public Meter getMeter(final String name, final double threshold) {
        MeterThreshold meterThreshold = this.circuitBreakerMap.get(name);

        if (meterThreshold == null) {
            final Meter meter = this.metricRegistry.meter(name);
            meterThreshold = new MeterThreshold(meter, threshold);
            this.circuitBreakerMap.put(name, meterThreshold);
        }

        return meterThreshold.getMeter();
    }

    /**
     * Executes the given {@link Operation} and if it throw any exception the
     * meter will be increased, which can cause the circuit to be opened.
     *
     * @param name
     *            The circuit name.
     * @param codeBlock
     *            The code block that will be executed.
     * @throws OperationException
     *             The exception thrown from the given code block.
     */
    public void wrapCodeBlock(final String name, final Operation codeBlock)
            throws OperationException {
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
     * @throws OperationException
     *             The exception thrown from the given code block.
     */
    public void wrapCodeBlockWithCircuitBreaker(final String name, final Operation codeBlock)
            throws CircuitBreakerOpenedException, OperationException {
        if (this.isCircuitOpen(name)) {
            throw new CircuitBreakerOpenedException(name);
        }

        this.wrapCodeBlock(name, codeBlock);
    }

    /**
     * Verifies if the circuit is opened for a given circuit name. It will use
     * the given {@link RateType} to determine if the circuit is opened or not.
     * If the meter has not been previously created, it will create it using the
     * default threshold. To make sure it uses the correct threshold, call
     * {@code getMeter()} before-hand.
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
            return exceptionMeter.getMeanRate() >= this.defaultThreshold;
        case ONE_MINUTE:
            return exceptionMeter.getOneMinuteRate() >= this.defaultThreshold;
        case FIVE_MINUTES:
            return exceptionMeter.getFiveMinuteRate() >= this.defaultThreshold;
        case FIFTEEN_MINUTES:
            return exceptionMeter.getFifteenMinuteRate() >= this.defaultThreshold;
        default:
            return false;
        }
    }
}