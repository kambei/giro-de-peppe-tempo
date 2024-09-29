package com.sigeosrl.serviceb.queue;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import java.util.concurrent.*;

@ApplicationScoped
@Slf4j
public class RabbitMQProducer {

    @Inject
    Tracer tracer;

    private final BlockingQueue<MessageWithContext> messageQueue = new LinkedBlockingQueue<>(); // Queue for message and context

    @Outgoing("my-channel")
    public CompletionStage<String> produce() {
        // Create an ExecutorService with virtual threads
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Retrieve the next message from the queue (simulated here)
                MessageWithContext messageWithContext = messageQueue.take();
                String message = messageWithContext.getMessage();
                Context parentContext = messageWithContext.getContext(); // Use the full context

                // Create a new span for message production
                Span span = tracer.spanBuilder("QUEUE | Produce message")
                        .setParent(parentContext)  // Ensure correct parent context
                        .startSpan();

                // Extract trace context (traceId, spanId) from the span
                String traceId = span.getSpanContext().getTraceId();
                String spanId = span.getSpanContext().getSpanId();

                // Embed traceId and spanId into the message
                String messageWithTrace = message + "|traceId=" + traceId + "|spanId=" + spanId;

                log.info("Trace ID: {}, Span ID: {}", traceId, spanId);
                log.info("Producing message: {}", messageWithTrace);

                try {
                    // Return the modified message with trace context embedded
                    return messageWithTrace;
                } catch (Exception e) {
                    // Capture exceptions
                    span.recordException(e);
                    throw e;
                } finally {
                    // Finish the span
                    span.end();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Failed to produce message", e);
            }
        }, executorService).whenComplete((result, ex) -> {
            // Shutdown the executor service after the task completes
            executorService.shutdown();
        });
    }

    // Method to add a message to the queue along with the trace context
    public void addMessageToQueueWithContext(String message, Context context) {  // Pass full Context

        // Add the message and trace context to the queue
        messageQueue.add(new MessageWithContext(message, context));  // Store full Context
    }

    // Helper class to store message with trace context
    private static class MessageWithContext {
        private final String message;
        private final Context context;

        public MessageWithContext(String message, Context context) {
            this.message = message;
            this.context = context;
        }

        public String getMessage() {
            return message;
        }

        public Context getContext() {
            return context;
        }
    }
}
