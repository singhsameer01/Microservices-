# Eureka Server — Service Registry

The Eureka Server is the central **service registry** for the entire microservices platform. Every other service registers itself here on startup, and services discover each other by logical name instead of hard-coded IP addresses.

- **Port:** `8761`
- **Dashboard:** [http://localhost:8761](http://localhost:8761)

---

## What It Does

When a service starts, it sends a registration request to Eureka containing its host, port, health-check URL, and metadata. Eureka stores that in-memory and replicates to peers (none here — single-node setup).

Clients poll Eureka every 30 seconds to refresh their local registry copy. When `api-gateway` resolves `lb://user-service`, Spring Cloud LoadBalancer reads the local copy and picks an instance via round-robin.

### Heartbeats and Self-Preservation

Every registered service sends a heartbeat every 30 seconds. If Eureka stops receiving heartbeats for more than 90 seconds it evicts that instance.

If more than 85% of heartbeats go missing at once (network partition scenario), Eureka enters **self-preservation mode** and stops evicting — it assumes the network is degraded, not the services. This prevents wiping out all instances during a transient outage.

`eureka.server.wait-time-in-ms-when-sync-empty: 0` skips the 5-minute peer-sync wait on startup, so the registry is immediately usable in a single-node setup.

---

## Configuration (`application.yml`)

```yaml
server:
  port: 8761

eureka:
  instance:
    hostname: localhost
  client:
    register-with-eureka: false   # this node does not register itself
    fetch-registry: false          # no peers to fetch from
    service-url:
      defaultZone: http://localhost:8761/eureka/
  server:
    wait-time-in-ms-when-sync-empty: 0
```

`register-with-eureka: false` and `fetch-registry: false` are required — without them, the server tries to register itself and enters an infinite loop.

---

## Actuator Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /` | Eureka dashboard (HTML) |
| `GET /eureka/apps` | All registered services (XML/JSON) |
| `GET /eureka/apps/{appName}` | Instances of a specific service |
| `GET /actuator/health` | Server health |
| `GET /actuator/info` | Build info |

---

## Running

```bash
# Start this FIRST, before any other service
mvn -f eureka-server/pom.xml spring-boot:run
```

---

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `spring-cloud-starter-netflix-eureka-server` | Embeds the Eureka server |
| `spring-boot-starter-actuator` | Health and info endpoints |
| `logstash-logback-encoder` | JSON-structured logs |

---

## Role in the Platform

```
All Services (on startup)
    │  POST /eureka/apps/<APP_NAME>   ← register
    │  PUT  /eureka/apps/<APP_NAME>/<INSTANCE_ID>  ← heartbeat every 30s
    ▼
Eureka Server :8761
    ▲
    │  lb://user-service → resolve instance list
    │  lb://product-service → resolve instance list
    │
API Gateway :8080  (Spring Cloud LoadBalancer reads local registry cache)
Order Service :8083 (Feign clients resolve @FeignClient(name=...) via Eureka)
```
