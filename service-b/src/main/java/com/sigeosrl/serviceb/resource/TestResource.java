package com.sigeosrl.serviceb.resource;

import com.sigeosrl.serviceb.external.ServiceAClient;
import com.sigeosrl.serviceb.queue.RabbitMQProducer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

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
    public Response hello() {

        Response response = serviceAClient.hello();

        if (response.getStatus() != 200) {
            return Response.serverError().build();
        } else {
            return Response.ok("Hello from Service B, that called Service A too: " + response.readEntity(String.class)).build();
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

}
