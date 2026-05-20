# API Gateway

The API Gateway is the **single entry point** for all client traffic. It routes requests to the correct downstream microservice, validates JWT tokens, enforces rate limiting, and handles CORS — all before a request ever reaches a business service.

- **Port:** `8080`
- **Stack:** Spring Cloud Gateway (WebFlux/reactive — not Spring MVC)

---

## What It Does

### Routing

All routes are declared in `application.yml`. Each route matches on a path predicate and forwards to a service resolved via Eureka (`lb://` prefix triggers client-side load balancing):

| Path Prefix | Forwards To | Special Filters |
|-------------|-------------|-----------------|
| `/api/v1/auth/**` | `lb://user-service` | `RequestRateLimiter` (10 req/s, burst 20) |
| `/api/v1/users/**` | `lb://user-service` | — |
| `/api/v1/products/**` | `lb://product-service` | — |
| `/api/v1/orders/**` | `lb://order-service` | — |
| `/api/v1/payments/**` | `lb://payment-service` | — |

The `lb://` URI scheme instructs Spring Cloud LoadBalancer to look up live instances from the Eureka registry and distribute traffic round-robin.

### JWT Validation

The gateway is configured as an **OAuth2 Resource Server**. It fetches the RSA public key from `user-service` at `http://localhost:8081/.well-known/jwks.json` and uses it to validate every incoming JWT signature and expiry — without calling `user-service` on each request.

Public paths bypassed by JWT validation:
- `POST /api/v1/auth/**` (login, register, refresh)
- `GET /actuator/**`

All other requests require a valid `Authorization: Bearer <token>` header.

### Rate Limiting

The `/api/v1/auth/**` route has a `RequestRateLimiter` filter backed by **Redis**:
- `replenishRate: 10` — refills 10 tokens per second
- `burstCapacity: 20` — allows short bursts up to 20 requests
- Key resolver: client IP address (`remoteAddrKeyResolver`)

Requests exceeding the limit receive `HTTP 429 Too Many Requests`.

### CORS

Global CORS policy permits all origins, methods, and headers — suitable for development. Tighten `allowedOrigins` for production.

---

## Key Classes

### `SecurityConfig.java`
Reactive (`@EnableWebFluxSecurity`) security config. Disables CSRF (stateless), defines the public path allowlist, configures the JWT resource server with the JWKS URI.

### `RateLimiterConfig.java`
Exposes a `KeyResolver` bean that resolves rate-limit keys by client IP (`exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()`).

---

## Configuration (`application.yml`)

```yaml
server:
  port: 8080

spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "*"
            allowedMethods: "*"
            allowedHeaders: "*"
      routes:
        - id: user-service-auth
          uri: lb://user-service
          predicates:
            - Path=/api/v1/auth/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
                key-resolver: "#{@remoteAddrKeyResolver}"

  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:8081/.well-known/jwks.json

  data:
    redis:
      host: localhost
      port: 6379
```

---

## Running

```bash
# Prerequisites: Eureka Server + Redis must be running
mvn -f api-gateway/pom.xml spring-boot:run
```

---

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `spring-cloud-starter-gateway` | WebFlux-based reverse proxy and routing |
| `spring-cloud-starter-netflix-eureka-client` | Service discovery for `lb://` resolution |
| `spring-boot-starter-security` + `oauth2-resource-server` | JWT validation |
| `spring-boot-starter-data-redis-reactive` | Rate limiter token bucket storage |
| `spring-boot-starter-actuator` | Health, metrics, prometheus endpoints |

---

## Role in the Platform

```
Client (browser / mobile / curl)
    │
    │  HTTP :8080
    ▼
API Gateway
    ├── JWT validation (RSA public key from user-service JWKS)
    ├── Rate limiting (Redis token bucket, per IP)
    ├── CORS headers
    │
    ├── /api/v1/auth/**      → lb://user-service   :8081
    ├── /api/v1/users/**     → lb://user-service   :8081
    ├── /api/v1/products/**  → lb://product-service :8082
    ├── /api/v1/orders/**    → lb://order-service   :8083
    └── /api/v1/payments/**  → lb://payment-service :8084
```
