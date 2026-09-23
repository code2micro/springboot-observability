# Payment Service Observability Demo

This project is a realistic Spring Boot microservice designed to demonstrate the three pillars of observability in a production-style payment workflow:

- Logs: What happened?
- Metrics: Is there a problem?
- Traces: Where is the problem?

The application models a payment processing flow with PostgreSQL persistence, mock downstream validation, a slow third-party Scorify API, structured logging, correlation IDs, Prometheus scraping, Grafana dashboards, and OpenTelemetry tracing.

## Architecture

Client
  |
  v
Payment Service
  |
  +----> Account Service / mock downstream API
  |
  +----> Scorify / mock third-party API
  |
  +----> PostgreSQL

The application exposes a main business API:

POST /api/payments

Example payload:

{
  "transactionId": "TXN1001",
  "accountId": "ACC100",
  "amount": 5000
}

## Project structure

.
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── prometheus.yml
├── README.md
├── src
│   ├── main
│   │   ├── java/com/example/payment
│   │   │   ├── client
│   │   │   ├── config
│   │   │   ├── controller
│   │   │   ├── dto
│   │   │   ├── entity
│   │   │   ├── exception
│   │   │   ├── filter
│   │   │   ├── metrics
│   │   │   ├── repository
│   │   │   ├── service
│   │   │   ├── util
│   │   │   └── PaymentApplication.java
│   │   └── resources
│   │       └── application.yml
│   └── test
│       ├── java/com/example/payment
│       └── resources/application-test.yml
└── .gitignore

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker and Docker Compose
- curl

## Build with Maven

```bash
cd /workspaces/springboot-observability
mvn clean package
```

## Run with Docker Compose

```bash
cd /workspaces/springboot-observability
docker compose up --build
```

This starts:

- payment-service on port 8080
- PostgreSQL on port 5432
- Prometheus on port 9090
- Grafana on port 3000
- Jaeger / OpenTelemetry collector on ports 16686, 4317, 4318

## Call the payment API

### Standard request

```bash
curl -i -X POST http://localhost:8080/api/payments \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-ID: CORR-1001' \
  -d '{
    "transactionId": "TXN1001",
    "accountId": "ACC100",
    "amount": 5000
  }'
```

### Without correlation ID

The filter generates a correlation ID automatically if the client does not provide one.

```bash
curl -i -X POST http://localhost:8080/api/payments \
  -H 'Content-Type: application/json' \
  -d '{
    "transactionId": "TXN1002",
    "accountId": "ACC101",
    "amount": 2500
  }'
```

## Simulate Scorify latency

The mock Scorify API accepts a delay query parameter:

```bash
curl -i 'http://localhost:8080/mock/scorify?transactionId=TXN1003&amount=1500&delay=10000'
```

This simulates a 10-second third-party delay to reproduce the latency scenario described in the interview example.

## Simulate Scorify failure

### HTTP 500

```bash
curl -i 'http://localhost:8080/mock/scorify?transactionId=TXN1004&amount=2000&status=500'
```

### HTTP 504

```bash
curl -i 'http://localhost:8080/mock/scorify?transactionId=TXN1005&amount=2000&status=504'
```

### Timeout / network issue

The client uses a 3-second read timeout and a 2-second connect timeout. A long delay, for example 10000ms, reproduces a slow upstream response and appears in the trace as a timeout-like delay.

## Check logs

```bash
docker compose logs -f payment-service
```

The log format includes both trace and correlation IDs:

```text
INFO [traceId=... correlationId=CORR-1001] Payment received transactionId=TXN1001 ...
ERROR [traceId=... correlationId=CORR-1001] Payment failed transactionId=TXN1001 ...
```

## Check actuator metrics

Open:

- http://localhost:8080/actuator/health
- http://localhost:8080/actuator/info
- http://localhost:8080/actuator/prometheus

## Query Prometheus

Open http://localhost:9090 and use the Prometheus UI to inspect metrics such as:

```promql
rate(http_server_requests_seconds_count{job="payment-service"}[5m])
```

Useful queries:

```promql
sum(rate(http_server_requests_seconds_count{job="payment-service"}[5m]))
```

```promql
sum(rate(http_server_requests_seconds_count{job="payment-service", status=~"5.."}[5m]))
  /
sum(rate(http_server_requests_seconds_count{job="payment-service"}[5m]))
```

```promql
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job="payment-service"}[5m])) by (le))
```

```promql
jvm_memory_used_bytes{area="heap"}
```

