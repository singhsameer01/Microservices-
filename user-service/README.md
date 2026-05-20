# User Service

The User Service handles **authentication and user management** for the entire platform. It is the only service that issues JWT tokens — all other services simply validate those tokens using the public key this service exposes.

- **Port:** `8081`
- **Swagger UI:** [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
- **H2 Console:** [http://localhost:8081/h2-console](http://localhost:8081/h2-console) (JDBC URL: `jdbc:h2:mem:userdb`)

---

## What It Does

### Authentication Flow

```
Client                          user-service
  │                                 │
  │  POST /api/v1/auth/register     │
  │ ──────────────────────────────► │  BCrypt-hash password, save User
  │ ◄────────────────────────────── │  201 UserResponse
  │                                 │
  │  POST /api/v1/auth/login        │
  │ ──────────────────────────────► │  AuthenticationManager.authenticate()
  │                                 │  generate RSA-signed JWT (15 min)
  │                                 │  generate refresh token UUID (7 days)
  │ ◄────────────────────────────── │  AuthResponse { accessToken, refreshToken }
  │                                 │
  │  POST /api/v1/auth/refresh      │
  │ ──────────────────────────────► │  validate refresh token, revoke old
  │ ◄────────────────────────────── │  new accessToken + new refreshToken (rotation)
  │                                 │
  │  POST /api/v1/auth/logout       │
  │ ──────────────────────────────► │  revoke refresh token
  │ ◄────────────────────────────── │  200 OK  (access token expires naturally)
```

### Token Architecture

Tokens are **RSA-2048 signed** (RS256). The service generates a new key pair **in memory** on every startup — meaning all tokens are invalidated on restart in development.

- The private key signs new JWTs (`JwtEncoder` + NimbusJWT)
- The public key is exposed at `GET /.well-known/jwks.json` as a JWKS document
- All other services (`api-gateway`, `product-service`, `order-service`, `payment-service`) configure `jwk-set-uri` pointing to this endpoint and validate tokens locally — zero round-trips to user-service per request

JWT claims:
```json
{
  "iss": "user-service",
  "sub": "<username>",
  "role": "ROLE_USER",
  "iat": ...,
  "exp": ...   // now + 15 minutes
}
```

### Refresh Token Rotation

Refresh tokens are stored in the `refresh_tokens` table (UUID, 7-day expiry, revocation flag). On each `/refresh` call:
1. Old token is validated (not expired, not revoked)
2. All existing tokens for that user are revoked (single-session semantics)
3. A new access token + refresh token pair is issued

This means a stolen refresh token can only be used once — the next legitimate use invalidates it.

---

## REST API

### Auth Endpoints (`/api/v1/auth`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/register` | None | Create new user account |
| `POST` | `/login` | None | Authenticate, receive tokens |
| `POST` | `/refresh` | None | Rotate refresh token |
| `POST` | `/logout` | Bearer token | Revoke refresh token |

**Register request:**
```json
{ "username": "alice", "email": "alice@example.com", "password": "secret123" }
```

**Login response:**
```json
{
  "accessToken": "<JWT>",
  "refreshToken": "<UUID>",
  "username": "alice",
  "role": "ROLE_USER"
}
```

### User Endpoints (`/api/v1/users`) — requires JWT

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/{id}` | Get user by ID |
| `GET` | `/` | Paginated list of all users |

### JWKS Endpoint

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/.well-known/jwks.json` | RSA public key for token verification |

---

## Database Schema

### `users`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT | Auto-generated PK |
| `username` | VARCHAR | Unique |
| `email` | VARCHAR | Unique |
| `password` | VARCHAR | BCrypt hashed (strength 10) |
| `role` | VARCHAR | Default `ROLE_USER` |
| `created_at` | TIMESTAMP | Immutable, set on insert |

### `refresh_tokens`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT | Auto-generated PK |
| `token` | VARCHAR | UUID, unique |
| `user_id` | BIGINT | FK → users |
| `expires_at` | TIMESTAMP | 7 days from creation |
| `revoked` | BOOLEAN | Default false |

---

## Key Classes

| Class | Package | Role |
|-------|---------|------|
| `AuthController` | `controller` | Login, register, refresh, logout endpoints |
| `UserController` | `controller` | User lookup endpoints |
| `JwksController` | `controller` | Serves RSA public key as JWKS |
| `UserService` | `service` | User CRUD, password encoding |
| `RefreshTokenService` | `service` | Token lifecycle management |
| `RsaKeyConfig` | `config` | Generates RSA key pair, beans for JwtEncoder/JwtDecoder |
| `JwtUtil` | `config` | Builds and signs JWT claims |
| `SecurityConfig` | `security` | HTTP security rules, stateless sessions |
| `UserDetailsServiceImpl` | `security` | Loads user from DB for Spring Security |

---

## Configuration (`application.yml`)

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:h2:mem:userdb;DB_CLOSE_DELAY=-1
  jpa:
    hibernate:
      ddl-auto: create-drop

jwt:
  expiration: 900000              # 15 minutes in milliseconds
  refresh-token-expiration-days: 7
```

---

## Running

```bash
# Prerequisites: Eureka Server must be running
mvn -f user-service/pom.xml spring-boot:run
```

---

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-web` | REST controllers |
| `spring-boot-starter-data-jpa` + `h2` | User and token persistence |
| `spring-boot-starter-security` | Authentication, BCrypt |
| `spring-boot-starter-oauth2-resource-server` | RSA JWT encoding/decoding (Nimbus) |
| `spring-boot-starter-validation` | Request body validation (`@Valid`) |
| `springdoc-openapi-starter-webmvc-ui` | Swagger UI |
| `spring-cloud-starter-netflix-eureka-client` | Service registration |
| `micrometer-registry-prometheus` | Prometheus metrics |
