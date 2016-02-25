package com.github.mtakaki.dropwizard.circuitbreaker.jersey;

import com.codahale.metrics.MetricRegistry;
import com.github.mtakaki.dropwizard.circuitbreaker.CircuitBreakerManager;

import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import lombok.Getter;

/**
 * Bundle that will build and register the {@link CircuitBreakerManager}. This
 * will enable the usage of {@link CircuitBreaker} annotation in resource
 * methods.
 *
 * @author mtakaki
 *
 * @param <T>
 *            The application configuration.
 */
@Getter
public abstract class CircuitBreakerBundle<T extends Configuration> implements ConfiguredBundle<T> {
    private CircuitBreakerManager circuitBreakerManager;

    @Override
    public void run(final T configuration, final Environment environment) throws Exception {
        final CircuitBreakerConfiguration circuitBreakerConfiguration = this
                .getConfiguration(configuration);
        this.circuitBreakerManager = this.buildCircuitBreakerManager(environment,
                circuitBreakerConfiguration);
        environment.jersey()
                .register(new CircuitBreakerApplicationEventListener(environment.metrics(),
                        this.circuitBreakerManager));
    }

    /**
     * Builds a {@link CircuitBreakerManager} with the given {@link Environment}
     * , from where we'll retrieve the {@link MetricRegistry}, and with the
     * given {@link CircuitBreakerConfiguration}.
     *
     * @param environment
     *            The application environment.
     * @param circuitBreakerConfiguration
     *            The circuit breaker configuration.
     * @return An instance of {@link CircuitBreakerManager}.
     */
    protected CircuitBreakerManager buildCircuitBreakerManager(final Environment environment,
            final CircuitBreakerConfiguration circuitBreakerConfiguration) {
        return new CircuitBreakerManager(environment.metrics(),
                circuitBreakerConfiguration.getThreshold(),
                circuitBreakerConfiguration.getRateType());
    }

    @Override
    public void initialize(final Bootstrap<?> bootstrap) {
    }

    /**
     * Abstract method that should return the
     * {@link CircuitBreakerConfiguration} coming from the given configuration.
     *
     * @param configuration
     *            The application configuration.
     * @return The configuration used to build the {@link CircuitBreakerManager}
     */
    protected abstract CircuitBreakerConfiguration getConfiguration(T configuration);
}