```promql
process_cpu_usage
```

```promql
rate(payment_success_count_total[5m])
```

```promql
rate(payment_failure_count_total[5m])
```

```promql
histogram_quantile(0.95, sum(rate(scorify_call_duration_seconds_bucket[5m])) by (le))
```

## Open Grafana

Open http://localhost:3000

Login with:

- username: admin
- password: admin

Then add Prometheus as a data source:

1. Click Configuration > Data Sources > Add data source.
2. Select Prometheus.
3. Set URL to http://prometheus:9090
4. Save and test.

Then create dashboards with metrics such as:

- HTTP request rate
- HTTP error rate
- 95th percentile latency
- JVM memory
- CPU usage
- Payment success rate
- Payment failure rate
- Scorify latency

## Inspect distributed traces

Open Jaeger at:

http://localhost:16686

Search for the transaction in the service named payment-service and inspect spans that include:

- PaymentController
- PaymentService
- Account Service call
- Scorify call
- PostgreSQL persistence

## Identify the slow Scorify call

A slow downstream call looks like this in the trace timeline:

- PaymentService: 200 ms
- Account Service: 300 ms
- Scorify: 10,000 ms
- PaymentService: returns failure after timeout

The metrics show the latency spike, the trace shows where it occurred, and the logs show the exact error and transaction ID.

## Observability workflow

The application demonstrates the real troubleshooting path:

Metrics -> Is there a problem?
Traces -> Where is the problem?
Logs -> What exactly happened?

Example sequence:

1. Prometheus/Grafana shows response time increasing from ~1s to ~12s.
2. OpenTelemetry tracing shows 11s spent in the Scorify call.
3. The trace ID or correlation ID is used to search logs.
4. The logs reveal the timeout and downstream failure details.

## Interview explanation

### Correlation ID vs Trace ID

- Correlation ID: business-level request identifier for tracking a client transaction across services and logs.
- Trace ID: distributed tracing identifier used by OpenTelemetry to link spans for a single request flow.
- Span ID: unique identifier for an individual operation within a trace.

### Prometheus vs Grafana

- Prometheus stores and scrapes time-series metrics.
- Grafana visualizes those metrics with dashboards and alerts.

### Logs vs Metrics vs Traces

- Logs show events and context.
- Metrics show behavior over time and alerting signals.
- Traces show the end-to-end execution path across services.

### Actuator

Spring Boot Actuator exposes operational endpoints such as health and metrics in a standards-based way.

### Micrometer

Micrometer is the vendor-neutral metrics facade used by Spring Boot. It integrates with Prometheus and other monitoring backends.

### OpenTelemetry

OpenTelemetry provides a vendor-neutral standard for collecting tracing, metrics, and logs. It is a strong fit for modern microservices.

### Monitoring vs Observability

- Monitoring: checking known symptoms and thresholds.
- Observability: understanding the internal state of a system from external outputs.

### Troubleshooting production latency

Check metrics for latency increase, inspect traces for the slowest span, then search logs using correlation ID or trace ID to determine the root cause.

### Monitoring downstream APIs

Use timers, counters, error counters, and distributed traces to track latency and failure rates for third-party dependencies.

### Observability in Kubernetes

In Kubernetes, each pod exports metrics and logs, and distributed tracing ties requests together across multiple services and node boundaries. Prometheus and Grafana are commonly used together with OpenTelemetry or vendor-native agents.

## Useful curl commands

### Normal success

```bash
curl -i -X POST http://localhost:8080/api/payments \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-ID: CORR-OK-1' \
  -d '{"transactionId":"TXN1001","accountId":"ACC100","amount":5000}'
```

### Slow Scorify

```bash
curl -i -X POST http://localhost:8080/api/payments \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-ID: CORR-SLOW-1' \
  -d '{"transactionId":"TXN1007","accountId":"ACC100","amount":6000}'
```

Then call:

```bash
curl -i 'http://localhost:8080/mock/scorify?transactionId=TXN1007&amount=6000&delay=10000'
```

### HTTP 500 failure

```bash
curl -i 'http://localhost:8080/mock/scorify?transactionId=TXNFAIL500&amount=5000&status=500'
```

### HTTP 504 failure

```bash
curl -i 'http://localhost:8080/mock/scorify?transactionId=TXNFAIL504&amount=5000&status=504'
```

## Notes

This project is intentionally simple but realistic. It demonstrates how observability practices can be used in a real enterprise payment workflow while remaining easy to run locally.
