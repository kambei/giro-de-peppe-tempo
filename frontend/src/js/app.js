import { WebTracerProvider } from '@opentelemetry/sdk-trace-web';
import { BatchSpanProcessor } from '@opentelemetry/sdk-trace-base';
import { ConsoleSpanExporter } from '@opentelemetry/sdk-trace-base';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { trace } from '@opentelemetry/api';

// ✅ 1. Initialize OpenTelemetry Web Tracer Provider
const provider = new WebTracerProvider();

// ✅ 2. Use an OTLP Exporter (send traces to Tempo via HTTP)
const exporter = new OTLPTraceExporter({
    url: 'http://tempo:4318/v1/traces',  // Ensure Tempo is reachable
});

// ✅ 3. Attach an Exporter (BatchSpanProcessor)
provider.getTracerProvider().addSpanProcessor(new BatchSpanProcessor(exporter));

// ✅ 4. (Optional) Also log spans in the browser console for debugging
provider.getTracerProvider().addSpanProcessor(new BatchSpanProcessor(new ConsoleSpanExporter()));

provider.register();

console.log('OpenTelemetry Initialized');

// Helper function to create spans
function createSpan(name) {
    const tracer = trace.getTracer('frontend-app');
    return tracer.startSpan(name);
}

// ✅ Wait for DOM to be loaded
document.addEventListener('DOMContentLoaded', () => {
    console.log('Frontend app initialized');

    const button = document.getElementById('my-button');
    const resultElement = document.getElementById('result');

    button.addEventListener('click', () => {
        console.log('User clicked the button.');

        // Start a new span for this action
        const span = createSpan('User Clicked Button');

        resultElement.style.display = 'block';
        resultElement.innerHTML = `<p>Trace generated at: ${new Date().toLocaleTimeString()}</p>`;

        // Add traceparent header to propagate the trace
        const traceparent = span.spanContext().traceId;
        const spanId = span.spanContext().spanId;

        console.log(`Generated Trace ID: ${traceparent}`);
        console.log(`Generated Span ID: ${spanId}`);

        fetch('/api/hello', {
            headers: {
                'traceparent': `00-${traceparent}-${spanId}-01`
            }
        })
        .then(response => response.text())
        .then(data => {
            console.log('API response:', data);
            resultElement.innerHTML += `<p>API Response: ${data}</p>`;
        })
        .catch(error => {
            console.error('API error:', error);
            resultElement.innerHTML += `<p>API Error: ${error.message}</p>`;
        })
        .finally(() => {
            // End the span when the request completes
            span.end();
        });
    });
});
