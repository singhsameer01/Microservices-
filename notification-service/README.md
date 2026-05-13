# notification-service

**Port:** 8085

## Purpose
Reacts to system events and sends notifications (email/log). Pure event consumer — no REST API for business operations.

## What Needs to Be Here

### Kafka Consumers
- Listens on topic `order-placed` → sends order confirmation notification
- Listens on topic `payment-processed` → sends payment receipt notification
- Both consumers use manual offset commit
- Failed messages go to a Dead Letter Topic

### Notification Logic
- A `NotificationService` that handles the notification action (log to console initially, wire real email later)

### Observability
- Structured JSON logging (every log line is valid JSON)
- Each log entry includes: timestamp, level, service name, traceId, spanId, correlationId
- A correlationId is extracted from the Kafka message and attached to logs for the entire processing span
- Distributed traces sent to Zipkin

### Registration
- Registers itself with Eureka

---

## Configuration to Write (`application.yml`)

> **How to use this section:** Notification-service has the simplest configuration — no database, no circuit breaker. It only needs Kafka and Eureka. Try to write it yourself before revealing the answer.

---

### 1. Server Port and Application Name

Port is `8085`. Even though this service has no REST API for business operations, it still runs an HTTP server (for the Actuator health endpoints and Eureka registration).

```yaml
server:
  port: ???

spring:
  application:
    name: ???
```

---

### 2. Kafka Consumer Configuration

**What is it?**
Notification-service is a **pure consumer** — it only reads from Kafka, never writes. It listens to two topics:
1. `order-placed` — published by order-service when an order is created
2. `payment-processed` — published by payment-service after a payment is processed

For each message received, it sends a notification (currently just a log message).

- **group-id: notification-service-group** — this service's consumer group. If you scale notification-service to 3 instances, Kafka splits the messages between them.
- **auto-offset-reset: earliest** — if connecting for the first time, read from the beginning of the topic
- **key-deserializer / value-deserializer** — convert the raw bytes from Kafka back into Java objects
- **trusted.packages** — only deserialize into classes from `com.learn.*` packages

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: notification-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.learn.*"
```

> **Learn:** This service has no `producer` config — it only consumes. What would happen if you ran two copies of notification-service at the same time? Would a customer get two notifications, or just one? (hint: consumer groups)

---

### 3. Register With Eureka

**What is it?**
Even though nothing calls notification-service directly (it receives events from Kafka, not HTTP requests), it still registers with Eureka. This lets the Eureka dashboard show it as healthy and lets Zipkin correlate its traces with other services.

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

---

### 4. Actuator Endpoints and Distributed Tracing

**What is it?**
Same pattern as order-service and payment-service. The `tracing.sampling.probability: 1.0` sends every trace to Zipkin so you can see the full journey from API Gateway → order-service → Kafka → notification-service in one timeline.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics
  endpoint:
    health:
      show-details: always
  tracing:
    sampling:
      probability: 1.0
```

---

### 5. Logging Level

```yaml
logging:
  level:
    com.learn.notification: DEBUG
```

> **Learn:** Notification-service is supposed to use **structured JSON logging** (each log line is valid JSON with fields like `traceId`, `spanId`, `correlationId`). This is done with a `logback-spring.xml` config file, not `application.yml`. Look up how to configure Logback with a JSON encoder (hint: Logstash encoder).

---

## Topics to Learn

- What is **structured logging** and why is it better than plain text logs in a microservices system?
- What is a **traceId** and a **spanId** in distributed tracing?
- What is a **correlationId** and how do you pass it through a Kafka message?
- Why does notification-service use **manual offset commit** instead of auto-commit?
- What is a **Dead Letter Topic** and how do you configure one in Spring Kafka?
- How would you replace the console log with a real email notification? (hint: Spring Mail / JavaMailSender)
- What is **KEDA** (Kubernetes Event Driven Autoscaling) and how could it scale notification-service based on Kafka lag?

---

## Answer

<details>
<summary>Click to reveal the full application.yml (try first!)</summary>

```yaml
server:
  port: 8085

spring:
  application:
    name: notification-service
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: notification-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.learn.*"

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
        include: health, info, metrics
  endpoint:
    health:
      show-details: always
  tracing:
    sampling:
      probability: 1.0

logging:
  level:
    com.learn.notification: DEBUG
```

</details>
