# Giro de Peppe: Distributed Tracing Playground

## Overview

Giro de Peppe is a playground project designed to demonstrate distributed tracing across multiple services implemented in different programming languages. The project showcases the integration between:

- **Quarkus** (Java) - Two microservices (service-a and service-b)
- **Go** - A Go-based service
- **C++** - A C++ service
- **Tempo** - Distributed tracing backend
- **Grafana** - Visualization platform for traces

This documentation provides comprehensive information about the project architecture, components, setup instructions, and usage examples.

## Architecture

The project implements a distributed system with multiple services that communicate with each other. Each service is instrumented with OpenTelemetry to capture traces, which are then sent to Tempo for storage and visualization through Grafana.

### System Components

![Architecture Diagram](imgs/giro-de-peppe.png)

#### Services

1. **Service B (Quarkus)** - Entry point service that:
   - Exposes REST endpoints
   - Communicates with Service A
   - Produces messages to RabbitMQ
   - Instruments traces with OpenTelemetry

2. **Service A (Quarkus)** - Intermediate service that:
   - Communicates with the Go service
   - Consumes messages from RabbitMQ
   - Instruments traces with OpenTelemetry

3. **Go Service** - Backend service implemented in Go that:
   - Exposes REST endpoints
   - Instruments traces with OpenTelemetry

4. **C++ Service** - Service implemented in C++ that:
   - Exposes HTTP endpoints
   - Instruments traces with OpenTelemetry

#### Infrastructure

1. **Tempo** - Distributed tracing backend that:
   - Collects traces from all services
   - Stores traces for querying
   - Supports multiple trace protocols (OTLP, Jaeger, Zipkin)

2. **Grafana** - Visualization platform that:
   - Connects to Tempo as a data source
   - Provides UI for exploring and analyzing traces

3. **RabbitMQ** - Message broker that:
   - Facilitates asynchronous communication between services
   - Preserves trace context across message boundaries

## Communication Flow

The typical request flow through the system is:

1. Client sends a request to Service B
2. Service B processes the request and calls Service A
3. Service A calls the Go Service
4. Each service contributes spans to the trace
5. All spans are sent to Tempo
6. Traces can be visualized in Grafana

## Tracing Implementation

### OpenTelemetry Integration

Each service is instrumented with OpenTelemetry to capture traces:

- **Quarkus Services**: Use Quarkus OpenTelemetry extension
- **Go Service**: Uses OpenTelemetry Go SDK
- **C++ Service**: Uses OpenTelemetry C++ SDK

### Trace Context Propagation

Trace context is propagated across service boundaries using:

- HTTP headers for REST calls
- Message properties for RabbitMQ messages

This ensures that spans from different services are correctly associated with the same trace.

## Setup and Installation

### Prerequisites

- Docker and Docker Compose
- Git

### Installation Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/kambei/giro-de-peppe-tempo.git
   cd giro-de-peppe-tempo
   ```

2. Pull the Docker images:
   ```bash
   docker compose pull
   ```

3. Start the services:
   ```bash
   docker compose up -d
   ```

## Usage

### Testing the Distributed Tracing

1. Send a request to the entry point service:
   ```bash
   curl http://localhost:1666/hello
   ```

2. Access Grafana at [http://localhost:3000](http://localhost:3000)

3. Configure Tempo as a data source in Grafana:
   - URL: http://tempo:3200

4. Explore traces in the Grafana Explore view:
   - Select Tempo as the data source
   - Use the query interface to find traces

![Grafana Explore View](imgs/Schermata.png)

### Producing Messages to RabbitMQ

To test the message queue tracing:

```bash
curl -X POST "http://localhost:1666/hello/produce?message=test-message"
```

This will:
1. Create a trace in Service B
2. Send a message to RabbitMQ
3. Service A will consume the message
4. The trace context will be preserved across the message boundary

## Component Details

### Service B (Quarkus)

Service B is the entry point for the system and is implemented using Quarkus. It exposes REST endpoints and communicates with Service A.

Key files:
- `service-b/src/main/java/com/sigeosrl/serviceb/resource/TestResource.java`: REST endpoints
- `service-b/src/main/java/com/sigeosrl/serviceb/external/ServiceAClient.java`: Client for Service A
- `service-b/src/main/java/com/sigeosrl/serviceb/queue/RabbitMQProducer.java`: RabbitMQ producer

Configuration:
- OpenTelemetry exporter endpoint: `http://tempo:4317`
- Service A URL: `http://service-a:8081`
- RabbitMQ host: `queue`
- RabbitMQ port: `5672`

### Service A (Quarkus)

Service A is an intermediate service implemented using Quarkus. It communicates with the Go service and consumes messages from RabbitMQ.

Key files:
- `service-a/src/main/java/com/sigeosrl/servicea/resource/TestResource.java`: REST endpoints
- `service-a/src/main/java/com/sigeosrl/servicea/external/ServiceGoClient.java`: Client for Go service
- `service-a/src/main/java/com/sigeosrl/servicea/queue/RabbitMQConsumer.java`: RabbitMQ consumer

Configuration:
- OpenTelemetry exporter endpoint: `http://tempo:4317`
- Go service URL: `http://service-go:8080`
- RabbitMQ host: `queue`
- RabbitMQ port: `5672`

### Go Service

The Go service is a backend service implemented in Go. It exposes REST endpoints and is instrumented with OpenTelemetry.

Key file:
- `go/main.go`: Main Go service implementation

Configuration:
- OpenTelemetry exporter endpoint: `tempo:4318`

### C++ Service

The C++ service is implemented using C++ and Boost.Beast for HTTP. It is instrumented with OpenTelemetry.

Key file:
- `cpp/main.cpp`: Main C++ service implementation

Configuration:
- OpenTelemetry exporter endpoint: `tempo:4318`

### Tempo

Tempo is a distributed tracing backend that collects traces from all services.

Configuration:
- HTTP listen port: `3200`
- Receivers: Jaeger, Zipkin, OTLP, OpenCensus
- Trace retention: 1 hour (for demo purposes)

### Grafana

Grafana is used to visualize traces collected by Tempo.

Configuration:
- Anonymous authentication enabled
- Anonymous user role: Admin
- Login form disabled
- Feature toggles: traceqlEditor, traceQLStreaming, metricsSummary

## Development

### Building Services Locally

#### Service A and Service B (Quarkus)

```bash
cd service-a
./gradlew build
```

```bash
cd service-b
./gradlew build
```

#### Go Service

```bash
cd go
go build -o service-go
```

#### C++ Service

```bash
cd cpp
mkdir build && cd build
cmake ..
make
```

## Troubleshooting

### Common Issues

1. **Services not starting**: Check Docker logs with `docker compose logs`
2. **Traces not appearing in Grafana**: 
   - Verify Tempo data source configuration
   - Check that services are correctly sending traces to Tempo
   - Verify that the trace retention period hasn't expired

3. **Connection issues between services**:
   - Check that all services are running with `docker compose ps`
   - Verify network connectivity between containers

## Conclusion

Giro de Peppe demonstrates how to implement distributed tracing across a polyglot microservices architecture. By using OpenTelemetry for instrumentation and Tempo for trace collection, the project showcases a modern approach to observability in distributed systems.

The playground provides a practical example of:
- Instrumenting services in different languages
- Propagating trace context across service boundaries
- Visualizing distributed traces

This makes it an excellent learning resource for understanding distributed tracing concepts and implementation techniques.
