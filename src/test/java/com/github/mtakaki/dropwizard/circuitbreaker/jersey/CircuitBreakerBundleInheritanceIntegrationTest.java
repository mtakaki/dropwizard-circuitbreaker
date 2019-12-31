package com.github.mtakaki.dropwizard.circuitbreaker.jersey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.annotation.ExceptionMetered;
import com.github.mtakaki.dropwizard.circuitbreaker.CircuitBreakerManager;
import com.github.mtakaki.dropwizard.circuitbreaker.RateType;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import lombok.Getter;

@ExtendWith(DropwizardExtensionsSupport.class)
public class CircuitBreakerBundleInheritanceIntegrationTest {
    private static final String METER_NAME = CircuitBreakerBundleInheritanceIntegrationTest.TestResource.class
            .getTypeName() + ".get.circuitBreaker";
    private static final String OPEN_CIRCUIT_METER_NAME = METER_NAME + ".openCircuit";

    private final DropwizardTestSupport<TestConfiguration> testSupport = new DropwizardTestSupport<>(
            TestApplication.class, "src/test/resources/config.yml");
    private final DropwizardAppExtension<TestConfiguration> DROPWIZARD = new DropwizardAppExtension<>(
            this.testSupport);

    public static ThreadLocal<CircuitBreakerManager> circuitBreakerManager = new ThreadLocal<>();
    public static ThreadLocal<Meter> meter = new ThreadLocal<>();
    public static ThreadLocal<Meter> customMeter = new ThreadLocal<>();
    public static ThreadLocal<MetricRegistry> metricRegistry = new ThreadLocal<>();

    public static class ParentResource {
        @GET
        @CircuitBreaker
        public Response get() throws Exception {
            throw new Exception("We want this to fail");
        }
    }

    @Path("/test")
    public static class TestResource extends ParentResource {
        @GET
        @Path("/custom")
        @CircuitBreaker(name = "customName")
        public Response getCustom() throws Exception {
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
                        .isSameAs(RateType.ONE_MINUTE);
                assertThat(circuitBreakerConfiguration.getThreshold()).isEqualTo(0.5);

                final Map<String, Double> customThreshold = new HashMap<>();
                customThreshold.put("customName", 0.2d);
                assertThat(circuitBreakerConfiguration.getCustomThresholds())
                        .containsAllEntriesOf(customThreshold);

                final CircuitBreakerManager circuitBreaker = mock(CircuitBreakerManager.class);
                circuitBreakerManager.set(circuitBreaker);
                when(circuitBreaker.getDefaultThreshold()).thenReturn(0.5d);

                // Creating the mock Meter that is marked only when there are
                // exceptions and the circuit is not open.
                final Meter meter = mock(Meter.class);
                CircuitBreakerBundleInheritanceIntegrationTest.meter.set(meter);

                when(circuitBreaker.getMeter(METER_NAME, 0.5d)).thenReturn(meter);

                final Meter customMeter = mock(Meter.class);
                CircuitBreakerBundleInheritanceIntegrationTest.customMeter.set(customMeter);
                when(circuitBreaker.getMeter("customName", 0.2d)).thenReturn(customMeter);

                CircuitBreakerBundleInheritanceIntegrationTest.metricRegistry
                        .set(environment.metrics());

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
        this.sendGetRequestAndVerifyStatus("/test", 500);

        // Verifying the meter was called once the exception happened.
        verify(meter.get(), only()).mark();
        // The count of our open circuit meter should be the same.
        assertThat(openCircuitMeter.getCount()).isEqualTo(beforeOpenCircuitCount);
    }

    @Test
    public void testCustomMeterCountIsIncremented() {
        when(circuitBreakerManager.get().isCircuitOpen("custom")).thenReturn(false);
        final Meter openCircuitMeter = metricRegistry.get().meter("custom.openCircuit");
        final long beforeOpenCircuitCount = openCircuitMeter.getCount();

        // We wanted this request to fail.
        this.sendGetRequestAndVerifyStatus("/test/custom", 500);

        // Verifying the meter was called once the exception happened.
        verify(customMeter.get(), only()).mark();
        // The count of our open circuit meter should be the same.
        assertThat(openCircuitMeter.getCount()).isEqualTo(beforeOpenCircuitCount);
    }

    @Test
    public void testInvalidURI() {
        when(circuitBreakerManager.get().isCircuitOpen("custom")).thenReturn(false);

        // An invalid URI should return 404 and not fail.
        final Response response = this.DROPWIZARD.client().target(
                String.format("http://localhost:%d/wrong", this.DROPWIZARD.getLocalPort()))
                .request().get();

        assertThat(response.getStatus()).isEqualTo(404);
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
        this.sendGetRequestAndVerifyStatus("/test", 503);

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
        this.sendGetRequestAndVerifyStatus("/test", 503);

        // Verifying the meter was not called because the circuit was opened.
        verify(meter.get(), times(0)).mark();
        // The count of our open circuit meter should have increased.
        final long afterOpenCircuitCount = openCircuitMeter.getCount();
        assertThat(afterOpenCircuitCount).isGreaterThan(beforeOpenCircuitCount);

        when(circuitBreakerManager.get().isCircuitOpen(METER_NAME)).thenReturn(false);

        // We should get 500 again.
        this.sendGetRequestAndVerifyStatus("/test", 500);

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
    private void sendGetRequestAndVerifyStatus(final String path, final int httpStatus) {
        final Response response = this.DROPWIZARD.client().target(
                String.format("http://localhost:%d%s", this.DROPWIZARD.getLocalPort(), path))
                .request().get();

        assertThat(response.getStatus()).isEqualTo(httpStatus);
    }
}