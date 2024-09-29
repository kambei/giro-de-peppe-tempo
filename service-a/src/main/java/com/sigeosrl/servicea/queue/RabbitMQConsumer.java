package com.sigeosrl.servicea.queue;

import com.sigeosrl.servicea.external.ServiceGoClient;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Map;

@ApplicationScoped
@Slf4j
public class RabbitMQConsumer {

    @Inject
    @RestClient
    ServiceGoClient serviceGoClient;

    @Inject
    Tracer tracer; // Inject the tracer

    TextMapPropagator propagator = TextMapPropagator.noop(); // Inject the propagator

    @Incoming("my-channel")
    public void receive(String message) {
        // Extract the traceId and spanId from the message
        String[] parts = message.split("\\|");
        String traceId = null;
        String spanId = null;
        String actualMessage = parts[0];  // Assuming the first part is the actual message

        // Parse the trace context from the message
        for (String part : parts) {
            if (part.startsWith("traceId=")) {
                traceId = part.split("=")[1];
            } else if (part.startsWith("spanId=")) {
                spanId = part.split("=")[1];
            }
        }

        if (traceId != null && spanId != null) {
            // Manually create the span context using the extracted traceId and spanId
            SpanContext spanContext = SpanContext.createFromRemoteParent(
                    traceId,
                    spanId,
                    TraceFlags.getSampled(),  // Assume sampled=true for demonstration
                    TraceState.getDefault()
            );

            // Create a new span as a child of the extracted context
            Span span = tracer.spanBuilder("QUEUE | Consume RabbitMQ message")
                    .setParent(Context.root().with(Span.wrap(spanContext)))
                    .startSpan();

            log.info("Consumed message with Trace ID: {}, Span ID: {}", traceId, spanId);

            try {
                // Process the actual message content
                processMessage(actualMessage, spanContext);
            } finally {
                // Finish the span
                span.end();
            }
        } else {
            // Handle cases where trace context is missing
            log.warn("Trace context not found in message: {}", message);
        }
    }

    private void processMessage(String message, SpanContext spanContext) {
        // Your message processing logic goes here
        log.info("Processing message: {} with Trace ID: {}", message);

        // Call the downstream service with the context
        log.info("Calling downstream service...");

        // Create a new span as a child of the extracted context
        Span span = tracer.spanBuilder("QUEUE | Consume RabbitMQ message")
                .setParent(Context.root().with(Span.wrap(spanContext)))
                .startSpan();

        try (Scope scope = span.makeCurrent()) {

            // Inject the span context into the headers
            propagator.inject(Context.current(), message, new TextMapSetter<String>() {
                @Override
                public void set(String carrier, String key, String value) {
                    log.info("Injecting trace context into message: {}={} for key: {}", key, value, carrier);
                }
            });

            // Call the downstream service
            serviceGoClient.end();

        } catch (Exception e) {
            log.error("Error calling downstream service", e);
        } finally {
            // Finish the span
            span.end();
        }
    }
}