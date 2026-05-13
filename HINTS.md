# HINTS — Open Only When Stuck

This file gives you just enough context to get unstuck. It does not give you the implementation.

---

## eureka-server

**What it needs to become:** A Netflix Eureka registry server.

Spring Cloud provides `@EnableEurekaServer` — that single annotation on the main class plus the right dependency in pom.xml is all that's needed to turn a Spring Boot app into a registry.

The application.yml already has `register-with-eureka: false` and `fetch-registry: false`. These exist because the server itself shouldn't try to register with or fetch from itself.

When it's working, open `http://localhost:8761` — you'll see the Eureka dashboard. Registered services show up under "Instances currently registered with Eureka".

**Common gotcha:** If the dashboard loads but shows "EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP WHEN THEY'RE NOT", that's self-preservation mode activating. It's normal during development when services aren't running.

---

## api-gateway

**What it needs to become:** A reactive proxy that forwards requests to downstream services and validates JWT tokens.

This service uses Spring Cloud Gateway which is WebFlux-based (reactive). This matters because:
- Filters implement `GlobalFilter` + `GatewayFilter`, not servlet filters
- You work with `ServerWebExchange` instead of `HttpServletRequest` / `HttpServletResponse`
- Do not add `spring-boot-starter-web` — it conflicts with WebFlux

**Routing** is already in application.yml. The `lb://service-name` URIs are resolved to actual instances via Eureka. `@EnableDiscoveryClient` on the main class enables that resolution.

**JWT Filter:** Create a class implementing `GlobalFilter`. Inside it:
- Get the `Authorization` header from `exchange.getRequest().getHeaders()`
- Strip the `Bearer ` prefix
- Use the same JWT library (jjwt) that user-service uses to validate the signature
- If invalid → return `exchange.getResponse().setComplete()` after setting status to 401
- If valid → pass the request down the chain via `chain.filter(exchange)`
- For paths that should skip auth (like `/api/v1/auth/**`), check the path before validating

**Rate Limiting** requires Redis. Add the Redis reactive dependency, configure a `KeyResolver` bean, and use `RequestRateLimiter` filter in application.yml on the route you want to limit.

**Common gotcha:** Spring Cloud Gateway auto-configures a lot. If you see "no routes" errors, confirm `spring-cloud-starter-gateway` is in pom.xml and `spring-boot-starter-web` is NOT.

---

## user-service

**What it needs to become:** A service that registers users, hashes passwords, and issues JWT tokens.

### JPA Entity
`User` is a standard `@Entity`. Mark `username` and `email` as `@Column(unique = true)` to get a database-level uniqueness constraint. `@CreationTimestamp` from Hibernate can auto-populate `createdAt`.

### Password Hashing
`BCryptPasswordEncoder` is the Spring Security standard. Register it as a `@Bean` in your SecurityConfig. Use `encoder.encode(rawPassword)` on save and `encoder.matches(raw, hashed)` on login.

### JWT
The jjwt library is already in pom.xml. Key methods:
- Build a token: `Jwts.builder().setSubject(...).signWith(...).compact()`
- Parse a token: `Jwts.parserBuilder().setSigningKey(...).build().parseClaimsJws(token)`
- The secret in application.yml is a hex string — decode it with `Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))`

### Security Config
`SecurityFilterChain` bean replaces the old `WebSecurityConfigurerAdapter` in Spring Security 6. Use `http.sessionManagement().sessionCreationPolicy(STATELESS)` for no sessions. `http.csrf().disable()` is needed since you're using JWT. Add your `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`.

### UserDetailsService
Spring Security expects a `UserDetailsService` bean with `loadUserByUsername(String)`. Implement this in your `UserService` — load the user from the DB and wrap it in a `UserDetails` (or have `User` implement `UserDetails` directly).

**Common gotchas:**
- Spring Security 6 uses `jakarta.*` not `javax.*`
- The `AuthenticationManager` bean must be explicitly exposed as a `@Bean` to use in AuthController
- If every endpoint returns 403 before you've written any logic, your SecurityConfig is blocking all requests — check your `permitAll()` paths

---

## product-service

**What it needs to become:** A clean CRUD service demonstrating REST best practices and JPA.

### Optimistic Locking
Add `@Version private Long version` to the `Product` entity. JPA will include this in UPDATE statements. If two requests try to update the same product simultaneously, the second one gets an `OptimisticLockingFailureException` — catch this and return a 409.

### Pagination
`JpaRepository` supports `Pageable` out of the box. Accept `Pageable` in the controller method (Spring MVC auto-populates it from `?page=0&size=10&sort=name,asc` query params). Return `Page<ProductResponse>` — the `Page` wrapper includes `totalElements`, `totalPages`, etc.

### Richardson Maturity
The REST endpoints should use resources + HTTP verbs (Level 2). Paths should be plural nouns (`/products`, not `/product`). No action words in URLs (`/products/{id}` not `/products/getById/{id}`).

