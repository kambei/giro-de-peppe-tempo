package main

import (
	"context"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"

	"github.com/sirupsen/logrus"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/semconv/v1.17.0"
	"go.opentelemetry.io/otel/trace"
)

func initTracer() func() {
    ctx := context.Background()

    // Create an OTLP HTTP exporter (use HTTP exporter here)
    exporter, err := otlptracehttp.New(ctx, otlptracehttp.WithEndpoint(os.Getenv("OTLP_ENDPOINT")), otlptracehttp.WithInsecure())
    if err != nil {
        log.Fatalf("failed to create trace exporter: %v", err)
    }

    // Create a trace provider with error handling
    res, err := resource.New(ctx,
        resource.WithAttributes(
            semconv.ServiceNameKey.String("my-service"),
        ),
    )
    if err != nil {
        log.Fatalf("failed to create resource: %v", err)
    }

    // Create a trace provider
    tp := sdktrace.NewTracerProvider(
        sdktrace.WithSampler(sdktrace.AlwaysSample()),  // Sample all traces
        sdktrace.WithBatcher(exporter),
        sdktrace.WithResource(res),
    )
    
    // Set the global tracer provider
    otel.SetTracerProvider(tp)

    return func() {
        if err := tp.Shutdown(ctx); err != nil {
            log.Fatalf("failed to shut down trace provider: %v", err)
        }
    }
}

// logWithTrace logs the trace and span IDs using logrus
func logWithTrace(ctx context.Context) {
	span := trace.SpanFromContext(ctx)
	traceID := span.SpanContext().TraceID()
	spanID := span.SpanContext().SpanID()
	sampled := span.SpanContext().IsSampled()

	// Log with trace context information
	logrus.WithFields(logrus.Fields{
		"traceId": traceID.String(),
		"spanId":  spanID.String(),
		"sampled": sampled,
	}).Info("Logging with trace information")
}

func callExternalEndpoint(ctx context.Context) (string, error) {
    
    client := http.Client{
        Transport: http.DefaultTransport,
    }

    // Create the request using the same context
    req, err := http.NewRequestWithContext(ctx, "GET", "http://service-b:8082/hello", nil)
    if err != nil {
        return "", err
    }

    // Extract span from the context
    span := trace.SpanFromContext(ctx)
    if !span.SpanContext().IsValid() {
        fmt.Println("No valid span found in context!")
        return "", fmt.Errorf("no valid span found")
    }

    // Manually construct the traceparent header
    traceID := span.SpanContext().TraceID().String()
    spanID := span.SpanContext().SpanID().String()
    sampledFlag := "00" // Default not sampled
    if span.SpanContext().IsSampled() {
        sampledFlag = "01"
    }
    traceparent := fmt.Sprintf("00-%s-%s-%s", traceID, spanID, sampledFlag)

    // Manually inject the traceparent header
    req.Header.Set("traceparent", traceparent)

    // Log the outgoing headers
    fmt.Printf("Outgoing headers with manual traceparent injection: %+v\n", req.Header)

    // Perform the request
    resp, err := client.Do(req)
    if err != nil {
        return "", err
    }
    defer resp.Body.Close()

    // Read the response body
    body, err := io.ReadAll(io.Reader(resp.Body))
    if err != nil {
        return "", err
    }

    return string(body), nil
}

func logTraceHandler(w http.ResponseWriter, r *http.Request) {
    // Create the root span for the HTTP request
    tracer := otel.Tracer("service-go")
    ctx, span := tracer.Start(r.Context(), "log-trace")
    defer span.End()

    // Log the trace in the current span context
    logWithTrace(ctx)

    // Start a child span for the external service call
    childCtx, childSpan := tracer.Start(ctx, "Calling External Service")
    defer childSpan.End()

    // Call the external service in the context of the child span
    response, err := callExternalEndpoint(childCtx)
    if err != nil {
        http.Error(w, "Failed to call external endpoint", http.StatusInternalServerError)
        logrus.Error("Error calling external endpoint:", err)
        return
    }

    w.WriteHeader(http.StatusOK)
    fmt.Fprintf(w, "Trace logged successfully!\nResponse from external endpoint: %s", response)
}

