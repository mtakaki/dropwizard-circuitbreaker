package com.github.mtakaki.dropwizard.circuitbreaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;

import org.assertj.core.data.Percentage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.github.mtakaki.dropwizard.circuitbreaker.CircuitBreakerManager.RateType;

@RunWith(MockitoJUnitRunner.class)
public class CircuitBreakerManagerTest {
    private static final String METER_NAME = "test.meter";
    // 1 request per second.
    private static final double DEFAULT_THRESHOLD = 2D;
    private static final double CUSTOM_THRESHOLD = 3D;

    private CircuitBreakerManager circuitBreaker;

    @Spy
    private MetricRegistry metricRegistry;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setup() {
        this.circuitBreaker = new CircuitBreakerManager(this.metricRegistry, DEFAULT_THRESHOLD,
                CircuitBreakerManager.RateType.MEAN);
    }

    /**
     * Testing {@code getMeter()} will build a new {@link Meter} the first time
     * the name is passed to it. And testing it returns the same instance when
     * we pass the same name again.
     */
    @Test
    public void testGetMeterWithExistingMeter() {
        final Meter meter = this.circuitBreaker.getMeter(METER_NAME);

        assertThat(meter).isNotNull();
        assertThat(meter.getCount()).isEqualTo(0L);

        final Meter sameMeter = this.circuitBreaker.getMeter(METER_NAME);

        assertThat(sameMeter).isNotNull().isEqualTo(meter);
        assertThat(sameMeter.getCount()).isEqualTo(0L);
    }

    /**
     * Testing {@code getMeter()} will build a new {@link Meter} the first time
     * the name is passed to it. We can't really easily test that the custom
     * threshold is actually set.
     */
    @Test
    public void testGetMeterWithCustomThreshold() {
        final Meter meter = this.circuitBreaker.getMeter(METER_NAME, CUSTOM_THRESHOLD);

        assertThat(meter).isNotNull();
        assertThat(meter.getCount()).isEqualTo(0L);
    }

    /**
     * Testing that when we call {@code isCircuitOpen()} with a new meter name
     * it will return {@code false}.
     */
    @Test
    public void testIsCircuitOpenWithBrandNewMeter() {
        final boolean isCircuitOpen = this.circuitBreaker.isCircuitOpen(METER_NAME);

        assertThat(isCircuitOpen).isFalse();
    }

    /**
     * Testing that when we call {@code isCircuitOpen()} and the {@link Meter}
     * rate is above the threshold, it should return true.
     */
    @Test
    public void testIsCircuitOpenWithFailingMeter() {
        final Meter mockMeter = mock(Meter.class);
        when(this.metricRegistry.meter(METER_NAME)).thenReturn(mockMeter);
        when(mockMeter.getMeanRate()).thenReturn(DEFAULT_THRESHOLD + 0.1);
        final boolean isCircuitOpen = this.circuitBreaker.isCircuitOpen(METER_NAME);

        assertThat(isCircuitOpen).isTrue();
        verify(mockMeter, only()).getMeanRate();
        verify(mockMeter, times(0)).getOneMinuteRate();
        verify(mockMeter, times(0)).getFiveMinuteRate();
        verify(mockMeter, times(0)).getFifteenMinuteRate();
    }

    /**
     * Testing that we call {@code getOneMinuteRate()} when we pass
     * {@code RateType.ONE_MINUTE} to our {@link CircuitBreakerManager}.
     */
    @Test
    public void testIsCircuitOpenWith1MinuteRate() {
        this.circuitBreaker = new CircuitBreakerManager(this.metricRegistry, DEFAULT_THRESHOLD,
                RateType.ONE_MINUTE);
        final Meter mockMeter = mock(Meter.class);
        when(this.metricRegistry.meter(METER_NAME)).thenReturn(mockMeter);
        when(mockMeter.getOneMinuteRate()).thenReturn(DEFAULT_THRESHOLD + 0.1);
        final boolean isCircuitOpen = this.circuitBreaker.isCircuitOpen(METER_NAME);

        assertThat(isCircuitOpen).isTrue();
        verify(mockMeter, times(0)).getMeanRate();
        verify(mockMeter, only()).getOneMinuteRate();
        verify(mockMeter, times(0)).getFiveMinuteRate();
        verify(mockMeter, times(0)).getFifteenMinuteRate();
    }