### Location Header on POST
When creating a resource, return `201 Created` and set the `Location` header to the URL of the new resource. `ResponseEntity.created(URI).body(response)` does this cleanly.

**Common gotcha:** `BigDecimal` for price — never use `double` or `float` for money. `@Column(precision = 10, scale = 2)` to set DB column precision.

---

## order-service

**What it needs to become:** The most complex service. It ties together Feign clients, a circuit breaker, Kafka publishing, and its own database.

### Feign Client
`@FeignClient(name = "product-service")` — the `name` must exactly match `spring.application.name` in product-service's application.yml. The interface method signatures mirror the target service's controller endpoints. `@EnableFeignClients` on the main class activates them.

When Feign gets a non-2xx response, it throws a `FeignException`. To map specific HTTP status codes (like 404 from product-service) to your own exceptions, implement `ErrorDecoder` and register it as a `@Bean`.

### Circuit Breaker
`@CircuitBreaker(name = "payment-service", fallbackMethod = "yourFallback")` wraps the method that calls PaymentClient. The fallback method must have the same return type and same parameters plus a `Throwable` as the last argument.

The circuit breaker name `"payment-service"` links to the configuration block in application.yml under `resilience4j.circuitbreaker.instances.payment-service`.

Three states to understand: **CLOSED** (normal, requests pass through), **OPEN** (failing fast, fallback runs immediately), **HALF_OPEN** (testing recovery with limited traffic).

### Kafka Producer
`KafkaTemplate<String, YourEvent>` is the main class. Inject it, call `kafkaTemplate.send(topicName, key, event)`. The topic name is just a string — `"order-placed"` works fine.

The `OrderPlacedEvent` is a plain Java record. Spring Kafka's `JsonSerializer` handles the conversion automatically.

### Outbox Pattern
The problem it solves: if you save an order to the DB and then the JVM crashes before publishing to Kafka, the event is lost. The outbox pattern avoids this by writing the event to an `outbox_events` DB table in the same `@Transactional` block as the order save. A separate scheduled job (`@Scheduled`) then reads unpublished events from the DB and publishes them to Kafka.

**Common gotchas:**
- `@EnableFeignClients` must be on the main class or a config class
- Feign and Resilience4j work together: add `feign.circuitbreaker.enabled: true` in application.yml
- If circuit breaker isn't tripping, check `minimum-number-of-calls` — it won't open until that many calls have been evaluated

---

## payment-service

**What it needs to become:** A Kafka consumer that processes payments and publishes results.

### Kafka Consumer
`@KafkaListener(topics = "order-placed", groupId = "payment-service-group")` on a method in a `@Component`. The method parameter type should match the event class — Spring Kafka deserializes it automatically via `JsonDeserializer`.

For at-least-once delivery, set `enable-auto-commit: false` and `ack-mode: MANUAL_IMMEDIATE` in application.yml, then accept `Acknowledgment ack` as a parameter in the listener method. Call `ack.acknowledge()` only after successfully processing.

### Dead Letter Topic (DLT)
When a message fails after max retries, it should go to a DLT. Configure a `DefaultErrorHandler` bean with a `DeadLetterPublishingRecoverer`. The DLT topic name is automatically `original-topic-name.DLT`.

### Idempotency
The same `OrderPlacedEvent` might be delivered more than once (Kafka at-least-once guarantee). Before processing, check if a payment for that `orderId` already exists in the DB. If yes, skip processing and acknowledge the message.

### Circuit Breaker
The payment-service simulates calling an external bank gateway. Create a method `callBankGateway(PaymentRequest)` and annotate it with `@CircuitBreaker(name = "bank-gateway", fallbackMethod = "bankFallback")`. The config is already in application.yml under `resilience4j.circuitbreaker.instances.bank-gateway`.

**Common gotchas:**
- `spring.json.trusted.packages` in application.yml must include the package where your event classes live
- Consumer group ID must be consistent — if you change it, Kafka treats it as a new consumer and starts from the beginning

---

## notification-service

**What it needs to become:** A pure event consumer with structured, observable logging.

### Multiple Kafka Listeners
You can have multiple `@KafkaListener` methods in the same class or in separate `@Component` classes. Each listens to a different topic.

### Structured Logging
`logstash-logback-encoder` is already in pom.xml. To activate it, create `src/main/resources/logback-spring.xml`. The encoder class is `net.logstash.logback.encoder.LogstashEncoder`. Configure an appender with this encoder targeting stdout.

Every log line becomes a JSON object with automatic fields: `timestamp`, `level`, `logger`, `message`. You add custom fields via MDC.

### MDC for Correlation
`MDC.put("correlationId", someId)` adds a field to every log line produced on the current thread. Do this at the start of a Kafka listener method, use the orderId or a correlation ID from the event as the value, and call `MDC.clear()` at the end.

### Micrometer Tracing
The `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies are already in pom.xml. Set `management.zipkin.tracing.endpoint: http://localhost:9411/api/v2/spans` in application.yml. Traces will automatically appear in Zipkin at `http://localhost:9411` after the service handles events.