func helloHandler(w http.ResponseWriter, r *http.Request) {
    // Manually extract traceparent header from the incoming request
    traceparent := r.Header.Get("traceparent")
    if traceparent == "" {
        http.Error(w, "Missing traceparent header", http.StatusBadRequest)
        return
    }

    // Log the traceparent value for debugging
    fmt.Printf("Extracted traceparent header: %s\n", traceparent)

    // Parse the traceparent header (assuming W3C traceparent format)
    var version, traceID, spanID, traceFlags string
    _, err := fmt.Sscanf(traceparent, "%2s-%32s-%16s-%2s", &version, &traceID, &spanID, &traceFlags)
    if err != nil {
        http.Error(w, "Invalid traceparent header", http.StatusBadRequest)
        return
    }

    // Convert traceID and spanID to OpenTelemetry SpanContext format
    parsedTraceID, err := trace.TraceIDFromHex(traceID)
    if err != nil {
        http.Error(w, "Invalid trace ID", http.StatusBadRequest)
        return
    }

    parsedSpanID, err := trace.SpanIDFromHex(spanID)
    if err != nil {
        http.Error(w, "Invalid span ID", http.StatusBadRequest)
        return
    }

    // Create a SpanContext from the extracted traceparent
    spanContext := trace.NewSpanContext(trace.SpanContextConfig{
        TraceID:    parsedTraceID,
        SpanID:     parsedSpanID,
        TraceFlags: trace.TraceFlags(0), // Default not sampled
    })

    // Set the span context manually into a new context
    ctx := trace.ContextWithRemoteSpanContext(r.Context(), spanContext)

    // Create a new tracer and start a span using the manually constructed context
    tracer := otel.Tracer("service-go")
    ctx, span := tracer.Start(ctx, "hello")
    defer span.End()

    // Call the extracted method to post to the external service
    response1, err := postToProduceEndpoint(ctx)
    if err != nil {
        http.Error(w, "Failed to call external endpoint", http.StatusInternalServerError)
        logrus.Error("Error calling external endpoint:", err)
        return
    }

    // Call the cpp-tempo service
    response2, err := callCppTempoHello(ctx)
    if err != nil {
        http.Error(w, "Failed to call cpp-tempo", http.StatusInternalServerError)
        logrus.Error("Error calling cpp-tempo:", err)
        return
    }

    logWithTrace(ctx)

    // Respond with a Hello Message
    w.WriteHeader(http.StatusOK)
    fmt.Fprintf(w, "Hello from service Go!!!\nResponse from external endpoint: %s\nResponse from cpp-tempo: %s", response1, response2)
}

func callCppTempoHello(ctx context.Context) (string, error) {
    
     // Create the request using the same context
     req, err := http.NewRequestWithContext(ctx, "GET", "http://cpp-tempo:8080/hello", nil)
     if err != nil {
         return "", err
     } 

    // Extract span from the context
    span := trace.SpanFromContext(ctx)
    if !span.SpanContext().IsValid() {
        fmt.Println("No valid span found in context!")
        return "", fmt.Errorf("no valid span found")
    }

    // Manually construct the traceparent header
    traceID := span.SpanContext().TraceID().String()
    spanID := span.SpanContext().SpanID().String()
    sampledFlag := "00" // Default not sampled
    if span.SpanContext().IsSampled() {
        sampledFlag = "01"
    }
    traceparent := fmt.Sprintf("00-%s-%s-%s", traceID, spanID, sampledFlag)

    // Manually inject the traceparent header
    req.Header.Set("traceparent", traceparent)


    // Log the outgoing headers for debugging
    fmt.Printf("Outgoing headers with traceparent: %+v\n", req.Header)

    // Perform the request
    resp, err := http.DefaultClient.Do(req)
    if err != nil {
        span.RecordError(err)
        span.SetStatus(codes.Error, "Failed to call cpp-tempo")
        return "", fmt.Errorf("failed to call cpp-tempo: %v", err)
    }
    defer resp.Body.Close()

    // Read the response body
    body, err := io.ReadAll(io.Reader(resp.Body))
    if err != nil {
        span.RecordError(err)
        span.SetStatus(codes.Error, "Failed to read response body")
        return "", fmt.Errorf("failed to read response body: %v", err)
    }

    // If successful, mark the span as OK
    span.SetStatus(codes.Ok, "Successfully called cpp-tempo")

    return string(body), nil
}