    /**
     * Testing that we call {@code getFiveMinuteRate()} when we pass
     * {@code RateType.FIVE_MINUTES} to our {@link CircuitBreakerManager}.
     */
    @Test
    public void testIsCircuitOpenWith5MinuteRate() {
        this.circuitBreaker = new CircuitBreakerManager(this.metricRegistry, DEFAULT_THRESHOLD,
                RateType.FIVE_MINUTES);
        final Meter mockMeter = mock(Meter.class);
        when(this.metricRegistry.meter(METER_NAME)).thenReturn(mockMeter);
        when(mockMeter.getFiveMinuteRate()).thenReturn(DEFAULT_THRESHOLD + 0.1);
        final boolean isCircuitOpen = this.circuitBreaker.isCircuitOpen(METER_NAME);

        assertThat(isCircuitOpen).isTrue();
        verify(mockMeter, times(0)).getMeanRate();
        verify(mockMeter, times(0)).getOneMinuteRate();
        verify(mockMeter, only()).getFiveMinuteRate();
        verify(mockMeter, times(0)).getFifteenMinuteRate();
    }

    /**
     * Testing that we call {@code getFifteenMinuteRate()} when we pass
     * {@code RateType.FIFTEEN_MINUTES} to our {@link CircuitBreakerManager}.
     */
    @Test
    public void testIsCircuitOpenWith15MinuteRate() {
        this.circuitBreaker = new CircuitBreakerManager(this.metricRegistry, DEFAULT_THRESHOLD,
                RateType.FIFTEEN_MINUTES);
        final Meter mockMeter = mock(Meter.class);
        when(this.metricRegistry.meter(METER_NAME)).thenReturn(mockMeter);
        when(mockMeter.getFifteenMinuteRate()).thenReturn(DEFAULT_THRESHOLD + 0.1);
        final boolean isCircuitOpen = this.circuitBreaker.isCircuitOpen(METER_NAME);

        assertThat(isCircuitOpen).isTrue();
        verify(mockMeter, times(0)).getMeanRate();
        verify(mockMeter, times(0)).getOneMinuteRate();
        verify(mockMeter, times(0)).getFiveMinuteRate();
        verify(mockMeter, only()).getFifteenMinuteRate();
    }

    /**
     * Testing that our meter is not marked when we call {@code wrapException()}
     * and no exception was thrown.
     */
    @Test
    public void testWrapCodeBlockWithNoExceptionCaught() {
        final Meter meter = this.circuitBreaker.getMeter(METER_NAME);

        // It's a brand new Meter, everything should be zero.
        assertThat(meter).isNotNull();
        assertThat(meter.getCount()).isEqualTo(0L);
        assertThat(meter.getMeanRate()).isCloseTo(0.0, Percentage.withPercentage(0.00001));

        try {
            this.circuitBreaker.wrapCodeBlock(METER_NAME, (currentMeter) -> {
            });
        } catch (final Exception e) {
        }

        // At this point our meter count should be 0 as we didn't have any
        // exception under it and the rate should still be close to zero.
        assertThat(meter.getCount()).isEqualTo(0L);
        assertThat(meter.getMeanRate()).isCloseTo(0.0, Percentage.withPercentage(0.00001));
        assertThat(this.circuitBreaker.isCircuitOpen(METER_NAME)).isFalse();
    }

    /**
     * Testing that when an exception is thrown within {@code wrapException()}
     * our meter is increased.
     */
    @Test
    public void testWrapCodeBlockWithExceptionCaught() {
        final Meter meter = this.circuitBreaker.getMeter(METER_NAME);
        assertThat(meter.getCount()).isEqualTo(0L);
        assertThat(meter.getMeanRate()).isCloseTo(0.0, Percentage.withPercentage(0.00001));

        try {
            this.circuitBreaker.wrapCodeBlock(METER_NAME, (currentMeter) -> {
                throw new OperationException();
            });
        } catch (final Exception e) {
        }

        assertThat(meter.getCount()).isEqualTo(1L);
        assertThat(meter.getMeanRate()).isGreaterThan(0.0);
        assertThat(this.circuitBreaker.isCircuitOpen("test.name")).isFalse();
    }

    /**
     * Testing that after several exceptions our circuit gets opened.
     */
    @Test
    public void testWrapCodeBlockWithExceptionCaughtCauseOpenCircuit() {
        final Meter meter = this.circuitBreaker.getMeter(METER_NAME);
        assertThat(meter.getCount()).isEqualTo(0L);
        assertThat(meter.getMeanRate()).isCloseTo(0.0, Percentage.withPercentage(0.00001));

        for (int i = 0; i < 10; i++) {
            try {
                this.circuitBreaker.wrapCodeBlock(METER_NAME, (currentMeter) -> {
                    throw new OperationException();
                });
            } catch (final Exception e) {
            }
        }

        assertThat(meter.getCount()).isEqualTo(10L);
        assertThat(meter.getMeanRate()).isGreaterThan(DEFAULT_THRESHOLD);
        assertThat(this.circuitBreaker.isCircuitOpen(METER_NAME)).isTrue();
    }

