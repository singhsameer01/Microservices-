# order-service

**Port:** 8083

## Purpose
Orchestrates order creation. Calls product-service and payment-service synchronously, then publishes an event to Kafka asynchronously.

## What Needs to Be Here

### Data
- An `Order` entity with: id, customerId, status (PENDING / CONFIRMED / CANCELLED), totalAmount, createdAt
- An `OrderItem` entity with: id, productId, quantity, price
- `Order` has a one-to-many relationship with `OrderItem`
- Both backed by their own H2 database

### DTOs
- `OrderRequest` — input (Java 17 record): customerId + list of items
- `OrderItemDto` — (Java 17 record): productId + quantity
- `OrderResponse` — output (Java 17 record)

### API
- `POST /api/v1/orders` → places order, returns 201
- `GET /api/v1/orders/{id}` → returns order or 404
- `GET /api/v1/orders/customer/{customerId}` → returns paginated list of customer orders

### Inter-Service Calls (Synchronous)
- A Feign client for product-service: fetches product details by ID
- A Feign client for payment-service: processes payment
- 404 from product-service maps to a domain-specific exception
- Payment call is protected by a circuit breaker
- Circuit breaker has a fallback that returns a safe degraded response
- Product-service call is retried up to 3 times on transient failures

### Event Publishing (Asynchronous)
- After an order is saved, an `OrderPlacedEvent` is published to the `order-placed` Kafka topic
- `OrderPlacedEvent` contains: orderId, customerId, totalAmount, timestamp
- Event published using Outbox Pattern: event written to DB in same transaction as order, then polled and sent to Kafka

### Error Handling
- Payment circuit open → fallback response, order stays PENDING
- Product not found → 404
- Invalid input → 400

### Documentation
- All endpoints documented with OpenAPI/Swagger

### Registration
- Registers itself with Eureka

---

## Configuration to Write (`application.yml`)

> **How to use this section:** This service has the most complex configuration in the project. Take it one section at a time. Try to write each block yourself before revealing the answer at the bottom.

---

### 1. Server Port, Application Name, Database

Same pattern as user-service and product-service. Port is `8083`, database name is `orderdb`.

```yaml
server:
  port: ???

spring:
  application:
    name: ???
  datasource:
    url: ???
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
  h2:
    console:
      enabled: true
      path: /h2-console
```

---

### 2. Kafka — Producer Configuration

**What is it?**
Kafka is a message bus — a way for services to talk to each other without calling each other directly. A **producer** sends messages. Order-service will publish an `OrderPlacedEvent` to Kafka when an order is created.

- **bootstrap-servers** — the address of the Kafka broker (the central server that stores messages). When running locally with Docker Compose, it is at `localhost:9092`.
- **key-serializer** — how to convert the message key (a String) to bytes so Kafka can store it
- **value-serializer** — how to convert the message body (a Java object) to bytes. `JsonSerializer` converts it to JSON automatically.

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

> **Learn:** What is Kafka? What are topics, producers, and consumers? Why is async messaging useful instead of always calling services directly?

---

### 3. Kafka — Consumer Configuration

**What is it?**
Even though order-service is mainly a producer, it can also consume messages. A **consumer** reads messages from Kafka topics.

- **group-id** — all consumers with the same group ID share the work. If you have 3 instances of order-service, Kafka splits the messages between them. Each message is only processed once across the group.
- **auto-offset-reset: earliest** — if this consumer has never read from this topic before, start from the very first message. The alternative is `latest` (only read new messages).
- **key-deserializer / value-deserializer** — the reverse of serializers: convert bytes back to Java objects
- **spring.json.trusted.packages** — for security, Spring only deserializes JSON into classes from trusted packages. `com.learn.*` means any class in your project is allowed.

```yaml
spring:
  kafka:
    consumer:
      group-id: order-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.learn.*"
```

---

### 4. Feign Client Timeouts

**What is it?**
When order-service calls product-service or payment-service using Feign (a REST client), it needs to know how long to wait before giving up.

- **connect-timeout** — how many milliseconds to wait when trying to establish a connection (2000 ms = 2 seconds)
- **read-timeout** — how many milliseconds to wait for the response once connected (5000 ms = 5 seconds)

```yaml
feign:
  client:
    config:
      default:
        connect-timeout: 2000
        read-timeout: 5000
```