**Common gotcha:** JSON logging only activates if `logback-spring.xml` exists with the right configuration. Without it, logs remain plain text regardless of the dependency being present.

---

## docker-compose.yml

**What it needs to become:** A file that starts the full infrastructure stack and all 7 services in containers.

### Dockerfiles
Each service needs a `Dockerfile`. Use a multi-stage build:
- Stage 1 (`builder`): use `eclipse-temurin:17-jdk-alpine`, run `mvn package`
- Stage 2 (`runtime`): use `eclipse-temurin:17-jre-alpine`, copy the jar from stage 1, set `ENTRYPOINT`

Key JVM flags for containers: `-XX:+UseContainerSupport` (respects container memory limit), `-XX:MaxRAMPercentage=75.0` (uses 75% of container memory for heap).

### Service Configuration in Docker Network
Inside Docker Compose, services talk to each other by service name, not `localhost`. So `EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE` should be `http://eureka-server:8761/eureka/`, not `http://localhost:8761/eureka/`. The commented-out service blocks in docker-compose.yml already have this.

### Startup Order
Kafka and Eureka must be healthy before the microservices start. Use `depends_on` with `condition: service_healthy` and add `healthcheck` blocks to your Kafka and Eureka services.

### prometheus.yml
Prometheus needs a scrape config to know where to pull metrics from. Create `prometheus.yml` at the project root with a `scrape_configs` section listing each service's `/actuator/prometheus` endpoint.

---

## k8s/

**What it needs to become:** Kubernetes manifests that deploy all services to a cluster.

### Core Resources
Each service needs a `Deployment` (manages pod replicas and rolling updates) and a `Service` (stable network endpoint). These are already stubbed.

### Probes
- **Liveness probe:** `GET /actuator/health/liveness` — Kubernetes restarts the pod if this fails
- **Readiness probe:** `GET /actuator/health/readiness` — Kubernetes stops routing traffic to the pod if this fails (pod stays up but removed from load balancer)

Spring Boot Actuator exposes both by default when `management.endpoint.health.probes.enabled: true` is set.

### Resource Requests and Limits
`requests` tells Kubernetes how much CPU/memory to reserve for scheduling. `limits` is the hard cap — exceeding memory limit causes OOMKill. HPA cannot work without `requests` being set.

### ConfigMaps and Secrets
Environment-specific config (Eureka URL, Kafka URL) goes in a `ConfigMap`. Sensitive values (JWT secret, DB passwords) go in a `Secret`. Reference them in the Deployment using `valueFrom.configMapKeyRef` and `valueFrom.secretKeyRef`.

### HPA (Horizontal Pod Autoscaler)
The `order-service.yml` stub has a commented-out HPA block. It requires `metrics-server` installed in the cluster and resource `requests` set on the container. Once configured, Kubernetes automatically scales the pod count based on CPU usage.

---

## .github/workflows/ci-cd.yml

**What it needs to become:** A pipeline that builds, tests, containerises, and deploys all services automatically on push.

The file is already complete and functional. What you need to do:

1. Add these secrets to your GitHub repository (Settings → Secrets):
   - `DOCKER_USERNAME` — your Docker Hub username
   - `DOCKER_PASSWORD` — your Docker Hub access token
   - `KUBECONFIG` — base64-encoded kubeconfig file for your cluster

2. Create a `Dockerfile` in each service directory (the pipeline references them at `./service-name/Dockerfile`)

3. For the deploy step to work, your cluster must be reachable from GitHub Actions (use a cloud cluster or expose it via a tool like `ngrok` for testing)

**Common gotcha:** The pipeline uses a matrix strategy — it runs build/test/docker jobs in parallel for all 7 services. If one service's build fails, that matrix job fails but others continue. Check the individual job logs, not just the top-level result.

---

## General Troubleshooting

**Service starts but doesn't appear in Eureka**
- Check `spring.application.name` is set in application.yml
- Check `eureka.client.service-url.defaultZone` points to the right host/port
- `@EnableDiscoveryClient` must be on the main class

**Feign call returns 404 or connection refused**
- Confirm the target service is running and registered in Eureka
- Confirm `@FeignClient(name = "...")` matches the target's `spring.application.name` exactly

**Circuit breaker never opens**
- Check `minimum-number-of-calls` — the CB won't evaluate until this many calls have happened
- Check `feign.circuitbreaker.enabled: true` is in application.yml
- Hit the endpoint enough times to trigger the threshold

**Kafka consumer not receiving messages**
- Confirm the topic name in `@KafkaListener` exactly matches the producer's topic name
- Confirm `spring.json.trusted.packages` includes the event class's package
- Check the consumer group — if the group already consumed those messages at an earlier offset, use `auto-offset-reset: earliest` and reset the offset

**JWT always returns 401 from gateway**
- Confirm the secret in api-gateway's application.yml matches the one in user-service
- The token format must be `Bearer <token>` in the Authorization header
- Check token expiry — the default in user-service is 24 hours