func postToProduceEndpoint(ctx context.Context) (string, error) {
    // Create a tracer and start a span for this method
    tracer := otel.Tracer("service-go")
    ctx, span := tracer.Start(ctx, "POST to /hello/produce")
    defer span.End()

    // Extract the trace context from the current span
    spanContext := span.SpanContext()
    traceID := spanContext.TraceID().String()
    spanID := spanContext.SpanID().String()
    sampledFlag := "00"
    if spanContext.IsSampled() {
        sampledFlag = "01"
    }

    // Manually construct the traceparent header
    traceparent := fmt.Sprintf("00-%s-%s-%s", traceID, spanID, sampledFlag)

    // Create the request using the same context
    req, err := http.NewRequestWithContext(ctx, "POST", "http://service-b:8082/hello/produce?message=ciao", nil)
    if err != nil {
        span.RecordError(err)
        span.SetStatus(codes.Error, "Failed to create request")
        return "", fmt.Errorf("failed to create request: %v", err)
    }

    // Inject the traceparent header into the outgoing request
    req.Header.Set("traceparent", traceparent)

    // Log the outgoing headers for debugging
    fmt.Printf("Outgoing headers with traceparent: %+v\n", req.Header)

    // Perform the request
    resp, err := http.DefaultClient.Do(req)
    if err != nil {
        span.RecordError(err)
        span.SetStatus(codes.Error, "Failed to call external endpoint")
        return "", fmt.Errorf("failed to call external endpoint: %v", err)
    }
    defer resp.Body.Close()

    // Read the response body
    body, err := io.ReadAll(io.Reader(resp.Body))
    if err != nil {
        span.RecordError(err)
        span.SetStatus(codes.Error, "Failed to read response body")
        return "", fmt.Errorf("failed to read response body: %v", err)
    }

    // If successful, mark the span as OK
    span.SetStatus(codes.Ok, "Successfully called external endpoint")

    return string(body), nil
}

func endHandler(w http.ResponseWriter, r *http.Request) {
    // Estrai l'intestazione traceparent dalla richiesta
    traceparent := r.Header.Get("traceparent")
    if traceparent == "" {
        http.Error(w, "Missing traceparent header", http.StatusBadRequest)
        return
    }

    // Log the traceparent value for debugging
    fmt.Printf("Extracted traceparent header: %s\n", traceparent)

    // Parse the traceparent header (assuming W3C traceparent format)
    var version, traceID, spanID, traceFlags string
    _, err := fmt.Sscanf(traceparent, "%2s-%32s-%16s-%2s", &version, &traceID, &spanID, &traceFlags)
    if err != nil {
        http.Error(w, "Invalid traceparent header", http.StatusBadRequest)
        return
    }

    // Convert traceID and spanID to OpenTelemetry SpanContext format
    parsedTraceID, err := trace.TraceIDFromHex(traceID)
    if err != nil {
        http.Error(w, "Invalid trace ID", http.StatusBadRequest)
        return
    }

    parsedSpanID, err := trace.SpanIDFromHex(spanID)
    if err != nil {
        http.Error(w, "Invalid span ID", http.StatusBadRequest)
        return
    }

    // Create a SpanContext from the extracted traceparent
    spanContext := trace.NewSpanContext(trace.SpanContextConfig{
        TraceID:    parsedTraceID,
        SpanID:     parsedSpanID,
        TraceFlags: trace.TraceFlags(0), // Default not sampled
    })

    // Set the span context manually into a new context
    ctx := trace.ContextWithRemoteSpanContext(r.Context(), spanContext)

    // Create a new tracer and start a span using the manually constructed context
    tracer := otel.Tracer("service-go")
    ctx, span := tracer.Start(ctx, "endHandler")
    defer span.End()

    // Log the trace in the current span context
    logWithTrace(ctx)

    fmt.Println("Giro de Peppe... completed!")

    // Respond with the completion message
    w.WriteHeader(http.StatusOK)
    fmt.Fprintf(w, "Giro de Peppe... completed!")
}

func main() {
	shutdown := initTracer()
	defer shutdown()

	logrus.SetFormatter(&logrus.TextFormatter{
		TimestampFormat: "15:04:05",
		FullTimestamp:   true,
	})

	http.HandleFunc("/log-trace", logTraceHandler)

    http.HandleFunc("/hello", helloHandler)

    http.HandleFunc("/end", endHandler)

	log.Println("Starting server on :8080")
	if err := http.ListenAndServe(":8080", nil); err != nil {
		log.Fatalf("could not start server: %v", err)
	}
}
