package com.sigeosrl.servicea.resource;

import com.sigeosrl.servicea.external.ServiceGoClient;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.HashMap;
import java.util.Map;

@Path("/hello")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class TestResource {

    @Inject
    Tracer tracer;

    @Inject
    @RestClient
    ServiceGoClient serviceGoClient;

    @ConfigProperty(name = "service-go.enabled")
    boolean serviceGoEnabled;

    @GET
    public Response hello(@Context HttpHeaders headers) {
        // ✅ Extract `traceparent` from incoming request
        String traceparent = headers.getHeaderString("traceparent");
        log.info("📌 Received Traceparent in Service A: {}", traceparent);

        // ✅ Extract OpenTelemetry context using the correct carrier and getter
        io.opentelemetry.context.Context extractedContext = extractContextFromHeaders(headers);

        Span span = tracer.spanBuilder("service-a-hello")
                .setParent(extractedContext)
                .startSpan();

        try {
            if (serviceGoEnabled) {
                Response response = serviceGoClient.hello();
                return Response.ok("Hello from Service A, that called Service Go: " + response.readEntity(String.class)).build();
            }
            return Response.ok("Hello from Service A").build();
        } finally {
            span.end();
        }
    }

    // ✅ Properly extract OpenTelemetry context from headers
    private io.opentelemetry.context.Context extractContextFromHeaders(HttpHeaders headers) {
        TextMapPropagator propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();

        // Convert `HttpHeaders` to a `Map<String, String>`
        Map<String, String> headerMap = new HashMap<>();
        headers.getRequestHeaders().forEach((key, values) -> {
            if (!values.isEmpty()) {
                headerMap.put(key, values.getFirst()); // Take the first value from the list
            }
        });

        // ✅ Use `TextMapGetter<Map<String, String>>`
        return propagator.extract(io.opentelemetry.context.Context.current(), headerMap, new TextMapGetter<Map<String, String>>() {
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
