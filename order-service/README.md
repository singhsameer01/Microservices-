# Order Service

The Order Service orchestrates the **order placement workflow**. It coordinates with product-service and payment-service via Feign clients, publishes events to Kafka using the Transactional Outbox Pattern, and wraps all downstream calls with Resilience4j circuit breakers.

- **Port:** `8083`
- **Swagger UI:** [http://localhost:8083/swagger-ui.html](http://localhost:8083/swagger-ui.html)
- **H2 Console:** [http://localhost:8083/h2-console](http://localhost:8083/h2-console) (JDBC URL: `jdbc:h2:mem:orderdb`)

---

## What It Does

### Order Placement Flow

```
Client
  │  POST /api/v1/orders
  ▼
OrderService.placeOrder()
  │
  ├─ 1. For each item: Feign GET /api/v1/products/{id}  → product-service
  │                    (lookup price, name, validate product exists)
  │
  ├─ 2. Build Order entity (status=PENDING), persist to H2 (same transaction)
  │
  ├─ 3. Save OrderPlacedEvent to outbox table  (same DB transaction as step 2)
  │
  ├─ 4. PaymentProcessor.processPayment()  [circuit-breaker protected]
  │      └─ Feign POST /api/v1/payments → payment-service
  │           ├─ SUCCESS → order.status = CONFIRMED
  │           └─ FAILURE / circuit open → paymentFallback() → order.status = CANCELLED
  │
  └─ 5. Return 201 OrderResponse
```

Steps 2 and 3 commit atomically. Even if the service crashes after the DB commit but before Kafka publish, the `OutboxEventPublisher` will pick up and publish the event on the next poll cycle.

### Transactional Outbox Pattern

Instead of publishing directly to Kafka inside the business transaction, the service saves a serialized `OrderPlacedEvent` to the `outbox_events` table in the **same transaction** as the order. A separate scheduled component (`OutboxEventPublisher`) polls every 5 seconds for unpublished events, sends them to Kafka, then marks them published.

This prevents the "dual write" problem: without the outbox, a crash between DB commit and Kafka publish would result in a lost event that no downstream consumer would ever process.

### Resilience Patterns

`PaymentProcessor` is a dedicated `@Service` bean (not part of `OrderService`) so that Spring AOP can intercept the Resilience4j annotations:

| Annotation | Config | Behaviour |
|------------|--------|-----------|
| `@CircuitBreaker(name="payment-service")` | Window 10, 50% threshold, 10s wait | Opens after 5 failures in 10 calls |
| `@Retry(name="payment-service")` | Max 3 attempts, 500ms, exponential x2 | Retries transient failures |
| `@Bulkhead(name="payment-service")` | Max 10 concurrent, 100ms wait | Limits parallel calls to payment-service |

All three are stacked on `processPayment()`. On any failure beyond retries, `paymentFallback()` is called, which sets order status to `CANCELLED`.

### Token Relay

`FeignConfig.tokenRelayInterceptor()` reads the `Authorization: Bearer <JWT>` from the incoming HTTP request and forwards it to all outgoing Feign calls. This means the user's token is propagated downstream to product-service and payment-service without re-authentication.

---

## REST API

All endpoints require `Authorization: Bearer <JWT>`.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/orders/` | Place a new order → `201` with `Location` header |
| `GET` | `/api/v1/orders/{id}` | Get order by ID |
| `GET` | `/api/v1/orders/customer/{customerId}` | Paginated orders for a customer |

**Place order request:**
```json
{
  "customerId": "user-123",
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 5, "quantity": 1 }
  ]
}
```

**Response:**
```json
{
  "id": 42,
  "customerId": "user-123",
  "status": "CONFIRMED",
  "totalAmount": 449.97,
  "items": [
    { "productId": 1, "productName": "Headphones", "quantity": 2, "price": 149.99 },
    { "productId": 5, "productName": "Keyboard", "quantity": 1, "price": 149.99 }
  ],
  "createdAt": "2024-01-15T10:30:00Z"
}
```

**Order statuses:** `PENDING` → `CONFIRMED` or `CANCELLED`

---

## Database Schema

### `orders`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT | Auto-generated PK |
| `customer_id` | VARCHAR | Reference to user |
| `status` | VARCHAR | PENDING / CONFIRMED / CANCELLED |
| `total_amount` | DECIMAL | Auto-calculated from items |
| `created_at` | TIMESTAMP | Immutable |

### `order_items`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT | PK |
| `order_id` | BIGINT | FK → orders |
| `product_id` | BIGINT | Product reference |
| `product_name` | VARCHAR | Snapshot at order time |
| `quantity` | INTEGER | |
| `price` | DECIMAL | Snapshot at order time |

### `outbox_events`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT | PK |
| `aggregate_type` | VARCHAR | e.g. `ORDER` |
| `aggregate_id` | BIGINT | Order ID |
| `event_type` | VARCHAR | e.g. `OrderPlacedEvent` |
| `payload` | TEXT | JSON-serialized event |
| `created_at` | TIMESTAMP | |
| `published` | BOOLEAN | false until Kafka publish succeeds |

---

## Key Classes

| Class | Package | Role |
|-------|---------|------|
| `OrderController` | `controller` | REST endpoints |
| `OrderService` | `service` | Orchestration logic, outbox save |
| `PaymentProcessor` | `service` | Resilience4j-wrapped payment call (separate bean for AOP) |
| `ProductClient` | `client` | `@FeignClient(name="product-service")` |
| `PaymentClient` | `client` | `@FeignClient(name="payment-service")` |
| `FeignConfig` | `config` | Token relay interceptor, error decoder |
| `OrderEventPublisher` | `messaging` | `KafkaTemplate` publisher |
| `OutboxEventPublisher` | `messaging` | `@Scheduled` outbox poller |

---

## Kafka Events

### Published: `order-placed` topic
```json
{
  "orderId": 42,
  "customerId": "user-123",
  "totalAmount": 449.97,
  "timestamp": "2024-01-15T10:30:00Z"
}
```
Message key: `orderId` (ensures all events for the same order go to the same partition, preserving order).

Producer config: `acks=all`, `retries=3`, `enable.idempotence=true` — guarantees exactly-once delivery to Kafka.

---

## Configuration (`application.yml`)

```yaml
server:
  port: 8083

resilience4j:
  circuitbreaker:
    instances:
      payment-service:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        minimumNumberOfCalls: 5
  retry:
    instances:
      payment-service:
        maxAttempts: 3
        waitDuration: 500ms
        multiplier: 2
  bulkhead:
    instances:
      payment-service:
        maxConcurrentCalls: 10
        maxWaitDuration: 100ms
```

---

## Running

```bash
# Prerequisites: Eureka + Kafka + Zookeeper must be running
mvn -f order-service/pom.xml spring-boot:run
```

---

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `spring-cloud-starter-openfeign` | Declarative REST clients for product/payment service |
| `spring-cloud-starter-circuitbreaker-resilience4j` | Circuit breaker, retry, bulkhead |
| `spring-kafka` | Kafka producer (order events) |
| `spring-boot-starter-data-jpa` + `h2` | Order and outbox persistence |
| `spring-boot-starter-oauth2-resource-server` | JWT validation |
| `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` | Distributed tracing |
