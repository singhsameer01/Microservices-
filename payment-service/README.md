# Payment Service

The Payment Service **processes payments** for orders. It consumes order events from Kafka (triggered by order-service), processes the payment via a simulated bank gateway (with its own circuit breaker), guarantees idempotency to survive Kafka redelivery, and publishes payment outcome events back to Kafka.

- **Port:** `8084`
- **Swagger UI:** [http://localhost:8084/swagger-ui.html](http://localhost:8084/swagger-ui.html)
- **H2 Console:** [http://localhost:8084/h2-console](http://localhost:8084/h2-console) (JDBC URL: `jdbc:h2:mem:paymentdb`)

---

## What It Does

### Two Ways to Process a Payment

Payments can be initiated in two ways:

1. **Kafka (async):** `order-service` publishes an `OrderPlacedEvent` to the `order-placed` topic. `OrderPlacedConsumer` picks it up and calls `paymentService.process()`.
2. **HTTP (sync):** `order-service` calls `POST /api/v1/payments` directly via Feign when the circuit is closed.

Both paths call the same `PaymentService.process()` method, which includes idempotency checks.

### Payment Processing Flow

```
OrderPlacedConsumer.handleOrderPlaced(event)
  │
  ▼
PaymentService.process(request)
  │
  ├─ 1. Idempotency check: if payment for this orderId already exists → throw
  │      (PaymentAlreadyExistsException is non-retryable — consumer acknowledges and skips)
  │
  ├─ 2. Create Payment entity (status=PENDING, transactionId=new UUID)
  │
  ├─ 3. callBankGateway()  [@CircuitBreaker(name="bank-gateway")]
  │       ├─ Simulates external bank API call → returns SUCCESS
  │       └─ fallback: returns FAILED
  │
  ├─ 4. Set payment.status = SUCCESS or FAILED, save
  │
  └─ 5. Publish PaymentProcessedEvent to "payment-processed" Kafka topic
```

### Idempotency

Each payment record has a unique constraint on `orderId`. If Kafka redelivers the same `OrderPlacedEvent` (at-least-once delivery), the `process()` call throws `PaymentAlreadyExistsException` on the duplicate check. The Kafka consumer catches this, logs it, and acknowledges the message without reprocessing — preventing double-charging.

### Bank Gateway Circuit Breaker

`callBankGateway()` simulates calling an external bank API. It is wrapped in `@CircuitBreaker(name="bank-gateway")`:

| Config | Value | Meaning |
|--------|-------|---------|
| `slidingWindowSize` | 5 | Track last 5 calls |
| `failureRateThreshold` | 60% | Open after 3 failures in 5 calls |
| `waitDurationInOpenState` | 15s | Stay open for 15s, then try half-open |
| `minimumNumberOfCalls` | 3 | Need 3 calls before evaluating |

When the circuit is open, `bankGatewayFallback()` returns `FAILED` immediately without calling the bank.

### Dead Letter Topics

Failed Kafka messages (after 3 retries with 1s backoff) are routed to `{topic}.DLT` (e.g., `order-placed.DLT`) by `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`. This prevents poison messages from blocking the consumer indefinitely.

---

## REST API

These endpoints are also called directly by `order-service` via Feign when operating synchronously.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/payments/` | Process a payment → `201` |
| `GET` | `/api/v1/payments/{id}` | Get payment by ID |
| `GET` | `/api/v1/payments/order/{orderId}` | Get payment for a specific order |

**Request body:**
```json
{
  "orderId": 42,
  "customerId": "user-123",
  "amount": 449.97
}
```

**Response:**
```json
{
  "id": 7,
  "orderId": 42,
  "customerId": "user-123",
  "amount": 449.97,
  "status": "SUCCESS",
  "transactionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "createdAt": "2024-01-15T10:30:01Z"
}
```

**Payment statuses:** `PENDING` → `SUCCESS` or `FAILED`

---

## Database Schema

### `payments`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT | Auto-generated PK |
| `order_id` | BIGINT | Unique — enforces idempotency |
| `customer_id` | VARCHAR | |
| `amount` | DECIMAL | |
| `status` | VARCHAR | PENDING / SUCCESS / FAILED |
| `transaction_id` | VARCHAR | UUID, unique, generated on construction |
| `created_at` | TIMESTAMP | Immutable |

---

## Key Classes

| Class | Package | Role |
|-------|---------|------|
| `PaymentController` | `controller` | REST endpoints |
| `PaymentService` | `service` | Core payment logic, idempotency, bank call |
| `OrderPlacedConsumer` | `messaging` | Kafka consumer for `order-placed` topic |
| `PaymentEventPublisher` | `messaging` | Publishes `PaymentProcessedEvent` to Kafka |
| `KafkaConfig` | `config` | Manual ack, DLT error handler, dead letter routing |
| `SecurityConfig` | `security` | Stateless JWT resource server |

---

## Kafka Integration

### Consumed: `order-placed` topic
```json
{
  "orderId": 42,
  "customerId": "user-123",
  "amount": 449.97,
  "timestamp": "2024-01-15T10:30:00Z"
}
```
Consumer group: `payment-service-group`. Offset: `earliest`. Manual acknowledgement (`AckMode.MANUAL_IMMEDIATE`).

### Published: `payment-processed` topic
```json
{
  "paymentId": 7,
  "orderId": 42,
  "status": "SUCCESS",
  "timestamp": "2024-01-15T10:30:01Z"
}
```
Message key: `orderId`.

---

## Configuration (`application.yml`)

```yaml
server:
  port: 8084

resilience4j:
  circuitbreaker:
    instances:
      bank-gateway:
        slidingWindowSize: 5
        failureRateThreshold: 60
        waitDurationInOpenState: 15s
        minimumNumberOfCalls: 3

app:
  kafka:
    topics:
      order-placed: order-placed
      payment-processed: payment-processed
```

---

## Running

```bash
# Prerequisites: Eureka + Kafka + Zookeeper must be running
mvn -f payment-service/pom.xml spring-boot:run
```

---

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-web` | REST API |
| `spring-boot-starter-data-jpa` + `h2` | Payment persistence |
| `spring-cloud-starter-circuitbreaker-resilience4j` | Bank gateway circuit breaker |
| `spring-kafka` | Consumer and producer |
| `spring-boot-starter-oauth2-resource-server` | JWT validation |
| `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` | Distributed tracing |
