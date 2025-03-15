import { WebTracerProvider } from 'https://unpkg.com/@opentelemetry/sdk-trace-web?module';
import { BatchSpanProcessor, SimpleSpanProcessor } from 'https://unpkg.com/@opentelemetry/sdk-trace-base?module';
import { ConsoleSpanExporter } from 'https://unpkg.com/@opentelemetry/sdk-trace-base?module';
import { OTLPTraceExporter } from 'https://unpkg.com/@opentelemetry/exporter-trace-otlp-http?module';
import { trace } from 'https://unpkg.com/@opentelemetry/api?module';
import { Resource } from 'https://unpkg.com/@opentelemetry/resources?module';
import { SEMRESATTRS_SERVICE_NAME } from 'https://unpkg.com/@opentelemetry/semantic-conventions?module';

// ✅ Initialize OpenTelemetry
const provider = new WebTracerProvider({
    resource: new Resource({
        [SEMRESATTRS_SERVICE_NAME]: 'frontend-app',
    }),
});

// ✅ Configure Exporters
const otlpExporter = new OTLPTraceExporter({
    url: 'http://tempo:4318/v1/traces', // Ensure Tempo is reachable
});

provider.addSpanProcessor(new BatchSpanProcessor(otlpExporter)); // Send traces to Tempo
provider.addSpanProcessor(new SimpleSpanProcessor(new ConsoleSpanExporter())); // Debugging

provider.register();

console.log('🚀 OpenTelemetry Initialized');

// ✅ Helper function to create spans
function createSpan(name) {
    const tracer = trace.getTracer('frontend-app');
    return tracer.startSpan(name);
}

// ✅ Handle Button Click
function handleButtonClick() {
    console.log('User clicked the button');

    const resultElement = document.getElementById('result');
    resultElement.style.display = 'block';

    // ✅ Start a new span
    const span = createSpan('User Clicked Button');

    // ✅ Generate trace headers
    const traceId = span.spanContext().traceId;
    const spanId = span.spanContext().spanId;
    const traceparent = `00-${traceId}-${spanId}-01`;

    console.log(`🛠️ Generated Trace ID: ${traceId}`);
    console.log(`🛠️ Generated Span ID: ${spanId}`);
    console.log(`🛠️ Generated Traceparent: ${traceparent}`);

    resultElement.innerHTML = `
        <div class="trace-info">
            <p>Trace generated at: ${new Date().toLocaleTimeString()}</p>
            <p>Trace ID: ${traceId}</p>
            <p>Span ID: ${spanId}</p>
        </div>
    `;

    // ✅ Send API request with traceparent header
    fetch('/api/hello', {
        headers: { 'traceparent': traceparent }
    })
    .then(response => response.text())
    .then(data => {
        console.log('✅ API response:', data);
        resultElement.innerHTML += `<div class="api-response"><p>API Response: ${data}</p></div>`;
    })
    .catch(error => {
        console.error('❌ API error:', error);
        resultElement.innerHTML += `<div class="api-error"><p>API Error: ${error.message}</p></div>`;
    })
    .finally(() => {
        span.end();
        provider.forceFlush().then(() => console.log("✅ Traces flushed to Tempo!"))
        .catch(err => console.error("❌ Trace export failed:", err));
    });
}

// ✅ Attach event listener
document.getElementById('traceButton').addEventListener('click', handleButtonClick);
console.log('Frontend app initialized');
