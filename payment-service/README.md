# payment-service

**Port:** 8084

## Purpose
Processes payments. Consumes order events from Kafka and publishes payment result events. Also exposes a REST API for direct payment requests.

## What Needs to Be Here

### Data
- A `Payment` entity with: id, orderId, customerId, amount (BigDecimal), status (PENDING / SUCCESS / FAILED), createdAt
- Backed by its own H2 database

### DTOs
- `PaymentRequest` — input (Java 17 record): orderId, customerId, amount
- `PaymentResponse` — output (Java 17 record): paymentId, orderId, status

### API
- `POST /api/v1/payments` → processes a payment, returns 201
- `GET /api/v1/payments/{id}` → returns payment details
- `GET /api/v1/payments/order/{orderId}` → returns payment for a given order

### Kafka Consumer
- Listens on topic `order-placed`
- On receiving an `OrderPlacedEvent`, processes the payment automatically
- Uses manual offset commit (at-least-once delivery guarantee)
- Failed messages after max retries go to a Dead Letter Topic

### Kafka Producer
- After processing, publishes a `PaymentProcessedEvent` to topic `payment-processed`
- `PaymentProcessedEvent` contains: paymentId, orderId, status, timestamp

### Circuit Breaker
- Calls to an external bank gateway (simulated) are protected by a circuit breaker

### Error Handling
- Order not found → 404
- Payment already exists for order → 409
- Invalid input → 400

### Documentation
- All endpoints documented with OpenAPI/Swagger

### Registration
- Registers itself with Eureka

---

## Configuration to Write (`application.yml`)

> **How to use this section:** Every setting you need in `src/main/resources/application.yml` is explained below in plain English. Try to write each one yourself before looking at the answer.

---

### 1. Server Port, Application Name, Database

Port is `8084`, database name is `paymentdb`. Same H2 in-memory setup as other services.

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

### 2. Kafka — Consumer Configuration

**What is it?**
Payment-service listens to the `order-placed` topic. When order-service places an order and publishes an `OrderPlacedEvent`, payment-service receives it and automatically processes the payment.

- **group-id: payment-service-group** — payment-service consumers share this group ID. Each message from `order-placed` goes to exactly one consumer in the group.
- **auto-offset-reset: earliest** — if this is the first time the consumer connects, start from the beginning of the topic
- **key-deserializer / value-deserializer** — convert bytes from Kafka back into Java objects
- **trusted.packages** — only deserialize JSON into classes from `com.learn.*`

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: payment-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.learn.*"
```

> **Learn:** What is a **consumer group offset**? What does "at-least-once delivery" mean and why could you receive the same message twice? What is a **Dead Letter Topic**?

---

### 3. Kafka — Producer Configuration

**What is it?**
After processing a payment, payment-service publishes a `PaymentProcessedEvent` to the `payment-processed` topic. Notification-service listens to this topic to send receipts.

```yaml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

---

### 4. Circuit Breaker for Bank Gateway

**What is it?**
Payment-service simulates calling an external bank to process the charge. This external call could fail or be slow. The circuit breaker protects the service — if the bank gateway is down, the circuit opens and payments fail fast instead of hanging.

Settings for the `bank-gateway` circuit breaker:
- **sliding-window-size: 5** — look at the last 5 calls
- **failure-rate-threshold: 60** — open the circuit if 60% of calls fail
- **wait-duration-in-open-state: 15s** — wait 15 seconds before trying again
- **minimum-number-of-calls: 3** — need at least 3 calls before making any health decision

```yaml
resilience4j:
  circuitbreaker:
    instances:
      bank-gateway:
        sliding-window-size: 5
        failure-rate-threshold: 60
        wait-duration-in-open-state: 15s
        minimum-number-of-calls: 3
```

> **Learn:** Why is the threshold higher (60%) for bank-gateway than for order-service's payment-service circuit breaker (50%)? What is a **fallback** method in Resilience4j?

---

### 5. Eureka, Swagger, Actuator, and Logging

Same pattern as order-service. Also includes distributed tracing with Zipkin.

```yaml
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

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus, circuitbreakers
  endpoint:
    health:
      show-details: always
  tracing:
    sampling:
      probability: 1.0

logging:
  level:
    com.learn.payment: DEBUG
```

---

## Topics to Learn

- What is **manual offset commit** in Kafka and why use it instead of auto-commit?
- What is a **Dead Letter Topic (DLT)** and when does a message end up there?
- What is **idempotency** and why should payment processing be idempotent? (hint: the 409 status code)
- What is the difference between a Kafka **consumer** and a **listener** in Spring?
- How does a circuit breaker in payment-service protect against a slow external bank API?
- What is `@KafkaListener` annotation and how do you configure it?

---

## Answer

<details>
<summary>Click to reveal the full application.yml (try first!)</summary>

```yaml
server:
  port: 8084

spring:
  application:
    name: payment-service
  datasource:
    url: jdbc:h2:mem:paymentdb;DB_CLOSE_DELAY=-1
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
    consumer:
      group-id: payment-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.learn.*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

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
      bank-gateway:
        sliding-window-size: 5
        failure-rate-threshold: 60
        wait-duration-in-open-state: 15s
        minimum-number-of-calls: 3

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus, circuitbreakers
  endpoint:
    health:
      show-details: always
  tracing:
    sampling:
      probability: 1.0

logging:
  level:
    com.learn.payment: DEBUG
```

</details>
