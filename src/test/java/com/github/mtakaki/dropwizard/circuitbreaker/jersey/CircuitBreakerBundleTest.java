package com.github.mtakaki.dropwizard.circuitbreaker.jersey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.codahale.metrics.MetricRegistry;
import com.github.mtakaki.dropwizard.circuitbreaker.CircuitBreakerManager;
import com.github.mtakaki.dropwizard.circuitbreaker.RateType;

import io.dropwizard.Configuration;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Environment;

@ExtendWith(MockitoExtension.class)
public class CircuitBreakerBundleTest {
    @Mock
    private Environment environment;
    @Mock
    private JerseyEnvironment jersey;

    @BeforeEach
    public void setUp() {
        when(this.environment.metrics()).thenReturn(mock(MetricRegistry.class));
        when(this.environment.jersey()).thenReturn(this.jersey);
    }

    @Test
    public void testRun() throws Exception {
        final CircuitBreakerBundle<Configuration> bundle = new CircuitBreakerBundle<Configuration>() {
            @Override
            protected CircuitBreakerConfiguration getConfiguration(
                    final Configuration configuration) {
                final CircuitBreakerConfiguration circuitBreaker = new CircuitBreakerConfiguration();
                circuitBreaker.setRateType(RateType.FIVE_MINUTES);
                circuitBreaker.setThreshold(0.5d);
                return circuitBreaker;
            }
        };

        bundle.run(new Configuration(), this.environment);

        verify(this.jersey, times(1))
                .register(any(CircuitBreakerApplicationEventListener.class));

        final CircuitBreakerManager manager = bundle.getCircuitBreakerManager();
        assertThat(manager.getRateType()).isEqualTo(RateType.FIVE_MINUTES);
        assertThat(manager.getDefaultThreshold()).isEqualTo(0.5d);
    }
}