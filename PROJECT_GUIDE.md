# Microservices E-Commerce Platform — Complete Project Guide

A production-grade e-commerce backend built as a learning project. Seven independent Spring Boot microservices wired together with service discovery, an API gateway, asynchronous messaging, distributed tracing, circuit breakers, and a full CI/CD pipeline.

---

## Table of Contents

1. [Technology Stack](#1-technology-stack)
2. [Architecture Overview](#2-architecture-overview)
3. [Port Reference](#3-port-reference)
4. [Step-by-Step Startup Guide](#4-step-by-step-startup-guide)
5. [End-to-End Request Flow](#5-end-to-end-request-flow)
6. [Security Architecture](#6-security-architecture)
7. [Module Deep-Dives](#7-module-deep-dives)
   - [eureka-server](#71-eureka-server)
   - [api-gateway](#72-api-gateway)
   - [user-service](#73-user-service)
   - [product-service](#74-product-service)
   - [order-service](#75-order-service)
   - [payment-service](#76-payment-service)
   - [notification-service](#77-notification-service)
8. [Kafka Event Bus](#8-kafka-event-bus)
9. [Resilience Patterns](#9-resilience-patterns)
10. [Observability](#10-observability)
11. [CI/CD Pipeline](#11-cicd-pipeline)
12. [Kubernetes Deployment](#12-kubernetes-deployment)
13. [Quick Reference: Key Files](#13-quick-reference-key-files)

---

## 1. Technology Stack

| Concern | Technology | Version |
|---|---|---|
| Language | Java | 17 |
| Framework | Spring Boot | 3.2.0 |
| Cloud | Spring Cloud | 2023.0.0 |
| Service Discovery | Netflix Eureka (Spring Cloud) | 4.1.0 |
| API Gateway | Spring Cloud Gateway (WebFlux/reactive) | 4.1.0 |
| Authentication | RSA-signed JWT via Spring OAuth2 Resource Server + Nimbus JOSE | — |
| Messaging | Apache Kafka (Confluent) | 7.5.0 |
| Service-to-Service | Spring Cloud OpenFeign | 4.1.0 |
| Resilience | Resilience4j (circuit breaker + retry + bulkhead) | — |
| Distributed Tracing | Micrometer Tracing + Brave + Zipkin Reporter | — |
| Metrics | Micrometer Prometheus Registry | — |
| Metrics Dashboard | Grafana | — |
| Persistence | Spring Data JPA + H2 (in-memory, all services) | — |
| Cache | Redis 7 (product-service) | — |
| Rate Limiting | Spring Cloud Gateway RequestRateLimiter + Redis | — |
| API Documentation | Springdoc OpenAPI / Swagger UI | 2.3.0 |
| Build Tool | Maven (each service is an independent module) | 3.8+ |
| Containerization | Docker (multi-stage builds) | — |
| Orchestration | Kubernetes | — |
| CI/CD | GitHub Actions | — |
| Structured Logging | logstash-logback-encoder (all 7 services) | 7.4 |

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                          External Client                             │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ HTTPS
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     API Gateway  :8080                               │
│  • Spring Cloud Gateway (WebFlux/reactive)                          │
│  • JWT validation via OAuth2 Resource Server                        │
│  • JWKS fetched from user-service /.well-known/jwks.json            │
│  • Routes by path prefix using lb:// (Eureka load-balanced URIs)   │
│  • Global CORS configuration                                        │
│  • Rate limiting on /api/v1/auth/** (10 req/s per IP via Redis)    │
│  • /api/v1/auth/**  →  permitted (no auth), rate limited           │
│  • Everything else  →  requires valid JWT                           │
└──┬──────────┬─────────────┬──────────────┬───────────────────────────┘
   │          │             │              │
   ▼          ▼             ▼              ▼
user-      product-      order-        payment-
service    service       service       service
:8081      :8082         :8083         :8084
           (JWT val.)    (JWT val.)    (JWT val.)
           (Redis cache)

                       order-service
                       ┌─────────────────────────────────────────┐
                       │  OpenFeign ──────────► product-service  │
                       │  OpenFeign ──────────► payment-service  │
                       │   (+ @CircuitBreaker + @Retry           │
                       │      + @Bulkhead on payment call)       │
                       │  Outbox table ───────► Kafka scheduler  │
                       │    scheduler ────────► order-placed     │
                       └─────────────────────────────────────────┘

                       payment-service
                       ┌─────────────────────────────────────────┐
                       │  Kafka consumer ◄──── order-placed      │
                       │  @CircuitBreaker ────► bank gateway sim  │
                       │  Kafka producer ─────► payment-processed│
                       │  DLT: order-placed.DLT (3 retries)     │
                       └─────────────────────────────────────────┘

                       notification-service
                       ┌─────────────────────────────────────────┐
                       │  Kafka consumer ◄──── order-placed      │
                       │  Kafka consumer ◄──── payment-processed │
                       │  Manual ack + DLT (3 retries)           │
                       │  MDC correlation + structured JSON logs  │
                       └─────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                    Eureka Server  :8761                              │
│  All 7 services register on startup. Itself does not register.      │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                    Infrastructure                                    │
│  Redis :6379    — rate limiter (gateway) + product cache           │
│  Kafka :9092    — async event bus                                   │
│  Zipkin :9411   — distributed tracing                              │
│  Prometheus :9090 + Grafana :3000  — metrics                       │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. Port Reference

| Service | Port | UI / Endpoint |
|---|---|---|
| Eureka Server | 8761 | http://localhost:8761 (dashboard) |
| API Gateway | 8080 | All client requests go here |
| user-service | 8081 | http://localhost:8081/swagger-ui.html |
| product-service | 8082 | http://localhost:8082/swagger-ui.html |
| order-service | 8083 | http://localhost:8083/swagger-ui.html |
| payment-service | 8084 | http://localhost:8084/swagger-ui.html |
| notification-service | 8085 | (no REST API — Kafka consumer only) |
| Apache Kafka (host) | 9092 | — |
| Apache Kafka (internal Docker) | 29092 | — |
| Redis | 6379 | — |
| Zipkin | 9411 | http://localhost:9411 |
| Prometheus | 9090 | http://localhost:9090 |
| Grafana | 3000 | http://localhost:3000 (admin / admin) |

---

## 4. Step-by-Step Startup Guide

### Prerequisites

| Tool | Version |
|---|---|
| Java JDK | 17+ |
| Maven | 3.8+ |
| Docker Desktop | Latest |

Verify before starting:
```bash
java -version      # should say 17 or higher
mvn -version       # should say 3.8 or higher
docker info        # Docker daemon must be running
```

---

### Option A — Local Development (Services via Maven, Infrastructure via Docker)

This is the recommended approach for development. Infrastructure (Kafka, Zipkin, etc.) runs in Docker; each Spring Boot service runs directly on your machine via Maven.

**Step 1 — Clone the repository**
```bash
git clone <repo-url>
cd microservices-ecommerce
```

**Step 2 — Start infrastructure containers**
```bash
docker-compose up -d zookeeper kafka zipkin redis prometheus grafana
```
Wait ~20 seconds for Kafka to fully initialize before starting any service.

**Step 3 — Start Eureka Server (service registry — start this first)**
```bash
mvn -f eureka-server/pom.xml spring-boot:run
```
Wait until you see `Started EurekaServerApplication` in the logs, then open http://localhost:8761 to confirm it is up.

**Step 4 — Start API Gateway**
```bash
mvn -f api-gateway/pom.xml spring-boot:run
```

**Step 5 — Start user-service**
```bash
mvn -f user-service/pom.xml spring-boot:run
```
This service generates the RSA key pair used by the whole system for JWT. It must be running before the gateway or any service can validate tokens.

**Step 6 — Start product-service**
```bash
mvn -f product-service/pom.xml spring-boot:run
```

**Step 7 — Start payment-service**
```bash
mvn -f payment-service/pom.xml spring-boot:run
```
payment-service must be up before order-service because order-service calls it via OpenFeign.

**Step 8 — Start order-service**
```bash
mvn -f order-service/pom.xml spring-boot:run
```

**Step 9 — Start notification-service**
```bash
mvn -f notification-service/pom.xml spring-boot:run
```

**Step 10 — Verify everything is registered**

Open http://localhost:8761 — you should see all 7 services listed as `UP` in the Eureka dashboard.

---

### Option B — Full Docker Stack (Everything in Containers)

Use this when you want to simulate a production environment locally.

**Step 1 — Build all service JARs**
```bash
mvn -f eureka-server/pom.xml clean package -DskipTests
mvn -f api-gateway/pom.xml clean package -DskipTests
mvn -f user-service/pom.xml clean package -DskipTests
mvn -f product-service/pom.xml clean package -DskipTests
mvn -f order-service/pom.xml clean package -DskipTests
mvn -f payment-service/pom.xml clean package -DskipTests
mvn -f notification-service/pom.xml clean package -DskipTests
```

**Step 2 — Uncomment microservice blocks in docker-compose.yml**

The service blocks in `docker-compose.yml` are commented out. Uncomment them.

**Step 3 — Start everything**
```bash
docker-compose up -d
```

**Step 4 — Check logs for a specific service**
```bash
docker-compose logs -f order-service
```

---

### Verification Checklist

| Check | URL | Expected |
|---|---|---|
| Eureka dashboard | http://localhost:8761 | All services listed as UP |
| API Gateway health | http://localhost:8080/actuator/health | `{"status":"UP"}` |
| user-service Swagger | http://localhost:8081/swagger-ui.html | Swagger UI loads |
| product-service Swagger | http://localhost:8082/swagger-ui.html | Swagger UI loads |
| order-service Swagger | http://localhost:8083/swagger-ui.html | Swagger UI loads |
| Zipkin | http://localhost:9411 | Zipkin UI loads |
| Prometheus | http://localhost:9090 | Prometheus UI loads |
| Grafana | http://localhost:3000 | Grafana login (admin/admin) |
| Redis | `redis-cli -p 6379 ping` → `PONG` | Redis running |
| H2 Console (user-service) | http://localhost:8081/h2-console | Login with `sa`, empty password |

---

### Running Tests

```bash
# Run tests for a single service
mvn -f order-service/pom.xml test

# Run a specific test class
mvn -f user-service/pom.xml test -Dtest=AuthControllerTest

# Build + test all services (run from repo root sequentially)
for svc in eureka-server api-gateway user-service product-service order-service payment-service notification-service; do
  mvn -f $svc/pom.xml clean package
done
```

---

## 5. End-to-End Request Flow

This section walks through what happens when a user places an order — tracing the request through every service.

### Step 1 — Register a User

```
POST http://localhost:8080/api/v1/auth/register
Body: { "username": "john", "email": "john@example.com", "password": "secret123" }
```

- API Gateway passes this through without JWT check (`/api/v1/auth/**` is a public route).
- `user-service` receives the request, validates input, BCrypt-hashes the password, saves the `User` entity to H2.
- Returns `201 Created`.

### Step 2 — Login and Get a JWT Token

```
POST http://localhost:8080/api/v1/auth/login
Body: { "username": "john", "password": "secret123" }
```

- `user-service` authenticates the credentials, then uses `JwtUtil` to sign a JWT with the RSA-2048 private key.
- JWT claims include: `sub=john`, `role=ROLE_USER`, `iss=user-service`, expiry = 24 hours.
- Response: `{ "token": "<jwt>", "username": "john", "role": "ROLE_USER" }`.
- **You must include this token as `Authorization: Bearer <token>` on all subsequent requests.**

### Step 3 — Browse Products

```
GET http://localhost:8080/api/v1/products
Authorization: Bearer <token>
```

- API Gateway intercepts the request, fetches the JWKS from `http://localhost:8081/.well-known/jwks.json`, and validates the JWT signature against the RSA public key.
- If valid, the request is forwarded to `product-service`.
- `product-service` also validates the JWT independently (defense in depth).
- Returns a paginated list of products.

### Step 3b — (Every 15 min) Refresh the Access Token

```
POST http://localhost:8080/api/v1/auth/refresh
Body: { "refreshToken": "<refresh-token-from-login>" }
```

- Returns a new access token (15 min) and rotates the refresh token.
- The old refresh token is revoked.
- To log out: `POST /api/v1/auth/logout` with the refresh token body.

### Step 4 — Place an Order

```
POST http://localhost:8080/api/v1/orders
Authorization: Bearer <token>
Body: {
  "customerId": 1,
  "items": [{ "productId": 1, "quantity": 2 }]
}
```

Inside `order-service.OrderService.placeOrder()`:

1. **Validate products** — for each order item, calls `ProductClient` (OpenFeign → `GET /api/v1/products/{id}` on product-service). The Bearer token is relayed via `FeignConfig.RequestInterceptor`. If a product is not found, an exception is thrown.
2. **Calculate total** — uses actual price from product-service response (not from the client request).
3. **Save order as PENDING** — `Order` entity with status `PENDING` is saved to H2.
4. **Initiate payment** — delegates to `PaymentProcessor.processPayment()` which is annotated with `@CircuitBreaker + @Retry + @Bulkhead`. Calls `PaymentClient` (OpenFeign → `POST /api/v1/payments`).
5. **If bulkhead full** — rejects immediately; fallback sets order to `CANCELLED`.
6. **If transient failure** — retry up to 3 times with exponential backoff (500ms, 1s, 2s).
7. **If circuit is OPEN** — `paymentFallback()` runs: sets order status to `CANCELLED`.
8. **If payment succeeds** — order status is set to `CONFIRMED`.
9. **Save to outbox** — an `OutboxEvent` is saved to the `outbox_events` table **in the same DB transaction** as the order. This is the outbox pattern — no dual-write risk.
10. **Async Kafka publish** — `OutboxEventPublisher` (@Scheduled, every 5s) picks up unpublished outbox events and sends them to Kafka topic `order-placed`, then marks them published.

### Step 5 — Payment Processing (Async via Kafka)

`payment-service.OrderPlacedConsumer` consumes from topic `order-placed`:

1. **Idempotency check** — checks if a `Payment` with this `orderId` already exists. If yes, acknowledges and skips (prevents duplicate processing on retry).
2. **Create Payment (PENDING)** — saves a `Payment` entity.
3. **Call bank gateway** — `PaymentService.callBankGateway()` is wrapped with `@CircuitBreaker(name="bank-gateway")`. This simulates a real bank API call — currently always returns `SUCCESS`.
4. **Update payment status to SUCCESS** — saves the updated entity.
5. **Publish Kafka event** — `PaymentEventPublisher` sends a `PaymentProcessedEvent` to topic `payment-processed`.
6. **Acknowledge the Kafka message** — manual ack (`MANUAL_IMMEDIATE`). On any non-retryable exception, the message is sent to `order-placed.DLT`.

### Step 6 — Notifications (Async via Kafka)

`notification-service.NotificationConsumer` has two Kafka listeners:

- `handleOrderPlaced` (topic: `order-placed`) — logs: "Order Confirmation: Order #X placed by customer Y for amount Z"
- `handlePaymentProcessed` (topic: `payment-processed`) — logs: "Payment Receipt: Payment for order #X processed, transaction ID: Y, status: Z"

Both listeners use `MDC.put("correlationId", "order-"+orderId)` so every log line for the same order carries the same correlation ID in structured JSON logs.

### Step 7 — Observability

At each step above:
- Micrometer + Brave automatically propagates trace context (TraceId, SpanId) across service boundaries via HTTP headers and Kafka message headers.
- All spans are exported to **Zipkin** (100% sampling). You can visualize the full distributed trace at http://localhost:9411.
- All services expose `/actuator/prometheus` — Prometheus scrapes these every 15 seconds. Dashboards are in Grafana.

---

## 6. Security Architecture

This project uses **asymmetric RSA-2048 JWT** — there is no shared secret between services.

```
user-service startup
  └─► RsaKeyConfig.java generates RSA-2048 key pair (in memory)
      ├─ Private key → used by JwtUtil to SIGN tokens
      └─ Public key → exposed at GET /.well-known/jwks.json (JWKS format)

api-gateway startup
  └─► Reads spring.security.oauth2.resourceserver.jwt.jwk-set-uri
      = http://localhost:8081/.well-known/jwks.json
      └─ Fetches public key, caches JWKS, validates ALL incoming JWTs automatically

product-service, order-service, payment-service
  └─► Each also reads jwk-set-uri = http://localhost:8081/.well-known/jwks.json
      └─ Validates JWT independently (defense in depth — even bypassing the gateway
         requires a valid token)

Token claims: sub=username, role, iss="user-service", exp=now+24h
```

**Important**: The RSA key pair is generated fresh on every `user-service` restart. All existing tokens become invalid after a restart.

### Refresh Token Flow

```
POST /api/v1/auth/login
  → returns: { token (15 min), refreshToken (7 days), username, role }

POST /api/v1/auth/refresh  { refreshToken: "..." }
  → validates refresh token in DB (not revoked, not expired)
  → issues new access token + rotates refresh token (old one revoked)
  → returns: { token (15 min), refreshToken (7 days), username, role }

POST /api/v1/auth/logout   { refreshToken: "..." }
  → marks refresh token as revoked in DB
  → access token expires naturally (max 15 min)
  → returns: 204 No Content
```

Refresh tokens are stored in the `refresh_tokens` DB table. Each login revokes all previous refresh tokens for that user (single-session semantics).

### Token Relay (Service-to-Service)

When `order-service` calls `product-service` or `payment-service` via OpenFeign, it must pass the original user's JWT downstream. This is done by `FeignConfig.java`:

```java
// order-service/config/FeignConfig.java
@Bean
public RequestInterceptor requestInterceptor() {
    return requestTemplate -> {
        String authHeader = ((ServletRequestAttributes) RequestContextHolder
            .getRequestAttributes()).getRequest().getHeader("Authorization");
        if (authHeader != null) {
            requestTemplate.header("Authorization", authHeader);
        }
    };
}
```

### Route Security Matrix

| Route Pattern | JWT Required? | Rate Limited? | Service |
|---|---|---|---|
| `POST /api/v1/auth/register` | No | Yes (10/s per IP) | user-service |
| `POST /api/v1/auth/login` | No | Yes (10/s per IP) | user-service |
| `POST /api/v1/auth/refresh` | No | Yes (10/s per IP) | user-service |
| `POST /api/v1/auth/logout` | No | Yes (10/s per IP) | user-service |
| `GET /.well-known/jwks.json` | No | No | user-service |
| `/actuator/**` | No | No | all services |
| `/h2-console/**` | No | No | all services (local dev) |
| `/swagger-ui/**`, `/api-docs/**` | No | No | all services |
| `/api/v1/users/**` | Yes | No | user-service |
| `/api/v1/products/**` | Yes | No | product-service (gateway + service-level) |
| `/api/v1/orders/**` | Yes | No | order-service (gateway + service-level) |
| `/api/v1/payments/**` | Yes | No | payment-service (gateway + service-level) |

Security is enforced at **both the gateway and individual service level** (defense in depth). Each of `product-service`, `order-service`, and `payment-service` validates the JWT independently using the same JWKS URI. Token relay in `FeignConfig` ensures the user's Bearer token is forwarded on all Feign calls between services.

---

## 7. Module Deep-Dives

### 7.1 eureka-server

**Purpose**: Central service registry. All other services register their host/port here and discover each other by logical name instead of hard-coded URLs.

**Key annotations**: `@EnableEurekaServer`

**Configuration highlights** (`application.yml`):
```yaml
eureka:
  client:
    register-with-eureka: false   # Does not register itself
    fetch-registry: false
  server:
    wait-time-in-ms-when-sync-empty: 0  # Fast startup in dev
```

**Dependencies**: `spring-cloud-starter-netflix-eureka-server`, `spring-boot-starter-actuator`

**Design note**: Eureka acts as a phone book. When `order-service` calls `lb://product-service`, Spring Cloud resolves that to a real host:port by querying Eureka. This allows services to scale horizontally without configuration changes.

---

### 7.2 api-gateway

**Purpose**: Single entry point for all external traffic. Handles JWT validation, routing, and CORS.

**Key annotations**: `@EnableDiscoveryClient`

**Routing** (defined in `application.yml`):
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/v1/auth/**,/api/v1/users/**
        - id: product-service
          uri: lb://product-service
          predicates:
            - Path=/api/v1/products/**
        # ... etc.
```

**Security** (`SecurityConfig.java`): Uses `@EnableWebFluxSecurity` (reactive, not servlet). The `spring-security-oauth2-resource-server` library automatically:
1. Fetches the JWKS from `http://localhost:8081/.well-known/jwks.json`
2. Caches the public key
3. Validates every incoming JWT's signature, expiry, and issuer
4. Rejects requests with missing or invalid tokens with 401n

**Dependencies**: `spring-cloud-starter-gateway`, `spring-cloud-starter-netflix-eureka-client`, `spring-boot-starter-oauth2-resource-server`

---

### 7.3 user-service

**Purpose**: User registration, authentication, and JWT issuance. The sole authority for identity in this system.

**REST Endpoints**:

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | None | Register new user → 201 Created |
| POST | `/api/v1/auth/login` | None | Login → JWT token |
| GET | `/.well-known/jwks.json` | None | Public JWKS for gateway and services |
| GET | `/api/v1/users/{id}` | JWT | Get user by ID |
| GET | `/api/v1/users` | JWT | List users (paginated) |
| GET | `/h2-console` | None | In-memory DB browser |
| GET | `/swagger-ui.html` | None | API documentation |

**Entity: `User`**

| Field | Type | Notes |
|---|---|---|
| id | Long | Auto-generated PK |
| username | String | Unique |
| email | String | Unique |
| password | String | BCrypt hashed |
| role | String | Default: `ROLE_USER` |
| createdAt | LocalDateTime | Auto-set |

**Key Classes**:
- `RsaKeyConfig.java` — generates RSA-2048 key pair on startup, exposes as Spring beans
- `JwtUtil.java` — signs tokens with `NimbusJwtEncoder` using the RSA private key
- `JwksController.java` — serves the public key at `/.well-known/jwks.json`
- `UserDetailsServiceImpl.java` — implements `UserDetailsService`, loads users from DB (separate class to avoid Spring circular dependency)

---

### 7.4 product-service

**Purpose**: Product catalog management — create, read, update, delete products with category filtering and optimistic locking.

**REST Endpoints**:

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/products` | JWT | Create product → 201 + Location header |
| GET | `/api/v1/products/{id}` | JWT | Get product by ID |
| GET | `/api/v1/products` | JWT | List products (paginated, optional `?category=`) |
| PUT | `/api/v1/products/{id}` | JWT | Update product |
| DELETE | `/api/v1/products/{id}` | JWT | Delete product → 204 No Content |

**Entity: `Product`**

| Field | Type | Notes |
|---|---|---|
| id | Long | Auto-generated PK |
| name | String | — |
| description | String | — |
| price | BigDecimal | Precision 10, scale 2 |
| stock | Integer | — |
| category | String | — |
| version | Long | `@Version` — optimistic locking |

**Security**: `SecurityConfig` validates JWT via OAuth2 Resource Server (JWKS from user-service). Public paths: `/h2-console/**`, `/actuator/**`, `/swagger-ui/**`, `/api-docs/**`.

**Notable patterns**:
- `@Version` on `version` field — prevents lost update anomalies when multiple threads/instances update the same product concurrently (JPA optimistic locking)
- `ProductRepository.findByCategory(String, Pageable)` — Spring Data derived query for category filtering
- `GlobalExceptionHandler` — maps `ProductNotFoundException` → 404, validation errors → 400

---

### 7.5 order-service

**Purpose**: Core business orchestration. Places orders by validating products, initiating payment, and publishing events. The most complex service in the system.

**REST Endpoints**:

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/orders` | JWT | Place order → 201 + Location header |
| GET | `/api/v1/orders/{id}` | JWT | Get order by ID |
| GET | `/api/v1/orders/customer/{customerId}` | JWT | List orders for customer (paginated) |

**Entities**:

`Order`:

| Field | Type | Notes |
|---|---|---|
| id | Long | Auto-generated PK |
| customerId | Long | — |
| status | OrderStatus | `PENDING`, `CONFIRMED`, `CANCELLED` |
| totalAmount | BigDecimal | Calculated from product prices |
| createdAt | LocalDateTime | — |
| items | List\<OrderItem\> | OneToMany, cascade ALL |

`OrderItem`:

| Field | Type | Notes |
|---|---|---|
| id | Long | — |
| order | Order | ManyToOne |
| productId | Long | — |
| productName | String | Copied from product at order time |
| quantity | Integer | — |
| price | BigDecimal | Copied from product at order time |

**Key Classes**:

- `ProductClient.java` — `@FeignClient(name="product-service")`, calls `GET /api/v1/products/{id}`
- `PaymentClient.java` — `@FeignClient(name="payment-service")`, calls `POST /api/v1/payments`
- `FeignConfig.java` — custom `ErrorDecoder` (maps HTTP 404 → `ProductNotFoundException`) + `RequestInterceptor` for token relay
- `PaymentProcessor.java` — separate `@Service` bean wrapping the payment Feign call with `@CircuitBreaker`. Must be a separate bean (not inner method) for Spring AOP to intercept the annotation
- `OrderEventPublisher.java` — `KafkaTemplate<String, OrderPlacedEvent>`, sends to topic `order-placed`

**Key annotations**: `@EnableDiscoveryClient`, `@EnableFeignClients`

**Security**: `SecurityConfig` validates JWT via OAuth2 Resource Server. Public paths: `/h2-console/**`, `/actuator/**`, `/swagger-ui/**`, `/api-docs/**`.

**Notable patterns**:
- Circuit breaker is on `PaymentProcessor` (not `OrderService`) — Spring AOP proxies only work across bean boundaries
- `PaymentProcessor` stacks `@CircuitBreaker + @Retry + @Bulkhead` — bulkhead limits concurrency, retry handles transient failures, circuit breaker prevents cascading failures
- **Outbox pattern**: `OrderService.placeOrder()` saves an `OutboxEvent` to the DB in the same transaction as the Order. `OutboxEventPublisher` (@Scheduled every 5s) reads unpublished events and sends to Kafka — eliminates the dual-write race condition
- `orderId` as Kafka message key — guarantees all events for the same order land on the same partition (ordering guarantee)
- Token relay via `FeignConfig.RequestInterceptor` — forwards user's Bearer token to downstream services

---

### 7.6 payment-service

**Purpose**: Processes payments triggered by order events. Demonstrates idempotent Kafka consumers, Dead Letter Topics, and nested circuit breakers.

**REST Endpoints**:

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/payments` | JWT | Initiate payment (called by order-service Feign) |
| GET | `/api/v1/payments/{id}` | JWT | Get payment by ID |
| GET | `/api/v1/payments/order/{orderId}` | JWT | Get payment for an order |

**Entity: `Payment`**

| Field | Type | Notes |
|---|---|---|
| id | Long | — |
| orderId | Long | Unique (idempotency key) |
| customerId | Long | — |
| amount | BigDecimal | — |
| status | PaymentStatus | `PENDING`, `SUCCESS`, `FAILED` |
| transactionId | UUID | Generated on success |
| createdAt | LocalDateTime | — |

**Key Classes**:

- `KafkaConfig.java` — configures `ConcurrentKafkaListenerContainerFactory` with:
  - `MANUAL_IMMEDIATE` ack mode
  - `DefaultErrorHandler` with `DeadLetterPublishingRecoverer` (failed messages → `order-placed.DLT`)
  - 3 retries with 1-second fixed backoff
  - `PaymentAlreadyExistsException` is non-retryable
- `OrderPlacedConsumer.java` — Kafka listener, checks idempotency first, then calls `PaymentService`
- `PaymentEventPublisher.java` — publishes `PaymentProcessedEvent` to `payment-processed`

**Security**: `SecurityConfig` validates JWT via OAuth2 Resource Server. Public paths: `/h2-console/**`, `/actuator/**`, `/swagger-ui/**`, `/api-docs/**`.

**Idempotency flow**:
```
Receive order-placed message
  → Check: does Payment with orderId already exist?
    YES → log "already processed", ack, return (do nothing)
    NO  → proceed with payment, save, publish, ack
```

---

### 7.7 notification-service

**Purpose**: Consumes order and payment events and sends notifications (currently: structured log output). No REST API.

**Kafka Listeners** (`NotificationConsumer.java`):

| Listener | Topic | Group ID | Action |
|---|---|---|---|
| `handleOrderPlaced` | `order-placed` | `notification-service-group` | Logs order confirmation |
| `handlePaymentProcessed` | `payment-processed` | `notification-service-group` | Logs payment receipt |

**Structured logging with MDC**:
```java
MDC.put("correlationId", "order-" + orderId);
try {
    // process
} finally {
    MDC.clear();  // always clear — thread pool safety
}
```

`logback-spring.xml` uses `LogstashEncoder` — every log line is JSON with `correlationId` as a top-level field, consumable directly by ELK/Loki without additional parsing.

**Local event records**: `OrderPlacedEvent` and `PaymentProcessedEvent` are defined locally. Kafka type mapping in `application.yml` maps the producer class names to these local classes.

---

## 8. Kafka Event Bus

### Topics

| Topic | Producer | Consumer(s) | Notes |
|---|---|---|---|
| `order-placed` | order-service | payment-service, notification-service | Key = orderId |
| `payment-processed` | payment-service | notification-service | Key = orderId |
| `order-placed.DLT` | payment-service (error handler) | Manual recovery | Dead Letter Topic |

### Event Payloads

**`OrderPlacedEvent`** (produced by order-service):
```
orderId, customerId, totalAmount, items[{productId, productName, quantity, price}]
```

**`PaymentProcessedEvent`** (produced by payment-service):
```
orderId, customerId, amount, status, transactionId
```

### Retry and DLT Configuration

```
payment-service KafkaConfig:
  ack-mode: MANUAL_IMMEDIATE
  retries: 3
  backoff: 1 second fixed
  on failure after 3 retries → send to order-placed.DLT

Non-retryable exceptions (go directly to DLT, no retries):
  - PaymentAlreadyExistsException

notification-service KafkaConfig:
  ack-mode: MANUAL_IMMEDIATE
  retries: 3
  backoff: 1 second fixed
  on failure after 3 retries → send to {topic}.DLT
  (order-placed.DLT, payment-processed.DLT)
```

**Kafka producer hardening** (order-service and payment-service):
- `acks: all` — wait for full replication before confirming
- `enable.idempotence: true` — exactly-once at producer level (no duplicate messages on retry)
- `retries: 3` — automatic producer retry on transient failures

### Consumer Groups

- `payment-service` and `notification-service` each have their own consumer group IDs, so both receive every `order-placed` message independently.
- Kafka guarantees at-least-once delivery; idempotency in `payment-service` handles duplicates.

---

## 9. Resilience Patterns

### Circuit Breaker + Retry + Bulkhead: order-service → payment-service

Location: `order-service/service/PaymentProcessor.java`

All three decorators stack on `processPayment()`. Execution order (outermost → innermost): CircuitBreaker → Retry → Bulkhead → actual Feign call.

```yaml
resilience4j:
  circuitbreaker:
    instances:
      payment-service:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        failure-rate-threshold: 50
        minimum-number-of-calls: 5
        wait-duration-in-open-state: 10s
  retry:
    instances:
      payment-service:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        ignore-exceptions:
          - io.github.resilience4j.bulkhead.BulkheadFullException
  bulkhead:
    instances:
      payment-service:
        max-concurrent-calls: 10
        max-wait-duration: 100ms
```

**Fallback behavior**: Any exception (circuit open, retry exhausted, bulkhead full) triggers `paymentFallback()` — sets order status to `CANCELLED`.

### Circuit Breaker: payment-service → Bank Gateway

Location: `payment-service/service/PaymentService.java`

```yaml
resilience4j:
  circuitbreaker:
    instances:
      bank-gateway:
        sliding-window-size: 5
        failure-rate-threshold: 60     # Open circuit if ≥60% fail
        minimum-number-of-calls: 3
        wait-duration-in-open-state: 15s
```

### Outbox Pattern: order-service → Kafka

The outbox pattern solves the dual-write problem between saving the order and publishing the Kafka event.

**Problem**: If `orderRepository.save()` succeeds but `kafkaTemplate.send()` fails (e.g., Kafka is briefly down), the order exists but no event is ever published — payment and notification never happen.

**Solution**:
```
placeOrder() @Transactional:
  orderRepository.save(order)         ← DB write #1
  outboxEventRepository.save(event)   ← DB write #2 — SAME transaction

OutboxEventPublisher @Scheduled(5s):
  SELECT * FROM outbox_events WHERE published = false
  kafkaTemplate.send(...).get(5s)     ← Kafka write (retried on next poll if failed)
  event.published = true              ← marked only after Kafka confirms
```

Both DB writes are atomic. If the service crashes between saving the order and the scheduler running, the scheduler picks up the unpublished event on next poll.

### Feign Circuit Breaker

```yaml
feign:
  circuitbreaker:
    enabled: true
```

This enables Resilience4j circuit breakers on all Feign clients automatically (in addition to the explicit `@CircuitBreaker` on `PaymentProcessor`).

---

## 10. Observability

### Distributed Tracing — Zipkin

Every service includes `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`. Micrometer automatically:
- Creates a trace (TraceId) for each incoming request
- Creates child spans for each Feign call and Kafka producer/consumer
- Propagates context via HTTP headers (`b3`) and Kafka message headers
- Exports all spans to Zipkin at `http://localhost:9411` (100% sampling in dev)

View full distributed traces at: **http://localhost:9411**

All services are configured with:
```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% sampling (reduce in production)
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

### Metrics — Prometheus + Grafana

Every service includes `micrometer-registry-prometheus`. Prometheus scrapes `/actuator/prometheus` every 15 seconds (configured in `config/prometheus.yml`).

Automatically collected metrics include:
- HTTP request counts, error rates, latency (histograms)
- JVM memory, GC, threads
- Kafka consumer lag
- Resilience4j circuit breaker state, call counts

View metrics at: **http://localhost:9090** (Prometheus) and **http://localhost:3000** (Grafana, admin/admin)

### Health Endpoints

Every service exposes:
```
GET /actuator/health          → liveness + readiness combined
GET /actuator/health/liveness → just liveness (used by Kubernetes)
GET /actuator/health/readiness → just readiness (used by Kubernetes)
GET /actuator/prometheus      → Prometheus metrics scrape endpoint
GET /actuator/info            → build info
```

---

## 11. Caching

### Product Cache (Redis, product-service)

Individual product lookups (`GET /api/v1/products/{id}`) are cached in Redis for 5 minutes.

```java
@Cacheable(value = "products", key = "#id")
public ProductResponse findById(Long id) { ... }

@CacheEvict(value = "products", key = "#id")
public ProductResponse update(Long id, ...) { ... }

@CacheEvict(value = "products", key = "#id")
public void delete(Long id) { ... }
```

`create()` uses `allEntries = true` eviction since a new product doesn't have a known ID pre-save.

Cache is configured in `CacheConfig.java` with a 5-minute TTL using `GenericJackson2JsonRedisSerializer` (handles Java records).

### Rate Limiting (Redis, api-gateway)

Auth endpoints are rate-limited at 10 requests/second per IP (burst of 20) using Spring Cloud Gateway's `RequestRateLimiter` filter backed by Redis.

```yaml
filters:
  - name: RequestRateLimiter
    args:
      redis-rate-limiter.replenishRate: 10
      redis-rate-limiter.burstCapacity: 20
      key-resolver: "#{@remoteAddrKeyResolver}"
```

A 429 Too Many Requests response is returned when the limit is exceeded.

---

## 12. CI/CD Pipeline

File: `.github/workflows/ci-cd.yml`

Triggered on: push to `main`/`develop`, pull request to `main`

### Job 1: build

Runs a matrix of all 7 services in parallel (`fail-fast: false` — one failure does not cancel others):

```
for each service:
  mvn clean package -DskipTests   → compile and package
  mvn test                         → run tests
```

### Job 2: docker-build

Runs after `build` job, only on push events (not PRs):

```
for each service:
  docker buildx build
  docker push  :<commit-SHA>   (immutable, traceable)
  docker push  :latest         (convenience tag)
```

Uses Docker Buildx with GitHub Actions layer caching for faster builds.

Required GitHub secrets:
- `DOCKER_USERNAME`
- `DOCKER_PASSWORD`

### Job 3: deploy

Runs after `docker-build`, only on pushes to `main`:

```
kubectl apply -f k8s/configmap.yml
kubectl apply -f k8s/          → apply all manifests
for each service:
  kubectl set image deployment/<service> <service>=<image>:<commit-SHA>
  kubectl rollout status deployment/<service> --timeout=5m
```

Required GitHub secrets:
- `KUBECONFIG` (base64-encoded kubeconfig for your cluster)

---

## 13. Kubernetes Deployment

Manifests are in `k8s/` — one file per service, plus a shared `configmap.yml`.

### Shared Configuration (`k8s/configmap.yml`)

```yaml
# ConfigMap (non-sensitive)
EUREKA_URL: http://eureka-server:8761/eureka
KAFKA_BOOTSTRAP_SERVERS: kafka:9092
ZIPKIN_ENDPOINT: http://zipkin:9411/api/v2/spans
SPRING_PROFILES_ACTIVE: prod

# Secret (sensitive — base64 encoded in real usage)
JWT_SECRET: <value>
```

### Per-Service Manifest Structure

Each service manifest contains a `Deployment` and a `Service`. Example highlights:

```yaml
spec:
  containers:
    - image: sameerks/<service>:latest
      livenessProbe:
        httpGet:
          path: /actuator/health/liveness
          port: 808X
        initialDelaySeconds: 60
      readinessProbe:
        httpGet:
          path: /actuator/health/readiness
          port: 808X
        initialDelaySeconds: 30
      resources:
        requests: { memory: "256Mi", cpu: "100m" }
        limits:   { memory: "512Mi", cpu: "500m" }
```

### Horizontal Pod Autoscaler (order-service)

```yaml
apiVersion: autoscaling/v2
spec:
  minReplicas: 1
  maxReplicas: 5
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

### Service Types

| Service | Kubernetes Type | Reason |
|---|---|---|
| api-gateway | `LoadBalancer` | External traffic entry point |
| All others | `ClusterIP` | Internal only; accessed through gateway |

---

## 14. Quick Reference: Key Files

| File | Purpose |
|---|---|
| `CLAUDE.md` | Build commands and architecture summary |
| `HINTS.md` | Implementation hints and code snippets per service |
| `docker-compose.yml` | Infrastructure + service orchestration (includes Redis) |
| `config/prometheus.yml` | Prometheus scrape config for all services |
| `.github/workflows/ci-cd.yml` | Full CI/CD pipeline |
| `k8s/configmap.yml` | Shared Kubernetes config and secrets |
| `k8s/order-service.yml` | Example HPA configuration |
| `user-service/.../RsaKeyConfig.java` | RSA key pair generation |
| `user-service/.../JwksController.java` | Public key JWKS endpoint |
| `user-service/.../RefreshTokenService.java` | Refresh token lifecycle (create, validate, revoke) |
| `user-service/.../AuthController.java` | Auth endpoints including /refresh and /logout |
| `order-service/.../OutboxEvent.java` | Outbox pattern entity |
| `order-service/.../OutboxEventPublisher.java` | Scheduled Kafka publisher from outbox table |
| `order-service/.../OrderService.java` | Saves to outbox instead of direct Kafka publish |
| `order-service/.../FeignConfig.java` | Token relay and error decoder |
| `order-service/.../PaymentProcessor.java` | CircuitBreaker + Retry + Bulkhead (must be separate bean) |
| `payment-service/.../KafkaConfig.java` | DLT and retry error handler |
| `payment-service/.../OrderPlacedConsumer.java` | Idempotent Kafka consumer |
| `product-service/.../CacheConfig.java` | Redis cache manager, 5 min TTL |
| `product-service/.../ProductService.java` | @Cacheable product lookups |
| `api-gateway/.../RateLimiterConfig.java` | IP-based KeyResolver for rate limiter |
| `notification-service/.../KafkaConfig.java` | DLT + manual ack for notification consumers |
| `notification-service/.../logback-spring.xml` | Structured JSON logging config (template for all services) |
