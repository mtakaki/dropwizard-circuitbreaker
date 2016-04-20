package com.github.mtakaki.dropwizard.circuitbreaker.jersey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.codahale.metrics.MetricRegistry;
import com.github.mtakaki.dropwizard.circuitbreaker.CircuitBreakerManager;
import com.github.mtakaki.dropwizard.circuitbreaker.CircuitBreakerManager.RateType;

import io.dropwizard.Configuration;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Environment;

@RunWith(MockitoJUnitRunner.class)
public class CircuitBreakerBundleTest {
    private static class TestConfiguration extends Configuration {
    }

    private CircuitBreakerBundle<TestConfiguration> bundle;

    @Mock
    private Environment environment;
    @Mock
    private JerseyEnvironment jersey;

    @Before
    public void setUp() {
        when(this.environment.metrics()).thenReturn(mock(MetricRegistry.class));
        when(this.environment.jersey()).thenReturn(this.jersey);
    }

    @Test
    public void testRun() throws Exception {
        this.bundle = new CircuitBreakerBundle<CircuitBreakerBundleTest.TestConfiguration>() {
            @Override
            protected CircuitBreakerConfiguration getConfiguration(
                    final TestConfiguration configuration) {
                final CircuitBreakerConfiguration circuitBreaker = new CircuitBreakerConfiguration();
                circuitBreaker.setRateType(RateType.FIVE_MINUTES);
                circuitBreaker.setThreshold(0.5d);
                return circuitBreaker;
            }
        };

        final TestConfiguration configuration = new TestConfiguration();
        this.bundle.run(configuration, this.environment);

        verify(this.jersey, times(1))
                .register(any(CircuitBreakerApplicationEventListener.class));

        final CircuitBreakerManager manager = this.bundle.getCircuitBreakerManager();
        assertThat(manager.getRateType()).isEqualTo(RateType.FIVE_MINUTES);
        assertThat(manager.getThreshold()).isEqualTo(0.5d);
    }
}