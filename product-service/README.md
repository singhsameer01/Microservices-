# product-service

**Port:** 8082

## Purpose
Manages the product catalog. Owned exclusively by this service — no other service accesses its database directly.

## What Needs to Be Here

### Data
- A `Product` entity with: id, name, description, price (BigDecimal), stock (Integer), category
- Optimistic locking on the entity (to handle concurrent stock updates)
- A `ProductRepository` backed by its own H2 database

### DTOs
- `ProductRequest` — input with validation constraints (Java 17 record)
- `ProductResponse` — output (Java 17 record)

### API
- `POST /api/v1/products` → creates product, returns 201 + Location header
- `GET /api/v1/products/{id}` → returns product or 404
- `GET /api/v1/products` → paginated + filterable by category
- `PUT /api/v1/products/{id}` → full update, returns 200
- `DELETE /api/v1/products/{id}` → returns 204

### Error Handling
- Product not found → 404
- Invalid request body → 400 with field errors

### Documentation
- All endpoints documented with OpenAPI/Swagger
- Swagger UI accessible

### Registration
- Registers itself with Eureka

---

## Configuration to Write (`application.yml`)

> **How to use this section:** Every setting you need in `src/main/resources/application.yml` is explained below in plain English. Try to write each one yourself before looking at the answer.

---

### 1. Server Port and Application Name

**What is it?**
The port this service runs on (`8082`) and its name in the Eureka registry and logs.

```yaml
server:
  port: ???

spring:
  application:
    name: ???
```

---

### 2. In-Memory Database (H2)

**What is it?**
Each microservice has its own separate database. This is the **Database-per-Service** pattern. Product-service uses an in-memory H2 database called `productdb`. It only lives while the app is running.

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:productdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
```

> **Learn:** Why do microservices each have their own database instead of sharing one? What problems would you have if order-service and product-service shared the same database?

---

### 3. JPA / Hibernate Settings

**What is it?**
Controls how Hibernate manages your database schema and SQL logging.

- **ddl-auto: create-drop** — automatically creates tables from your Java entities on startup, drops them on shutdown
- **show-sql: true** — prints every SQL query to the console
- **format_sql: true** — makes the printed SQL readable

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

---

### 4. H2 Console

**What is it?**
A browser-based database UI built into H2. Access it at `http://localhost:8082/h2-console` while the service is running.

```yaml
spring:
  h2:
    console:
      enabled: true
      path: /h2-console
```

---

### 5. Swagger / OpenAPI Documentation

**What is it?**
Auto-generated interactive API docs. Browse and test your endpoints at `http://localhost:8082/swagger-ui.html`.

```yaml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
```

---

### 6. Register With Eureka

**What is it?**
Registers this service so the API gateway (and order-service via Feign) can find it by name rather than by hardcoded IP and port.

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

---

### 7. Expose Actuator Endpoints

**What is it?**
Exposes health, info, metrics, and prometheus endpoints for monitoring.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  endpoint:
    health:
      show-details: always
```

---

### 8. Logging Level

**What is it?**
Sets DEBUG logging for the product-service package so you can see detailed output while developing.

```yaml
logging:
  level:
    com.learn.product: DEBUG
```

---

## Topics to Learn

- What is **Optimistic Locking** (hint: `@Version` annotation in JPA)? Why does product-service need it for stock updates?
- What is the **Database-per-Service** pattern and what are the trade-offs?
- What is `BigDecimal` and why is it used for prices instead of `double`?
- What is **pagination** in Spring Data JPA (`Pageable`)?
- What is a **Location header** in an HTTP 201 response?

---

## Answer

<details>
<summary>Click to reveal the full application.yml (try first!)</summary>

```yaml
server:
  port: 8082

spring:
  application:
    name: product-service
  datasource:
    url: jdbc:h2:mem:productdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
  h2:
    console:
      enabled: true
      path: /h2-console

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
        include: health, info, metrics, prometheus
  endpoint:
    health:
      show-details: always

logging:
  level:
    com.learn.product: DEBUG
```

</details>
