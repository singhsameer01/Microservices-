# user-service

**Port:** 8081

## Purpose
Manages user accounts and authentication. Issues JWT tokens consumed by the gateway and other services.

## What Needs to Be Here

### Data
- A `User` entity with: id, username, email, password (hashed), role, createdAt
- A `UserRepository` backed by its own H2 database (no shared DB with other services)

### DTOs
- `RegisterRequest` — input for registration (Java 17 record)
- `LoginRequest` — input for login (Java 17 record)
- `UserResponse` — output (never exposes the password)

### API
- `POST /api/v1/auth/register` — creates a new user, returns 201
- `POST /api/v1/auth/login` — validates credentials, returns a JWT token
- `GET /api/v1/users/{id}` — returns user info (requires authentication)
- `GET /api/v1/users` — paginated user list (admin only)

### Security
- Passwords stored as BCrypt hashes
- JWT token generation and validation
- Stateless session (no server-side sessions)
- Endpoints protected except `/api/v1/auth/**`
- Admin-only endpoints enforced at method level

### Documentation
- All endpoints documented with OpenAPI/Swagger
- Swagger UI accessible and testable with JWT bearer auth

### Error Handling
- Duplicate username/email → 409
- Invalid input → 400 with field-level errors
- User not found → 404

### Registration
- Registers itself with Eureka

---

## Configuration to Write (`application.yml`)

> **How to use this section:** Every setting you need in `src/main/resources/application.yml` is explained below in plain English. Try to write each one yourself before looking at the answer.

---

### 1. Server Port and Application Name

**What is it?**
The port this service runs on. `8081` is the convention for user-service in this project. The application name is what appears in Eureka's registry and in log output.

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
Instead of installing a real database like MySQL or PostgreSQL, you use H2 — a tiny database that lives entirely in your computer's memory. It starts fresh every time the app starts and disappears when it stops. Perfect for learning.

- **url** — the connection string. `jdbc:h2:mem:userdb` means "use an in-memory H2 database named `userdb`". The `;DB_CLOSE_DELAY=-1` part keeps it alive as long as the app runs.
- **driver-class-name** — the Java class H2 uses to handle database connections
- **username / password** — H2's default credentials are `sa` with no password

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:userdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
```

> **Learn:** What is JDBC? What is a "driver class"? Why does each microservice have its own separate database (this is the Database-per-Service pattern)?

---

### 3. JPA / Hibernate Settings

**What is it?**
JPA (Java Persistence API) is the standard way to talk to databases in Java. Hibernate is the most popular implementation. These settings control how it behaves.

- **ddl-auto: create-drop** — when the app starts, automatically create all database tables from your Java entities. When it stops, drop (delete) everything. Great for development, never use in production.
- **show-sql: true** — print every SQL query to the console. Helps you understand what Hibernate is doing under the hood.
- **format_sql: true** — make the printed SQL readable (with line breaks and indentation)

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

> **Learn:** What are the other `ddl-auto` options (`validate`, `update`, `none`)? Why would you never use `create-drop` in a real production database?

---

### 4. H2 Console (Database Browser)

**What is it?**
H2 comes with a built-in web UI that lets you browse your in-memory database like a mini SQL client in your browser. Enabling this makes it accessible at `http://localhost:8081/h2-console`.

```yaml
spring:
  h2:
    console:
      enabled: true
      path: /h2-console
```

> **Learn:** Open `http://localhost:8081/h2-console` after starting the service. Connect using the JDBC URL, username, and password from your datasource config. Browse the tables that Hibernate created from your entities.

---

### 5. Swagger / OpenAPI Documentation

**What is it?**
SpringDoc automatically generates interactive API documentation from your code. This lets you test your endpoints in a browser without needing Postman.

- `/api-docs` — the raw JSON description of your API (machine-readable)
- `/swagger-ui.html` — the human-friendly interactive UI

```yaml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
```

> **Learn:** Start the service, open `http://localhost:8081/swagger-ui.html`. Try calling the register and login endpoints from there.

---

### 6. JWT Secret and Expiration

**What is it?**
JWT (JSON Web Token) is how this service proves that a user is who they say they are. The secret is a secret key used to sign and verify tokens — think of it as a password that only your server knows.

- **secret** — a long hex string used to sign JWTs. This exact same secret must also be in api-gateway (that is how the gateway can verify tokens without calling user-service)
- **expiration** — how long (in milliseconds) a token stays valid. `86400000` = 24 hours (24 × 60 × 60 × 1000)

```yaml
jwt:
  secret: 404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
  expiration: 86400000
```

> **Learn:** What is a JWT? What are the three parts (header, payload, signature)? Why must the same secret be in both user-service AND api-gateway? What happens when a token expires?

---

### 7. Register With Eureka

**What is it?**
This service needs to announce itself to the Eureka registry so the API gateway can find and route traffic to it.

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

---

### 8. Expose Actuator Endpoints (with Prometheus)

**What is it?**
This service exposes more actuator endpoints than eureka-server because we want to collect metrics from it.

- `health` — is the service up?
- `info` — app metadata
- `metrics` — internal counters (requests processed, memory used, etc.)
- `prometheus` — same metrics but in a format Prometheus can scrape

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

> **Learn:** Open `http://localhost:8081/actuator/prometheus` after starting. What do you see? This is what Prometheus reads every few seconds to collect metrics.

---

### 9. Logging Level

**What is it?**
Controls how much log output you see in the console. `DEBUG` is very verbose — it prints everything, including SQL queries and internal method calls. Good for learning, noisy in production.

```yaml
logging:
  level:
    com.learn.user: DEBUG
```

> **Learn:** What are the log levels in order from most to least verbose: TRACE, DEBUG, INFO, WARN, ERROR? What level would you use in production?

---

## Topics to Learn

- What is **JPA** and how does it map Java objects to database tables?
- What is **Hibernate** and how is it related to JPA?
- What is a **JWT** (JSON Web Token) and how does it work?
- What is **BCrypt** and why is it used for passwords instead of MD5 or SHA?
- What is the **Database-per-Service** pattern and why does each microservice need its own DB?
- What is **Spring Security** and how do you configure it for stateless JWT auth?
- What is **OpenAPI/Swagger** and how does SpringDoc generate it automatically?

---

## Answer

<details>
<summary>Click to reveal the full application.yml (try first!)</summary>

```yaml
server:
  port: 8081

spring:
  application:
    name: user-service
  datasource:
    url: jdbc:h2:mem:userdb;DB_CLOSE_DELAY=-1
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

jwt:
  secret: 404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
  expiration: 86400000

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
    com.learn.user: DEBUG
```

</details>
