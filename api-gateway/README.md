# api-gateway

**Port:** 8080

## Purpose
Single entry point for all client traffic. Routes requests to downstream services, enforces authentication, and handles cross-cutting concerns.

## What Needs to Be Here

### Routing
- Routes exist for: user-service, product-service, order-service, payment-service
- Routing uses service names (not hardcoded IPs) via the Eureka registry

### Authentication
- A filter that validates JWT tokens on every incoming request
- Unauthenticated paths: `/api/v1/auth/**`
- Requests with missing or invalid tokens are rejected with 401
- Valid token → downstream service receives the authenticated user info as a header

### Cross-Cutting
- A global filter that logs every request (method, path, response status)
- Rate limiting on order-service routes

### Registration
- This service registers itself with Eureka

---

## Configuration to Write (`application.yml`)

> **How to use this section:** Every setting you need in `src/main/resources/application.yml` is explained below in plain English. Try to write each one yourself before looking at the answer — that is how you will actually learn it.

---

### 1. Server Port

**What is it?**
The port the API Gateway opens on. All external traffic enters through this single door — no client ever talks directly to user-service, product-service, etc.

```yaml
server:
  port: ???   # hint: 8080
```

---

### 2. Application Name

**What is it?**
The name this service registers under in Eureka. Other services and tools use this name to identify it.

```yaml
spring:
  application:
    name: ???
```

---

### 3. Route Rules (the most important config here)

**What is it?**
This is where you tell the gateway "when a request comes in matching this URL pattern, forward it to this service." This is the core job of an API gateway.

Each route has:
- **id** — a name you give the route (just a label)
- **uri** — where to send the request. `lb://user-service` means "use Eureka's load balancer to find a service called `user-service`"
- **predicates** — the condition that triggers this route (a URL pattern)

You need four routes:

| Route name       | URL patterns                          | Service to forward to |
|-----------------|---------------------------------------|-----------------------|
| user-service    | `/api/v1/users/**` and `/api/v1/auth/**` | `lb://user-service`  |
| product-service | `/api/v1/products/**`                 | `lb://product-service`|
| order-service   | `/api/v1/orders/**`                   | `lb://order-service`  |
| payment-service | `/api/v1/payments/**`                 | `lb://payment-service`|

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**, /api/v1/auth/**

        - id: product-service
          uri: ???
          predicates:
            - Path: ???

        # ... add routes for order-service and payment-service
```

> **Learn:** What does `lb://` mean? How does the gateway find the real IP of `user-service` using Eureka? What is a **predicate** in Spring Cloud Gateway?

---

### 4. Register With Eureka

**What is it?**
The gateway needs to register itself with Eureka so it can look up the real addresses of downstream services. It also needs to tell Eureka where the Eureka server lives.

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

> **Learn:** What does `prefer-ip-address: true` do? When would you want the IP address instead of the hostname?

---

### 5. Expose Actuator Endpoints

**What is it?**
Same as other services, but the gateway also exposes a `gateway` endpoint that shows all active routes.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, gateway
  endpoint:
    health:
      show-details: always
```

> **Learn:** Try hitting `/actuator/gateway/routes` after the gateway starts. What do you see?

---

## Topics to Learn

- What is an **API Gateway** pattern and why is it used instead of calling each service directly?
- What is **Spring Cloud Gateway** and how is it different from older Zuul gateway?
- What does `lb://` mean in the route URI? (hint: Ribbon / Spring Cloud LoadBalancer)
- What is a **predicate** vs a **filter** in Spring Cloud Gateway?
- What is a **JWT** and how does a gateway validate one without a shared database?
- What is a **GlobalFilter** and how do you write one?
- What is **rate limiting** and how do you add it to a specific route?

---

## Answer

<details>
<summary>Click to reveal the full application.yml (try first!)</summary>

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**, /api/v1/auth/**

        - id: product-service
          uri: lb://product-service
          predicates:
            - Path=/api/v1/products/**

        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/v1/orders/**

        - id: payment-service
          uri: lb://payment-service
          predicates:
            - Path=/api/v1/payments/**

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

management:
  endpoints:
    web:
      exposure:
        include: health, info, gateway
  endpoint:
    health:
      show-details: always
```

</details>
