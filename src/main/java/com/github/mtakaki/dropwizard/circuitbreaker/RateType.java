package com.github.mtakaki.dropwizard.circuitbreaker;

/**
 * The rate used to determine if the circuit is opened or not.
 *
 * @author mtakaki
 */
public enum RateType {
    MEAN, ONE_MINUTE, FIVE_MINUTES, FIFTEEN_MINUTES;
}