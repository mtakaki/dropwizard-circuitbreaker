package com.github.mtakaki.dropwizard.circuitbreaker.jersey;

import java.util.HashMap;
import java.util.Map;

import com.github.mtakaki.dropwizard.circuitbreaker.CircuitBreakerManager;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CircuitBreakerConfiguration {
    /**
     * Default threshold.
     */
    private double threshold;
    /**
     * Default threshold rate type.
     */
    private CircuitBreakerManager.RateType rateType;
    /**
     * Custom thresholds with circuit breaker name and threshold value.
     */
    private Map<String, Double> customThresholds = new HashMap<>();
}