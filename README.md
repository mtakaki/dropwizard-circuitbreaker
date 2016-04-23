### Status
![Build Status](https://codeship.com/projects/6571ba70-b9be-0133-14bc-4a1410bd443d/status?branch=master)
[![Coverage Status](https://coveralls.io/repos/mtakaki/dropwizard-circuitbreaker/badge.svg?branch=master&service=github)](https://coveralls.io/github/mtakaki/dropwizard-circuitbreaker?branch=master)
[![Codacy Badge](https://api.codacy.com/project/badge/grade/039da749e34a4c46b297ddf1389d593a)](https://www.codacy.com/app/mitsuotakaki/dropwizard-circuitbreaker)
[![Download](https://maven-badges.herokuapp.com/maven-central/com.github.mtakaki/dropwizard-circuitbreaker/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.mtakaki/dropwizard-circuitbreaker)
[![Javadoc](https://javadoc-emblem.rhcloud.com/doc/com.github.mtakaki/dropwizard-circuitbreaker/badge.svg)](http://www.javadoc.io/doc/com.github.mtakaki/dropwizard-circuitbreaker)

# Circuit Breaker Library

This library provides a simple implementation of a [circuit breaker design pattern](https://en.wikipedia.org/wiki/Circuit_breaker_design_pattern).

It uses [dropwizard metrics](http://metrics.dropwizard.io/) to provide insights on the rate of failures and, with it, we can reliably assume a certain functionality is having issues. After a certain threshold is hit the circuit is opened and an exception is thrown, preventing from increasing the load on the failing code.

This library can be used as a stand-alone library or embedded into [dropwizard](http://www.dropwizard.io/), through the usage of annotations.

[Project lombok](https://projectlombok.org/) was used to auto-generate the getter and setters, so you will need to have lombok plugin installed in your eclipse/intellij if you want to import the source code. The project also uses Java 8 features, such as `Optional` and lambda expressions, so it's not compatible with prior versions.

These are the supported versions of dropwizard:

| Dropwizard  |  Circuit Breaker |
|---|---|
| 0.8.X  | 0.0.2  |
| 0.9.X  | 0.0.5  |

## Stand-alone

To use this library as a stand-alone circuit breaker, you will need to build the object and pass a `MetricRegistry`. And it can be used to wrap code blocks, which can throw exceptions.

```java
// We build the CircuitBreakerManager with a threshold of 0.5 failure per second and we'll
// be using a 1-minute average rate to determine if our circuit should be opened or not.
CircuitBreakerManager circuitBreaker = new CircuitBreakerManager(metricRegistry, 0.5, CircuitBreakerManager.RateType.ONE_MINUTE);
// When this exception below is thrown, the meter will be increased.
circuitBreaker.wrapCodeBlockWithCircuitBreaker("my.test.block", (meter) -> {
    // This is where you should put your code.

});
```

After several exceptions, and when the rate reaches the threshold, the method `wrapCodeBlockWithCircuitBreaker()` will thrown a `CircuitBreakerOpenedException` and won't run the code block anymore. Alternatively you can run it without throwing an exception, but you need to manually verify if the circuit is opened.

```java
CircuitBreakerManager circuitBreaker = new CircuitBreakerManager(metricRegistry, 0.5, CircuitBreakerManager.RateType.ONE_MINUTE);

if (!circuitBreaker.isCircuitOpen("my.test.block")) {
    // This method will not throw an exception if our circuit is open.
    circuitBreaker.wrapCodeBlock("my.test.block", (meter) -> {
        // This is where you should put your code.

    });
}
```

## With dropwizard

This library seamlessly integrates with dropwizard and provides the annotation `@CircuitBreaker` and it can be used to decorate the resource methods that will use the circuit breaker. When the API has reached a certain rate of exceptions it will automatically return `503 Service Unavailable` until the rate drops below the threshold.

The usage of this design pattern can help alleviate the load when errors cascades from internal dependencies and surfaces into the outermost APIs. As the API starts returning `503` the clients will fail, either gracefully or not but that's outside of the scope of the back end, and the load will decrease on the internal dependencies that were causing the cascading failures.

This library also provides a configuration class (`CircuitBreaker`) that can be used in the application configuration class and a bundle class (`CircuitBreakerBundle`) to automatically register the circuit breaker with the application.

The annotation `@CircuitBreaker` should not be used in conjunction with `@ExceptionMetered` as they both will conflict when creating and registering the `Meter`. As they both are essentially measuring the same thing you won't need the `@ExceptionMetered` annotation.

### Configuration

The configuration class can add reference to `CircuitBreaker` class, which holds the threshold and rate type:

```java
public class MyAppConfiguration extends Configuration {
    private CircuitBreakerConfiguration circuitBreaker;
    
    public CircuitBreakerConfiguration getCircuitBreaker() {
        return this.circuitBreaker;
    }
}
``` 

Your configuration YML will look like this. The `customThresholds` allows having custom thresholds for specific circuit breakers.

```yaml
circuitBreaker:
  threshold: 0.5
  rateType: ONE_MINUTE
  customThresholds:
    com.mtakaki.testcb.TestResource.get.circuitBreaker: 0.2
```

To register the bundle in the application:

```java
public class MyApp extends Application {
    public static void main(final String[] args) throws Exception {
        new MyApp().run(args);
    }

    private final CircuitBreakerBundle<MyAppConfiguration> circuitBreakerBundle = new CircuitBreakerBundle<MyAppConfiguration>() {
        @Override
        protected CircuitBreakerConfiguration getConfiguration(
                final MyAppConfiguration configuration) {
            return configuration.getCircuitBreaker();
        }
    };

    @Override
    public void initialize(final Bootstrap<MyAppConfiguration> bootstrap) {
        bootstrap.addBundle(this.circuitBreakerBundle);
    };
    
    @Override
    public void run(final MyAppConfiguration configuration, final Environment environment)
            throws Exception {
        environment.jersey().register(new TestResource());
    }
}
```

To annotate the resource:

```java
@Path("/test")
public class TestResource {
    @GET
    @CircuitBreaker
    public Response get() throws Exception {
        throw new Exception("We want this to fail");
    }

    @GET
    @Path("/custom")
    @CircuitBreaker(name = "customName")
    public Response getCustom() throws Exception {
        throw new Exception("We want this to fail");
    }
}
```

Now the API `localhost:8080/test` will keep returning `500 Internal Server Error` until it reaches 0.5 exceptions per second (the rate set in our configuration) looking at the 1 minute average (also set in our configuration). Once that happens you will start getting `503 Service Unavailable`. After a couple of seconds the rate will decrease, even if you keep hitting the service and getting `503` responses, and you will be getting `500` once again.

The meter is available in the `metrics` page under the admin port like any other meter. The `circuitBreaker` one measures the exceptions in the API, while `circuiteBreaker.openCircuit` measures the requests that didn't hit your API due to the circuit being open. The latter is only available from version `0.0.3`.

```json
  "meters" : {
    "com.mtakaki.testcb.TestResource.get.circuitBreaker" : {
      "count" : 51,
      "m15_rate" : 0.055135750452891936,
      "m1_rate" : 0.564668417659197,
      "m5_rate" : 0.15659880505693252,
      "mean_rate" : 1.1303494869953181,
      "units" : "events/second"
    },
    "com.mtakaki.testcb.TestResource.get.circuitBreaker.openCircuit" : {
      "count" : 29,
      "m15_rate" : 0.03204704240331378,
      "m1_rate" : 0.44614897600789627,
      "m5_rate" : 0.09510333717433071,
      "mean_rate" : 0.8154557050860357,
      "units" : "events/second"
    }
  }
```

## Maven

The library is available at the maven central, so just add dependency to `pom.xml`:

```xml
<dependencies>
  <dependency>
    <groupId>com.github.mtakaki</groupId>
    <artifactId>dropwizard-circuitbreaker</artifactId>
    <version>0.0.5</version>
  </dependency>
</dependencies>
```

## Benchmark

**tl;dr**: Circuit breaker is very lean and there's barely any impact on performance.

Benchmark tests were executed to assess if there is any cost of running an API with the circuit breaker always verifying if the circuit is open, before piping the request through. These tests were executed with these specs:

- 30 concurrent clients
- 2,000 requests

On average, **without** circuit breaker the benchmark test reached **179.97 requests/second**. While **with** circuit breaker it reached **180.77 requests/second**.

### Failing API

With a constantly failing API, when using the circuit breaker it got **97.40 requests/second**, while without using the circuit breaker it got **92.29 requests/second**. On the other hand, out of the 2,000 requests, 1,396 requests returned `503 Service Unavailable` status.

The metric `CircuitBreakerApplicationEventListener.getCircuitBreakerName` shows how much overhead the circuit breaker adds to the requests. The average overhead, during these tests, were way below 1ms per request.

```json
  "timers" : {
    "com.github.mtakaki.dropwizard.circuitbreaker.jersey.CircuitBreakerApplicationEventListener.getCircuitBreakerName" : {
      "count" : 4015,
      "max" : 0.001024921,
      "mean" : 8.349680896258536E-6,
      "min" : 1.038E-6,
      "p50" : 4.683E-6,
      "p75" : 7.406000000000001E-6,
      "p95" : 1.6315000000000002E-5,
      "p98" : 2.0050000000000003E-5,
      "p99" : 2.7683000000000002E-5,
      "p999" : 6.15457E-4,
      "stddev" : 3.60241125613854E-5,
      "m15_rate" : 6.589688969963449,
      "m1_rate" : 13.102701736894172,
      "m5_rate" : 11.54464183938005,
      "mean_rate" : 30.191455659575904,
      "duration_units" : "seconds",
      "rate_units" : "calls/second"
    }
}
```