package com.github.mtakaki.dropwizard.circuitbreaker.jersey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.annotation.ExceptionMetered;
import com.github.mtakaki.dropwizard.circuitbreaker.CircuitBreakerManager;
import com.github.mtakaki.dropwizard.circuitbreaker.jersey.CircuitBreaker;
import com.github.mtakaki.dropwizard.circuitbreaker.jersey.CircuitBreakerBundle;
import com.github.mtakaki.dropwizard.circuitbreaker.jersey.CircuitBreakerConfiguration;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.dropwizard.util.Duration;

import lombok.Getter;

public class CircuitBreakerBundleIntegrationTest {
    private static final String METER_NAME = CircuitBreakerBundleIntegrationTest.TestResource.class
            .getTypeName() + ".get.circuitBreaker";
    private static final String OPEN_CIRCUIT_METER_NAME = METER_NAME + ".openCircuit";

    @Path("/test")
    public static class TestResource {
        @GET
        @CircuitBreaker
        public Response get() throws Exception {
            throw new Exception("We want this to fail");
        }
    }

    @Getter
    public static class TestConfiguration extends Configuration {
        private CircuitBreakerConfiguration circuitBreaker;
    }

    public static class TestApplication extends Application<TestConfiguration> {
        private final CircuitBreakerBundle<TestConfiguration> circuitBreakerBundle = new CircuitBreakerBundle<TestConfiguration>() {
            @Override
            protected CircuitBreakerConfiguration getConfiguration(
                    final TestConfiguration configuration) {
                return configuration.getCircuitBreaker();
            }

            @Override
            protected CircuitBreakerManager buildCircuitBreakerManager(
                    final Environment environment,
                    final CircuitBreakerConfiguration circuitBreakerConfiguration) {
                // Verifying that our configuration was properly parsed.
                assertThat(circuitBreakerConfiguration.getRateType())
                        .isSameAs(CircuitBreakerManager.RateType.ONE_MINUTE);
                assertThat(circuitBreakerConfiguration.getThreshold()).isEqualTo(0.5);

                final CircuitBreakerManager circuitBreaker = mock(CircuitBreakerManager.class);
                circuitBreakerManager.set(circuitBreaker);

                // Creating the mock Meter that is marked only when there are
                // exceptions and the circuit is not open.
                final Meter meter = mock(Meter.class);
                CircuitBreakerBundleIntegrationTest.meter.set(meter);

                when(circuitBreaker.getMeter(METER_NAME)).thenReturn(meter);

                CircuitBreakerBundleIntegrationTest.metricRegistry.set(environment.metrics());

                return circuitBreaker;
            }
        };

        @Override
        public void initialize(final Bootstrap<TestConfiguration> bootstrap) {
            bootstrap.addBundle(this.circuitBreakerBundle);
        };

        @Override
        public void run(final TestConfiguration configuration, final Environment environment)
                throws Exception {
            environment.jersey().register(new TestResource());
        }
    }

    @Rule
    public final DropwizardAppRule<TestConfiguration> RULE = new DropwizardAppRule<TestConfiguration>(
            TestApplication.class, ResourceHelpers.resourceFilePath("config.yml"));

    private static Client client;

    public static ThreadLocal<CircuitBreakerManager> circuitBreakerManager = new ThreadLocal<>();
    public static ThreadLocal<Meter> meter = new ThreadLocal<>();
    public static ThreadLocal<MetricRegistry> metricRegistry = new ThreadLocal<>();

    @Before
    public void setupClient() {
        final JerseyClientConfiguration jerseyClientConfiguration = new JerseyClientConfiguration();
        jerseyClientConfiguration.setConnectionTimeout(Duration.minutes(1L));
        jerseyClientConfiguration.setConnectionRequestTimeout(Duration.minutes(1L));
        jerseyClientConfiguration.setTimeout(Duration.minutes(1L));
        client = new JerseyClientBuilder(this.RULE.getEnvironment())
                .using(jerseyClientConfiguration)
                .withProperty(ClientProperties.FOLLOW_REDIRECTS, Boolean.FALSE)
                .build("test client");
    }

    /**
     * Testing that our meter gets incremented when a request fails. This should
     * behave like an {@link ExceptionMetered} annotation.
     */
    @Test
    public void testMeterCountIsIncremented() {
        when(circuitBreakerManager.get().isCircuitOpen(METER_NAME)).thenReturn(false);
        final Meter openCircuitMeter = metricRegistry.get().meter(OPEN_CIRCUIT_METER_NAME);
        final long beforeOpenCircuitCount = openCircuitMeter.getCount();

        // We wanted this request to fail.
        this.sendGetRequestAndVerifyStatus(500);

        // Verifying the meter was called once the exception happened.
        verify(meter.get(), only()).mark();
        // The count of our open circuit meter should be the same.
        assertThat(openCircuitMeter.getCount()).isEqualTo(beforeOpenCircuitCount);
    }

    /**
     * Testing that when the circuit is open we should get 503 responses.
     */
    @Test
    public void testCircuitBreakerIsOpened() {
        when(circuitBreakerManager.get().isCircuitOpen(METER_NAME)).thenReturn(true);
        final Meter openCircuitMeter = metricRegistry.get().meter(OPEN_CIRCUIT_METER_NAME);
        final long beforeOpenCircuitCount = openCircuitMeter.getCount();

        // We should get 503 - Service unavailable.
        this.sendGetRequestAndVerifyStatus(503);

        // Verifying the meter was not called because the circuit was opened.
        verify(meter.get(), times(0)).mark();
        // The count of our open circuit meter should have increased.
        assertThat(openCircuitMeter.getCount()).isGreaterThan(beforeOpenCircuitCount);
    }

    /**
     * Testing that when the circuit is open we should get 503 responses and if
     * it closes we should get 500s again.
     */
    @Test
    public void testCircuitBreakerIsOpenedAndClosesAgain() {
        when(circuitBreakerManager.get().isCircuitOpen(METER_NAME)).thenReturn(true);
        final Meter openCircuitMeter = metricRegistry.get().meter(OPEN_CIRCUIT_METER_NAME);
        final long beforeOpenCircuitCount = openCircuitMeter.getCount();

        // We should get 503 - Service unavailable.
        this.sendGetRequestAndVerifyStatus(503);
        // The count of our open circuit meter should have increased.
        final long afterOpenCircuitCount = openCircuitMeter.getCount();
        assertThat(afterOpenCircuitCount).isGreaterThan(beforeOpenCircuitCount);

        // Verifying the meter was not called because the circuit was opened.
        verify(meter.get(), times(0)).mark();

        when(circuitBreakerManager.get().isCircuitOpen(METER_NAME)).thenReturn(false);

        // We should get 500 again.
        this.sendGetRequestAndVerifyStatus(500);

        // Verifying the meter was called only once as the the first time the
        // circuit was opened.
        verify(meter.get(), times(1)).mark();
        // The count of our open circuit meter should be the same as
        // afterOpenCircuitCount.
        assertThat(openCircuitMeter.getCount()).isEqualTo(afterOpenCircuitCount);
    }

    /**
     * Sends a request and verify it returns the given status code.
     *
     * @param httpStatus
     *            The expected status code.
     */
    private void sendGetRequestAndVerifyStatus(final int httpStatus) {
        final Response response = client.target(
                String.format("http://localhost:%d/test/", this.RULE.getLocalPort()))
                .request().get();

        assertThat(response.getStatus()).isEqualTo(httpStatus);
    }
}