    /**
     * Testing that after getting our circuit open, if we wait 1 second we
     * should get our circuit closed again.
     *
     * @throws InterruptedException
     */
    @Test
    public void testWrapCodeBlockWithExceptionCaughtCauseOpenCircuitAndClosesAfterSomeTime()
            throws InterruptedException {
        final Meter meter = this.circuitBreaker.getMeter(METER_NAME);
        assertThat(meter.getCount()).isEqualTo(0L);
        assertThat(meter.getMeanRate()).isCloseTo(0.0, Percentage.withPercentage(0.00001));

        for (int i = 0; i < 2; i++) {
            try {
                this.circuitBreaker.wrapCodeBlock(METER_NAME, (currentMeter) -> {
                    throw new OperationException();
                });
            } catch (final Exception e) {
            }
        }

        assertThat(meter.getCount()).isEqualTo(2L);
        assertThat(meter.getMeanRate()).isGreaterThan(DEFAULT_THRESHOLD);
        assertThat(this.circuitBreaker.isCircuitOpen(METER_NAME)).isTrue();

        // Unfortunately we need to wait 1 seconds until the threshold gets
        // decreased below the threshold.
        Thread.sleep(1000L);

        assertThat(meter.getCount()).isEqualTo(2L);
        assertThat(meter.getMeanRate()).isLessThan(DEFAULT_THRESHOLD);
        assertThat(this.circuitBreaker.isCircuitOpen(METER_NAME)).isFalse();
    }

    /**
     * Testing that when we call {@code wrapCodeBlockWithCircuitBreaker()} it
     * gets executed.
     *
     * @throws Exception
     */
    @Test
    public void testWrapCodeBlockWithCircuitBreakerWithoutExceptions() throws Exception {
        final Meter meter = this.circuitBreaker.getMeter(METER_NAME);
        assertThat(meter.getCount()).isEqualTo(0L);
        assertThat(meter.getMeanRate()).isCloseTo(0.0, Percentage.withPercentage(0.00001));

        final AtomicBoolean executed = new AtomicBoolean(false);
        this.circuitBreaker.wrapCodeBlockWithCircuitBreaker(METER_NAME, (currentMeter) -> {
            executed.set(true);
        });

        assertThat(meter.getCount()).isEqualTo(0L);
        assertThat(meter.getMeanRate()).isLessThan(DEFAULT_THRESHOLD);
        assertThat(this.circuitBreaker.isCircuitOpen(METER_NAME)).isFalse();
        assertThat(executed.get()).isTrue();
    }

    /**
     * Testing that after our circuit breaker gets opened, if we try call
     * {@code wrapCodeBlockWithCircuitBreaker()} it will throw a
     * {@link CircuitBreakerOpenedException}.
     *
     * @throws CircuitBreakerOpenedException
     * @throws Exception
     */
    @Test
    public void testWrapCodeBlockWithCircuitBreakerWithExceptions()
            throws CircuitBreakerOpenedException, Exception {
        final Meter meter = this.circuitBreaker.getMeter(METER_NAME);
        assertThat(meter.getCount()).isEqualTo(0L);
        assertThat(meter.getMeanRate()).isCloseTo(0.0, Percentage.withPercentage(0.00001));

        for (int i = 0; i < 2; i++) {
            try {
                this.circuitBreaker.wrapCodeBlockWithCircuitBreaker(METER_NAME, (currentMeter) -> {
                    throw new OperationException();
                });
            } catch (final Exception e) {
            }
        }

        assertThat(meter.getCount()).isBetween(1L, 2L);
        assertThat(meter.getMeanRate()).isGreaterThan(DEFAULT_THRESHOLD);
        assertThat(this.circuitBreaker.isCircuitOpen(METER_NAME)).isTrue();

        this.expectedException.expect(CircuitBreakerOpenedException.class);
        this.expectedException.expectMessage("Circuit breaker is currently opened: " + METER_NAME);
        this.circuitBreaker.wrapCodeBlockWithCircuitBreaker(METER_NAME, (currentMeter) -> {
        });
    }
}