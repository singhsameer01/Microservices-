# eureka-server

**Port:** 8761

## Purpose
Central service registry for the entire system. All microservices register here and discover each other by name.

## What Needs to Be Here

### Configuration
- This app should act as a Eureka Server
- It should NOT register itself with the registry
- It should NOT fetch the registry from any peer

### Behavior
- When running, the Eureka dashboard should be accessible
- Other services starting up should automatically appear in the registry
- Instances that stop sending heartbeats should eventually be evicted
- Self-preservation mode should prevent mass eviction during network issues

---

## Configuration to Write (`application.yml`)

> **How to use this section:** Every setting you need in `src/main/resources/application.yml` is explained below in plain English. Try to write each one yourself before looking at the answer — that is how you will actually learn it. The full working answer is at the bottom in a collapsible block.

---

### 1. Server Port

**What is it?**
The "door number" your app opens on your computer. When you type `http://localhost:8761` in a browser, the `8761` is the port.

**Why this matters:** Every other service in this project will be hardcoded to look for Eureka at port `8761`, so this must match.

```yaml
server:
  port: ???
```

---

### 2. Application Name

**What is it?**
Your service's name tag. When this app starts up and writes logs, it labels itself with this name. Monitoring tools like Zipkin also use it to group traces.

```yaml
spring:
  application:
    name: ???
```

---

### 3. Eureka Instance Hostname

**What is it?**
The address Eureka advertises to the outside world. On your laptop, your machine is simply `localhost`.

```yaml
eureka:
  instance:
    hostname: ???
```

---

### 4. Don't Register With Itself

**What is it?**
Every Spring app that includes the Eureka dependency tries to register itself in the registry by default. But the Eureka *server* is the registry keeper — it should not add itself as a member.

**What happens if you skip this?** The server throws confusing errors on startup while trying to register with itself before it has fully booted.

```yaml
eureka:
  client:
    register-with-eureka: ???   # should this server register itself?
```

---

### 5. Don't Fetch the Registry

**What is it?**
Normally Eureka clients download a copy of the full service list from the server. The server itself already holds the master copy — it doesn't need to download it from anywhere.

```yaml
eureka:
  client:
    fetch-registry: ???
```

---

### 6. Default Zone URL

**What is it?**
The URL where Eureka lives. Even the server needs this as a self-reference. Notice the `${}` syntax — Spring Boot fills those in automatically from the values you defined earlier.

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/
```

> **Learn:** This is called **property interpolation**. Spring replaces `${eureka.instance.hostname}` with `localhost` and `${server.port}` with `8761` at startup. Try to understand why using placeholders is better than hardcoding the full URL.

---

### 7. Skip Empty Sync Wait

**What is it?**
When Eureka starts with no peer servers (you have just one, not a cluster), it normally waits a few seconds for peers to sync. Setting this to `0` makes startup instant.

```yaml
eureka:
  server:
    wait-time-in-ms-when-sync-empty: ???   # hint: 0
```

---

### 8. Expose Health and Info Endpoints

**What is it?**
Spring Boot Actuator is a built-in module that adds monitoring URLs to your app. You need to tell it which URLs to make publicly accessible over HTTP.

- `/actuator/health` — tells you if the app is running (Kubernetes uses this)
- `/actuator/info` — shows app metadata

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info
  endpoint:
    health:
      show-details: always
```

> **Learn:** What is Spring Boot Actuator? What other endpoints can you expose (try `metrics`, `prometheus`, `env`)? Why does Kubernetes need a health endpoint?

---

## Topics to Learn

- What is **Service Discovery** and why can't services just use hardcoded IP addresses?
- What is the difference between Eureka **server** and Eureka **client**?
- What is a **heartbeat** in distributed systems and what happens when one is missed?
- What is Eureka's **self-preservation mode** and when does it kick in?
- What is **Spring Boot Actuator** and what does it give you out of the box?

---

## Answer

<details>
<summary>Click to reveal the full application.yml (try first!)</summary>

```yaml
server:
  port: 8761

spring:
  application:
    name: eureka-server

eureka:
  instance:
    hostname: localhost
  client:
    register-with-eureka: false
    fetch-registry: false
    service-url:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/
  server:
    wait-time-in-ms-when-sync-empty: 0

management:
  endpoints:
    web:
      exposure:
        include: health, info
  endpoint:
    health:
      show-details: always
```

</details>
