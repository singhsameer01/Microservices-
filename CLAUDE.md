# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

Each service is an independent Maven module — there is no parent/aggregator `pom.xml`.

```bash
# Build a single service (skip tests)
mvn -f <service>/pom.xml clean package -DskipTests

# Build and run tests for a single service
mvn -f <service>/pom.xml clean package

# Run tests only
mvn -f <service>/pom.xml test

# Run a single test class
mvn -f <service>/pom.xml test -Dtest=ClassName

# Start a service locally
mvn -f <service>/pom.xml spring-boot:run
```

Replace `<service>` with: `eureka-server`, `api-gateway`, `user-service`, `product-service`, `order-service`, `payment-service`, or `notification-service`.

## Infrastructure (Docker Compose)

The microservice blocks in `docker-compose.yml` are commented out pending Dockerfiles. Start only infrastructure:

```bash
docker-compose up -d zookeeper kafka zipkin prometheus grafana
```

| Service   | Port |
|-----------|------|
| Eureka    | 8761 |
| API Gateway | 8080 |
| user-service | 8081 |
| product-service | 8082 |
| order-service | 8083 |
| payment-service | 8084 |
| notification-service | 8085 |
| Kafka     | 9092 |
| Zipkin    | 9411 |
| Prometheus | 9090 |
| Grafana   | 3000 (admin/admin) |

**Missing prerequisite**: `prometheus.yml` must be created at the project root with scrape configs for all `/actuator/prometheus` endpoints before Prometheus can start.

## Architecture Overview

Spring Boot 3.2.0 / Spring Cloud 2023.0.0 / Java 17 e-commerce platform.

```
Client → API Gateway (8080)
           ├── Routes to user-service, product-service, order-service, payment-service
           └── JWT validation (GlobalFilter — not yet implemented)

Eureka Server (8761) ← all services register and discover each other

order-service ──OpenFeign──→ product-service  (check stock)
order-service ──OpenFeign──→ payment-service  (initiate payment)

order-service ──Kafka──→ notification-service  (order events)
payment-service ──Kafka──→ notification-service (payment events)

All services → Zipkin (distributed tracing, 100% sampling)
All services → Prometheus/Grafana (metrics via /actuator/prometheus)
```

**Databases**: All services use H2 in-memory (`ddl-auto: create-drop`) — no external DB required for local dev.

**Kafka topic packages**: `com.learn.*` (trusted package for deserialization).

**JWT**: Secret `404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970` must match in both `user-service` and `api-gateway`. Expiry: 24 hours.

**Resilience4j circuit breakers**:
- `order-service` wraps calls to `payment-service` (window=10, 50% threshold, 10s wait)
- `payment-service` wraps calls to `bank-gateway` (window=5, 60% threshold, 15s wait)

## What Still Needs to Be Built

- **Dockerfiles**: Multi-stage builds (eclipse-temurin:17-jdk-alpine) in each service directory — required before uncommenting docker-compose microservice blocks
- **prometheus.yml**: Scrape config at repo root for all service `/actuator/prometheus` endpoints
- **api-gateway**: JWT `GlobalFilter`, `@EnableDiscoveryClient`
- **order-service**: Feign clients (`@FeignClient` for product-service and payment-service), `@CircuitBreaker` annotation, Kafka producer
- **notification-service**: Kafka listener implementation, `logback-spring.xml` for structured JSON logging
- **k8s manifests**: Uncomment probes/resource limits, set real Docker Hub image names

## CI/CD

`.github/workflows/ci-cd.yml` runs three jobs on push to `main`/`develop`:
1. **build** — matrix build (`mvn clean package -DskipTests` then `mvn test`) for all 7 services
2. **docker-build** — builds and pushes images to Docker Hub (requires `DOCKER_USERNAME` + `DOCKER_PASSWORD` secrets)
3. **deploy** — applies `k8s/` manifests (requires `KUBECONFIG` secret)

Kubernetes manifests are in `k8s/` with one file per service. The `order-service.yml` includes a commented-out HPA block (min 1 / max 5 replicas, 70% CPU).

## Key Reference Files

- `HINTS.md` — implementation hints per service (JWT filter code, Feign client examples, Kafka producer/consumer patterns, Dockerfile template, prometheus.yml template)
- `LEARNING_GUIDE.md` — chapter-by-chapter build order for the full system
