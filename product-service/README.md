# Product Service

The Product Service manages the **product catalog** — creating, reading, updating, and deleting products. It adds **Redis caching** on top of the database to avoid repeated reads for the same product, and uses **optimistic locking** to handle concurrent stock updates safely.

- **Port:** `8082`
- **Swagger UI:** [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)
- **H2 Console:** [http://localhost:8082/h2-console](http://localhost:8082/h2-console) (JDBC URL: `jdbc:h2:mem:productdb`)

---

## What It Does

### Product CRUD

Full create/read/update/delete for products. All write endpoints require a valid JWT (passed by the gateway or directly via `Authorization: Bearer`).

### Redis Caching

`findById` results are cached in Redis with a 5-minute TTL. Cache entries are evicted on create, update, and delete via Spring's `@Cacheable` / `@CacheEvict` annotations. This means:
- First `GET /products/42` → hits H2, stores result in Redis
- Subsequent `GET /products/42` within 5 minutes → served from Redis, no DB query
- `PUT /products/42` or `DELETE /products/42` → invalidates that cache entry

Cache serialization uses `GenericJackson2JsonRedisSerializer` (JSON, not Java serialization).

### Optimistic Locking

The `Product` entity has a `@Version Long version` field. If two requests update the same product simultaneously (e.g., two orders decrementing stock), one will succeed and the other receives a `409 Conflict` from JPA — preventing a lost update without database-level row locking.

---

## REST API

All endpoints require `Authorization: Bearer <JWT>` except `/actuator/**`, `/h2-console/**`, and `/swagger-ui/**`.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/products/` | Create product → `201` with `Location` header |
| `GET` | `/api/v1/products/{id}` | Get product by ID (cached) |
| `GET` | `/api/v1/products/` | Paginated list, optional `?category=` filter |
| `PUT` | `/api/v1/products/{id}` | Full update → `200 ProductResponse` |
| `DELETE` | `/api/v1/products/{id}` | Delete → `204` |

**Create/Update request body:**
```json
{
  "name": "Wireless Headphones",
  "description": "Noise-cancelling over-ear",
  "price": 149.99,
  "stock": 50,
  "category": "Electronics"
}
```

**Response:**
```json
{
  "id": 1,
  "name": "Wireless Headphones",
  "description": "Noise-cancelling over-ear",
  "price": 149.99,
  "stock": 50,
  "category": "Electronics"
}
```

---

## Database Schema

### `products`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT | Auto-generated PK |
| `name` | VARCHAR | Not null |
| `description` | VARCHAR | Optional |
| `price` | DECIMAL(10,2) | BigDecimal, not null |
| `stock` | INTEGER | Available quantity |
| `category` | VARCHAR | For filtering |
| `version` | BIGINT | Optimistic lock version counter |

---

## Key Classes

| Class | Package | Role |
|-------|---------|------|
| `ProductController` | `controller` | REST endpoints |
| `ProductService` | `service` | CRUD logic, `@Cacheable`/`@CacheEvict` annotations |
| `Product` | `model` | JPA entity with `@Version` optimistic lock |
| `CacheConfig` | `config` | `RedisCacheManager` with 5-min TTL, JSON serializer |
| `SecurityConfig` | `security` | Stateless JWT resource server |

---

## Caching Behaviour

```
ProductService.findById(id)
    │
    ├── @Cacheable(value="products", key="#id")
    │       │
    │       ├── Cache HIT  → return from Redis (no DB call)
    │       └── Cache MISS → query H2, store in Redis with 5-min TTL, return
    │
ProductService.create(req)   → @CacheEvict(value="products", allEntries=true)
ProductService.update(id, req) → @CacheEvict(value="products", key="#id")
ProductService.delete(id)    → @CacheEvict(value="products", key="#id")
```

---

## Configuration (`application.yml`)

```yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:h2:mem:productdb;DB_CLOSE_DELAY=-1
  data:
    redis:
      host: localhost
      port: 6379
  cache:
    type: redis
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:8081/.well-known/jwks.json
```

---

## Running

```bash
# Prerequisites: Eureka Server + Redis must be running
mvn -f product-service/pom.xml spring-boot:run
```

---

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-web` | REST controllers |
| `spring-boot-starter-data-jpa` + `h2` | Product persistence |
| `spring-boot-starter-cache` + `spring-boot-starter-data-redis` | Redis-backed caching |
| `spring-boot-starter-oauth2-resource-server` | JWT validation |
| `spring-boot-starter-validation` | Request body validation |
| `springdoc-openapi-starter-webmvc-ui` | Swagger UI |
| `spring-cloud-starter-netflix-eureka-client` | Service registration |
| `micrometer-registry-prometheus` | Prometheus metrics |

---

## Role in the Platform

Order Service calls this service (via Feign) to fetch product details when placing an order:
```
order-service
    │  GET /api/v1/products/{id}   (via @FeignClient(name="product-service"))
    ▼
product-service :8082
    │  @Cacheable → Redis → H2
```
