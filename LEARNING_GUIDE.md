# Learning Guide — Java Microservices E-Commerce Platform

## Domain Overview
An e-commerce platform decomposed into 7 microservices. Each service maps to specific handbook chapters.

```
Client
  │
  ▼
api-gateway (8080)          ← single entry point
  │
  ├── user-service (8081)   ← auth & JWT
  ├── product-service (8082)← catalog
  ├── order-service (8083)  ← orchestrator
  └── payment-service (8084)← payments

order-service  ──Kafka──►  payment-service
payment-service ──Kafka──► notification-service (8085)

All services register with eureka-server (8761)
```

---

## Service → Chapter Map

| Service | Primary Chapters |
|---|---|
| eureka-server | 7 |
| api-gateway | 9, 10, 16 |
| user-service | 3, 4, 5, 11, 16 |
| product-service | 3, 4, 5, 11 |
| order-service | 3–6, 8, 11, 12, 20 |
| payment-service | 3–6, 12, 20 |
| notification-service | 17, 20 |

---

## Recommended Build Order

Work in this sequence so each service is testable before it becomes a dependency.

1. **eureka-server** — start here; everything else needs it
2. **product-service** — no external dependencies; good first CRUD service
3. **user-service** — introduces Spring Security and JWT
4. **payment-service** — introduces Kafka consumer/producer
5. **order-service** — ties Feign, Circuit Breaker, and Kafka together
6. **notification-service** — pure Kafka consumer; focus on observability
7. **api-gateway** — add last; requires all upstream services working
8. **Docker** — containerise each service with a Dockerfile, wire up docker-compose.yml
9. **Kubernetes** — fill in the k8s/ manifests (probes, resources, secrets)
10. **CI/CD** — wire up .github/workflows/ci-cd.yml

---

## Infrastructure

| Tool | Port | Purpose |
|---|---|---|
| Eureka | 8761 | Service registry |
| Zipkin | 9411 | Distributed tracing (Chapter 17) |
| Prometheus | 9090 | Metrics scraping (Chapter 17) |
| Grafana | 3000 | Metrics dashboards (Chapter 17) |
| Kafka | 9092 | Async messaging (Chapters 12, 20) |

Start infrastructure:
```bash
docker-compose up -d zookeeper kafka zipkin prometheus grafana
```

---

## End-to-End Request Flow

Understanding this flow is the goal of the whole project:

```
POST /api/v1/orders  (via api-gateway:8080)
  │
  ▼ JWT validated by gateway
  ▼ Routed to order-service:8083
  │
  ├─ Feign → product-service: validate product exists
  │    └─ Circuit breaker + retry wraps this call
  │
  ├─ Feign → payment-service: process payment
  │    └─ Circuit breaker + fallback wraps this call
  │
  ├─ Save order to orderdb
  │
  └─ Publish OrderPlacedEvent → Kafka topic: order-placed
                                        │
                                        ├─ payment-service consumes → processes → publishes PaymentProcessedEvent
                                        │
                                        └─ notification-service consumes → sends confirmation
```
