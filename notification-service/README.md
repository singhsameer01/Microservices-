# Notification Service

The Notification Service is a **pure Kafka consumer** — it has no REST API for business operations. It listens to both `order-placed` and `payment-processed` topics and sends notifications (currently structured log output; designed to be replaced with real email/SMS via Spring Mail).

- **Port:** `8085` (Actuator and Eureka registration only — no business HTTP endpoints)

---

## What It Does

### Event Consumption

```
order-service  ──Kafka──►  order-placed topic
                                │
                                ▼
                     NotificationConsumer.handleOrderPlaced()
                                │
                                ▼
                     NotificationService.sendOrderConfirmation()
                                │
                                ▼
                     Structured log: "Order confirmation for customer X, order Y"

payment-service ──Kafka──►  payment-processed topic
                                │
                                ▼
                     NotificationConsumer.handlePaymentProcessed()
                                │
                                ▼
                     NotificationService.sendPaymentReceipt()
                                │
                                ▼
                     Structured log: "Payment receipt for customer X, order Y, status SUCCESS"
```

### Structured Logging with Correlation

Every log entry for a notification is tagged with a `correlationId` in the MDC (Mapped Diagnostic Context):

```java
MDC.put("correlationId", "order-" + orderId);
// ... log the notification ...
MDC.clear();   // always cleared in finally block
```

This correlationId appears in every log line written during that handler invocation. Combined with the `traceId` and `spanId` from Micrometer/Zipkin, you can trace a single order event through all services in a distributed log aggregator like Kibana or Grafana Loki.

Log output format (JSON via logstash-logback-encoder):
```json
{
  "timestamp": "2024-01-15T10:30:02.123Z",
  "level": "INFO",
  "traceId": "abc123",
  "spanId": "def456",
  "correlationId": "order-42",
  "message": "Sending order confirmation for customer user-123, order 42"
}
```

### Manual Offset Acknowledgement

Both listeners use `AckMode.MANUAL_IMMEDIATE`. The consumer only acknowledges a message **after** successfully processing it:

```
receive message
    │
    ├─ process (send notification)
    ├─ acknowledgment.acknowledge()   ← offset committed to Kafka
    └─ MDC.clear()
```

If processing throws an exception before `acknowledge()`, Kafka will redeliver the message. After 3 retries (1s backoff), failed messages are routed to `{topic}.DLT` by the `DefaultErrorHandler`.

### Event Type Mapping

`notification-service` defines its own copies of `OrderPlacedEvent` and `PaymentProcessedEvent` in its local `event` package. The Kafka consumer is configured with type mappings so the deserializer converts the incoming event class names from `com.learn.order.event.*` and `com.learn.payment.event.*` to the local `com.learn.notification.event.*` equivalents — avoiding a cross-service dependency on shared POJOs.

---

## Kafka Integration

### Consumed: `order-placed` topic
- Consumer group: `notification-service-group`
- Offset: `earliest`
- Ack mode: `MANUAL_IMMEDIATE`

Event structure:
```json
{
  "orderId": 42,
  "customerId": "user-123",
  "amount": 449.97,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Consumed: `payment-processed` topic
- Same consumer group and ack settings

Event structure:
```json
{
  "paymentId": 7,
  "orderId": 42,
  "status": "SUCCESS",
  "timestamp": "2024-01-15T10:30:01Z"
}
```

### Dead Letter Topics
Failed messages go to `order-placed.DLT` and `payment-processed.DLT` after 3 retries.

---

## Extending to Real Notifications

The `NotificationService` is designed so you can swap out log statements for actual delivery:

```java
// Current implementation
public void sendOrderConfirmation(String customerId, Long orderId) {
    log.info("Sending order confirmation for customer {}, order {}", customerId, orderId);
}

// Replace with Spring Mail
@Autowired JavaMailSender mailSender;

public void sendOrderConfirmation(String customerId, Long orderId) {
    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setTo(lookupEmail(customerId));
    msg.setSubject("Order #" + orderId + " confirmed");
    mailSender.send(msg);
}
```

---

## Key Classes

| Class | Package | Role |
|-------|---------|------|
| `NotificationConsumer` | `messaging` | `@KafkaListener` for both topics, MDC management |
| `NotificationService` | `service` | Sends notifications (currently logs) |
| `KafkaConfig` | `config` | Manual ack container factory, DLT error handler |
| `OrderPlacedEvent` | `event` | Local copy of the order event record |
| `PaymentProcessedEvent` | `event` | Local copy of the payment event record |

---

## Configuration (`application.yml`)

```yaml
server:
  port: 8085

spring:
  kafka:
    consumer:
      group-id: notification-service-group
      auto-offset-reset: earliest
      enable-auto-commit: false
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: com.learn.notification.event
        spring.json.type.mapping: >
          com.learn.order.event.OrderPlacedEvent:com.learn.notification.event.OrderPlacedEvent,
          com.learn.payment.event.PaymentProcessedEvent:com.learn.notification.event.PaymentProcessedEvent
    listener:
      ack-mode: manual_immediate
```

---

## Running

```bash
# Prerequisites: Eureka + Kafka + Zookeeper must be running
mvn -f notification-service/pom.xml spring-boot:run
```

---

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-web` | Actuator HTTP endpoints only |
| `spring-kafka` | Kafka consumer |
| `spring-cloud-starter-netflix-eureka-client` | Service registration for health monitoring |
| `spring-boot-starter-actuator` | Health, metrics, prometheus |
| `logstash-logback-encoder` | JSON-structured log output |
| `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` | Distributed tracing (traceId in logs) |

---

## Role in the Platform

```
order-service ─── order-placed ─────────────────────────────┐
                                                             │
payment-service ─── payment-processed ──────────────────────┤
                                                             │
                                                             ▼
                                               notification-service :8085
                                                   │
                                                   ├─ handleOrderPlaced()  → log / send email
                                                   └─ handlePaymentProcessed() → log / send receipt
```