> **Learn:** What is **OpenFeign**? How does it let you call another service's REST API as if it were a local Java method? What happens when the timeout is exceeded?

---

### 5. Circuit Breaker (Resilience4j)

**What is it?**
A circuit breaker is a safety mechanism. Imagine order-service calls payment-service, but payment-service is down or very slow. Without a circuit breaker, order-service would hang waiting, and all incoming requests would pile up — eventually crashing order-service too.

A circuit breaker monitors calls and "opens" (stops calling the failing service) when too many fail. After a wait period, it goes "half-open" and tries a few test calls. If those succeed, it closes again (normal operation).

The settings below are for the `payment-service` circuit breaker inside order-service:

- **sliding-window-size: 10** — look at the last 10 calls to decide if things are healthy
- **minimum-number-of-calls: 5** — need at least 5 calls before making any health decision
- **failure-rate-threshold: 50** — if 50% or more of the last 10 calls failed, open the circuit
- **wait-duration-in-open-state: 10s** — wait 10 seconds in the open state before trying again
- **permitted-number-of-calls-in-half-open-state: 3** — allow 3 test calls when half-open
- **slow-call-rate-threshold: 50** — also consider a call a "failure" if it takes too long
- **slow-call-duration-threshold: 2s** — "too long" means longer than 2 seconds

```yaml
resilience4j:
  circuitbreaker:
    instances:
      payment-service:
        register-health-indicator: true
        sliding-window-size: 10
        minimum-number-of-calls: 5
        permitted-number-of-calls-in-half-open-state: 3
        wait-duration-in-open-state: 10s
        failure-rate-threshold: 50
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 2s
```

> **Learn:** What are the three states of a circuit breaker (Closed, Open, Half-Open)? What is a **fallback method**? What is the difference between a circuit breaker and a retry?

---

### 6. Eureka Registration

Same as other services.

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

---

### 7. Actuator with Tracing and Circuit Breaker Health

This service also exposes circuit breaker health and distributed tracing.

- **circuitbreakers** — exposes a `/actuator/circuitbreakers` endpoint showing circuit breaker state
- **tracing.sampling.probability: 1.0** — send 100% of requests to Zipkin for distributed tracing (in production you might use 0.1 = 10% to reduce overhead)

```yaml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus, circuitbreakers
  endpoint:
    health:
      show-details: always
  health:
    circuitbreakers:
      enabled: true
  tracing:
    sampling:
      probability: 1.0

logging:
  level:
    com.learn.order: DEBUG
    feign: DEBUG
```

> **Learn:** What is **distributed tracing**? What is **Zipkin**? How does a trace flow from API Gateway → order-service → payment-service and appear in Zipkin as a single timeline?

---

## Topics to Learn

- What is **Apache Kafka** and how does it compare to a REST API call?
- What is a **Kafka topic**, **partition**, **offset**, and **consumer group**?
- What is the **Circuit Breaker pattern** and the three states (Closed / Open / Half-Open)?
- What is **OpenFeign** and how do you annotate a Feign client interface?
- What is the **Outbox Pattern** for reliable event publishing?
- What is **distributed tracing** and how does Zipkin display it?
- What is the difference between **at-least-once** and **exactly-once** message delivery?

---

## Answer

<details>
<summary>Click to reveal the full application.yml (try first!)</summary>

```yaml
server:
  port: 8083

spring:
  application:
    name: order-service
  datasource:
    url: jdbc:h2:mem:orderdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
  h2:
    console:
      enabled: true
      path: /h2-console
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: order-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.learn.*"

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

resilience4j:
  circuitbreaker:
    instances:
      payment-service:
        register-health-indicator: true
        sliding-window-size: 10
        minimum-number-of-calls: 5
        permitted-number-of-calls-in-half-open-state: 3
        wait-duration-in-open-state: 10s
        failure-rate-threshold: 50
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 2s

feign:
  client:
    config:
      default:
        connect-timeout: 2000
        read-timeout: 5000

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus, circuitbreakers
  endpoint:
    health:
      show-details: always
  health:
    circuitbreakers:
      enabled: true
  tracing:
    sampling:
      probability: 1.0

logging:
  level:
    com.learn.order: DEBUG
    feign: DEBUG
```

</details>
