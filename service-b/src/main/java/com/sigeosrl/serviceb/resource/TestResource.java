package com.sigeosrl.serviceb.resource;

import com.sigeosrl.serviceb.external.ServiceAClient;
import com.sigeosrl.serviceb.queue.RabbitMQProducer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.HashMap;
import java.util.Map;

@Path("/hello")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class TestResource {

    @Inject
    @RestClient
    ServiceAClient serviceAClient;

    @Inject
    RabbitMQProducer rabbitMQProducer;

    @Inject
    Tracer tracer;

    @GET
    public Response hello(@jakarta.ws.rs.core.Context HttpHeaders headers) {

        // ✅ Extract `traceparent` from frontend request
        String traceparent = headers.getHeaderString("traceparent");
        log.info("📌 Received Traceparent in Service B: {}", traceparent);

        // ✅ Propagate the trace context
        Context extractedContext = extractContextFromHeaders(headers);
        Span span = tracer.spanBuilder("service-b-hello")
                .setParent(extractedContext)
                .startSpan();

        try {
            Response response = serviceAClient.hello(traceparent);

            if (response.getStatus() != 200) {
                return Response.serverError().build();
            } else {
                return Response.ok("Hello from Service B, that called Service A too: " + response.readEntity(String.class)).build();
            }
        } finally {
            span.end();
        }
    }

    @POST
    @Path("/produce")
    public Response produce(@QueryParam("message") String message) {
        // Start a span for the HTTP request
        Span span = tracer.spanBuilder("HTTP request processing").startSpan();
        log.info("HTTP Trace ID: {}, HTTP Span ID: {}", span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId());
        Context context = Context.current().with(span);

        try {
            // Add the message along with the trace context to the queue
            rabbitMQProducer.addMessageToQueueWithContext(message, context);  // Pass full Context
            return Response.ok("Message produced").build();
        } finally {
            span.end();  // End the HTTP request span here after adding to the queue
        }
    }

    // ✅ Properly extract OpenTelemetry context from headers
    private Context extractContextFromHeaders(HttpHeaders headers) {
        TextMapPropagator propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();

        // Convert `HttpHeaders` to a `Map<String, String>`
        Map<String, String> headerMap = new HashMap<>();
        headers.getRequestHeaders().forEach((key, values) -> {
            if (!values.isEmpty()) {
                headerMap.put(key, values.getFirst()); // Take the first value from the list
            }
        });

        // ✅ Use `TextMapGetter<Map<String, String>>`
        return propagator.extract(Context.current(), headerMap, new TextMapGetter<Map<String, String>>() {
            @Override
            public String get(Map<String, String> carrier, String key) {
                return carrier.getOrDefault(key, null);
            }

            @Override
            public Iterable<String> keys(Map<String, String> carrier) {
                return carrier.keySet();
            }
        });
    }
}
