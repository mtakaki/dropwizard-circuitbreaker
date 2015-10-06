package com.mtakaki.dropwizard.circuitbreaker.jersey;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

import com.codahale.metrics.Meter;
import com.mtakaki.dropwizard.circuitbreaker.CircuitBreakerManager;

import lombok.AllArgsConstructor;

/**
 * ApplicationEventListener that will bind the annotation {@link CircuitBreaker}
 * to the given {@link CircuitBreakerManager}. It will mark the meters when an
 * exception is thrown from a resource method and it will automatically return
 * 503 (Service Unavailable) when the circuit is open.
 *
 * @author mtakaki
 *
 */
@AllArgsConstructor
@Provider
public class CircuitBreakerApplicationEventListener implements ApplicationEventListener {
    @AllArgsConstructor
    private static class CircuitBreakerEventListener implements RequestEventListener {
        private final CircuitBreakerManager circuitBreakerManager;
        private final Map<String, Meter> meterMap;

        @Override
        public void onEvent(final RequestEvent event) {
            final Optional<String> circuitName = CircuitBreakerApplicationEventListener
                    .getCircuitBreakerName(event.getUriInfo().getMatchedResourceMethod());

            circuitName.ifPresent((actualCircuitName) -> {
                if (event.getType() == RequestEvent.Type.RESOURCE_METHOD_START
                        && this.circuitBreakerManager.isCircuitOpen(actualCircuitName)) {
                    throw new WebApplicationException(Response.Status.SERVICE_UNAVAILABLE);
                } else if (event.getType() == RequestEvent.Type.ON_EXCEPTION) {
                    this.meterMap.get(actualCircuitName).mark();
                }
            });
        }
    }

    private final CircuitBreakerManager circuitBreaker;
    private final Map<String, Meter> meterMap = new HashMap<>();

    @Override
    public void onEvent(final ApplicationEvent event) {
        if (event.getType() == ApplicationEvent.Type.INITIALIZATION_APP_FINISHED) {
            for (final Resource resource : event.getResourceModel().getResources()) {
                this.registerCircuitBreakerAnnotations(resource.getAllMethods());

                for (final Resource childResource : resource.getChildResources()) {
                    this.registerCircuitBreakerAnnotations(childResource.getAllMethods());
                }
            }
        }
    }

    private void registerCircuitBreakerAnnotations(final List<ResourceMethod> resourceMethods) {
        for (final ResourceMethod resourceMethod : resourceMethods) {
            this.registerCircuitBreakerAnnotations(resourceMethod);
        }
    }

    private void registerCircuitBreakerAnnotations(final ResourceMethod resourceMethod) {
        final Optional<String> circuitName = getCircuitBreakerName(resourceMethod);

        if (circuitName.isPresent()) {
            final String actualCircuitName = circuitName.get();
            final Meter meter = this.circuitBreaker.getMeter(actualCircuitName);
            this.meterMap.put(actualCircuitName, meter);
        }
    }

    private static Optional<String> getCircuitBreakerName(final ResourceMethod resourceMethod) {
        if (resourceMethod == null) {
            return Optional.empty();
        }

        final Invocable invocable = resourceMethod.getInvocable();
        Method method = invocable.getDefinitionMethod();
        CircuitBreaker circuitBreaker = method.getAnnotation(CircuitBreaker.class);

        // In case it's a child class with a parent method annotated.
        if (circuitBreaker == null) {
            method = invocable.getHandlingMethod();
            circuitBreaker = method.getAnnotation(CircuitBreaker.class);
        }

        if (circuitBreaker != null) {
            return Optional.of(StringUtils.defaultIfBlank(circuitBreaker.name(),
                    name(invocable.getHandler().getHandlerClass(), method.getName())));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Builds the {@link Meter} name using the given class and method name.
     *
     * @param clazz
     *            The class that has the annotated method.
     * @param methodName
     *            The method name that is annotated with {@link CircuitBreaker}.
     * @return A String that represents the class and method.
     */
    private static String name(final Class<?> clazz, final String methodName) {
        return new StringBuilder(clazz.getName()).append(".").append(methodName).toString();
    }

    @Override
    public RequestEventListener onRequest(final RequestEvent requestEvent) {
        return new CircuitBreakerEventListener(this.circuitBreaker, this.meterMap);
    }
}