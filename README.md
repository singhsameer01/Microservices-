# Microservices E-Commerce — Learning Project

This is a self-learning project. Every configuration file has been intentionally left blank. Your job is to fill each one in yourself, guided by the explanations in this file and in each service's own README.

## Architecture

```
Client → API Gateway (8080)
           ├── user-service    (8081)
           ├── product-service (8082)
           ├── order-service   (8083)
           └── payment-service (8084)

Eureka Server (8761) ← all services register here

order-service ──Feign──→ product-service  (check stock)
order-service ──Feign──→ payment-service  (initiate payment)

order-service   ──Kafka──→ notification-service (8085)
payment-service ──Kafka──→ notification-service

All services → Zipkin (9411)   distributed tracing
All services → Prometheus (9090) / Grafana (3000)   metrics
```

## Service Ports

| Service              | Port |
|----------------------|------|
| Eureka Server        | 8761 |
| API Gateway          | 8080 |
| user-service         | 8081 |
| product-service      | 8082 |
| order-service        | 8083 |
| payment-service      | 8084 |
| notification-service | 8085 |
| Kafka                | 9092 |
| Zipkin               | 9411 |
| Prometheus           | 9090 |
| Grafana              | 3000 |

## Where to Start

Read each service's README.md for its specific configuration. The order below matches the order you should build the services:

1. `eureka-server/README.md` — service discovery
2. `api-gateway/README.md` — routing and authentication
3. `user-service/README.md` — users, JWT, database
4. `product-service/README.md` — product catalog
5. `order-service/README.md` — Kafka, circuit breakers, Feign
6. `payment-service/README.md` — Kafka consumer/producer
7. `notification-service/README.md` — pure Kafka consumer

---

## Docker Compose Configuration to Write (`docker-compose.yml`)

> **What is Docker Compose?**
> Docker Compose is a tool that lets you run multiple Docker containers with a single command (`docker-compose up`). Instead of starting Kafka, Zookeeper, Zipkin, Prometheus, and Grafana manually, you describe them all in one `docker-compose.yml` file and start them together.

Write your `docker-compose.yml` in the project root. Here is every service block explained.

---

### The File Version

**What is it?**
The version of the Docker Compose file format. `3.8` is a modern, widely-supported version.

```yaml
version: '3.8'

services:
  # ... your service blocks go here
```

---

### Zookeeper

**What is it?**
Zookeeper is a coordination service that Kafka depends on. Think of it as Kafka's manager — it keeps track of which Kafka brokers are alive, who is the leader for each partition, etc.

You don't interact with Zookeeper directly; it just needs to be running for Kafka to work.

- **image** — the Docker image to use. `confluentinc/cp-zookeeper:7.5.0` is a specific tested version.
- **container_name** — a friendly name so you can refer to it in other containers
- **environment** — settings passed to Zookeeper inside the container:
  - `ZOOKEEPER_CLIENT_PORT: 2181` — the port Kafka will connect to Zookeeper on
  - `ZOOKEEPER_TICK_TIME: 2000` — heartbeat interval in milliseconds (2 seconds)
- **ports** — maps port on your machine to port inside the container: `"host:container"`

```yaml
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"
```

> **Learn:** What is Zookeeper? Why is it needed for Kafka? (Note: newer Kafka versions use KRaft and don't need Zookeeper — learn about both)

---

### Kafka

**What is it?**
The message broker. Services publish events to Kafka topics; other services subscribe to receive them. In this project, order-service publishes to `order-placed` and payment-service reads it.

- **depends_on: zookeeper** — Docker Compose starts Zookeeper first
- **KAFKA_BROKER_ID: 1** — each Kafka broker has a unique ID. You have one broker, so it's `1`.
- **KAFKA_ZOOKEEPER_CONNECT** — where Kafka finds Zookeeper. `zookeeper:2181` — `zookeeper` is the container name defined above; Docker Compose's internal network lets containers find each other by name.
- **KAFKA_ADVERTISED_LISTENERS** — the address Kafka tells clients to connect to. When running locally, this is `localhost:9092`.
- **KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1** — how many copies of the internal offsets topic to keep. With only 1 broker, this must be 1.
- **KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"** — topics are created automatically when a producer first publishes to them. In production, you'd create topics explicitly.

```yaml
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
```

---

### Zipkin

**What is it?**
A distributed tracing system. When a request flows through multiple services (Gateway → order-service → payment-service), Zipkin stitches all those individual steps into a single timeline so you can see exactly where time was spent and where errors occurred.

Access the Zipkin UI at `http://localhost:9411` after starting.

```yaml
  zipkin:
    image: openzipkin/zipkin:latest
    container_name: zipkin
    ports:
      - "9411:9411"
```

> **Learn:** What is a trace? What is a span? How does Zipkin collect trace data from multiple services? (hint: your services send HTTP requests to Zipkin's `/api/v2/spans` endpoint)

---

### Prometheus

**What is it?**
Prometheus is a metrics collection tool. It periodically scrapes (polls) the `/actuator/prometheus` endpoint of each service and stores the numbers in a time-series database. You can then query "how many requests per second did order-service handle in the last 5 minutes?"

- **volumes** — mounts a local file into the container. `./prometheus.yml:/etc/prometheus/prometheus.yml` means "take the `prometheus.yml` file from your project root and put it at `/etc/prometheus/prometheus.yml` inside the container". You still need to create that file.

```yaml
  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
```

> **Learn:** What does `prometheus.yml` need to contain? (hint: `scrape_configs` with a target for each service's `/actuator/prometheus` endpoint). Create `prometheus.yml` at the project root.

---

### Grafana

**What is it?**
Grafana is a dashboard tool that connects to Prometheus and displays your metrics as graphs and charts. You can build dashboards showing request rates, error rates, JVM memory, and more.

- **depends_on: prometheus** — Grafana needs Prometheus running first
- **GF_SECURITY_ADMIN_PASSWORD** — the password for the Grafana admin user. Username is always `admin`.

Access Grafana at `http://localhost:3000` (login: admin / admin).

```yaml
  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    ports:
      - "3000:3000"
    depends_on:
      - prometheus
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
```

> **Learn:** After starting Grafana, add Prometheus as a data source (URL: `http://prometheus:9090`). Then import the Spring Boot dashboard (ID: 12900 from grafana.com/dashboards).

---

### Your Microservices (add after creating Dockerfiles)

Each of your services also needs a block in `docker-compose.yml`. The pattern is the same for all of them. Here is an example for eureka-server:

- **build: ./eureka-server** — build the Docker image from the `Dockerfile` in the `eureka-server` folder
- **environment** — override application properties using environment variables. Spring Boot translates `EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE` into `eureka.client.service-url.defaultZone` automatically.

```yaml
  eureka-server:
    build: ./eureka-server
    container_name: eureka-server
    ports:
      - "8761:8761"

  api-gateway:
    build: ./api-gateway
    container_name: api-gateway
    ports:
      - "8080:8080"
    depends_on:
      - eureka-server
    environment:
      EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE: http://eureka-server:8761/eureka/

  # ... repeat for user-service, product-service, order-service, payment-service, notification-service
  # order-service and payment-service also need: depends_on: kafka
```

> **Learn:** What is a `Dockerfile`? What does a multi-stage Docker build look like for a Spring Boot app? (hint: stage 1 = build with maven, stage 2 = run with just the JRE)

---

## Kubernetes Configuration to Write (`k8s/*.yml`)

> **What is Kubernetes?**
> Kubernetes (K8s) is a system for running containers in production. While Docker Compose runs containers on your laptop, Kubernetes runs them across a cluster of machines, handles restarts, scales up/down, and manages networking between containers.

Each service has a file in the `k8s/` folder. Write the manifest for each service there. The structure is the same for all of them — here is a complete example and explanation using eureka-server.

---

### The Two Objects Every Service Needs

Every service in Kubernetes needs two things:
1. A **Deployment** — tells Kubernetes "run this container, keep N copies running, restart if it crashes"
2. A **Service** — gives the container a stable network address inside the cluster

These two objects are written in the same file, separated by `---`.

---

### Deployment

**What is it?**
A Deployment manages a set of identical containers (called Pods). You tell it what image to run, how many copies, and what environment variables to set.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: eureka-server          # name of this Deployment resource
  labels:
    app: eureka-server         # label used to find this deployment
spec:
  replicas: 1                  # how many copies to run
  selector:
    matchLabels:
      app: eureka-server       # which pods this deployment manages (must match template labels)
  strategy:
    type: RollingUpdate        # update pods one at a time (zero downtime)
    rollingUpdate:
      maxUnavailable: 0        # never kill an old pod until new one is ready
      maxSurge: 1              # allow 1 extra pod during the update
  template:                    # the pod blueprint
    metadata:
      labels:
        app: eureka-server     # must match selector above
    spec:
      containers:
        - name: eureka-server
          image: your-dockerhub-username/eureka-server:latest  # replace with your image
          ports:
            - containerPort: 8761
          # env:               # set env vars to override application.yml
          # livenessProbe:     # Kubernetes checks this URL; restarts pod if it fails
          # readinessProbe:    # Kubernetes only sends traffic once this URL returns 200
          # resources:         # CPU and memory limits (required for autoscaling)
```

Key fields to learn:
- **replicas** — how many identical pods to run
- **RollingUpdate** — the default update strategy; replaces pods one at a time with zero downtime
- **livenessProbe** — Kubernetes calls `/actuator/health/liveness` periodically; if it fails, the pod is restarted
- **readinessProbe** — Kubernetes calls `/actuator/health/readiness`; pod receives no traffic until this passes
- **resources.requests** — the minimum CPU/memory Kubernetes reserves for this pod
- **resources.limits** — the maximum CPU/memory this pod can use before being throttled

---

### Service (Kubernetes networking)

**What is it?**
A Kubernetes Service gives your pods a stable name and IP address inside the cluster. Without it, pods have random IPs that change every time they restart.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: eureka-server
spec:
  selector:
    app: eureka-server         # routes traffic to pods with this label
  ports:
    - protocol: TCP
      port: 8761               # the port this Service listens on
      targetPort: 8761         # the port the pod actually listens on
  type: ClusterIP              # only reachable inside the cluster
```

**Service types:**
- **ClusterIP** — internal only. Use for services that only other services talk to (user-service, product-service, order-service, payment-service, notification-service, eureka-server)
- **NodePort** — exposes the service on a port on every node. Use for the api-gateway during development.
- **LoadBalancer** — creates a cloud load balancer (AWS ELB, GCP GLB). Use for the api-gateway in production.

---

### Environment Variables

When your service runs in Kubernetes, it can't use `localhost:8761` to find Eureka because each pod has its own `localhost`. Instead, use the Kubernetes Service name:

```yaml
env:
  - name: EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE
    value: http://eureka-server:8761/eureka/
    # "eureka-server" is the name of the Kubernetes Service, not localhost
  - name: SPRING_KAFKA_BOOTSTRAP_SERVERS
    value: kafka:9092
    # (for services that use Kafka)
```

> **Learn:** How does Kubernetes DNS work? When a pod says `http://eureka-server:8761`, how does it resolve to the right IP?

---

### Horizontal Pod Autoscaler (HPA) for order-service

**What is it?**
An HPA automatically scales the number of replicas up or down based on CPU usage. If order-service is under heavy load (CPU above 70%), Kubernetes adds more pods. When load drops, it removes them.

This requires `resources.requests.cpu` to be set in the Deployment — HPA can't calculate "70% of nothing".

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 1
  maxReplicas: 5
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

> **Learn:** What is a HorizontalPodAutoscaler? What is the difference between horizontal scaling (more copies) and vertical scaling (bigger machine)? Why is horizontal scaling preferred for microservices?

---

## CI/CD Pipeline to Write (`.github/workflows/ci-cd.yml`)

> **What is CI/CD?**
> CI (Continuous Integration) means every time you push code, it is automatically built and tested. CD (Continuous Deployment) means if the build passes, the new version is automatically deployed. GitHub Actions is the tool that runs this pipeline.

Write your pipeline in `.github/workflows/ci-cd.yml`. Here is every section explained.

---

### Triggers — When Does the Pipeline Run?

```yaml
on:
  push:
    branches: [ main, develop ]   # run when you push to main or develop
  pull_request:
    branches: [ main ]            # run when someone opens a PR targeting main
```

---

### Secrets — Credentials Stored Safely in GitHub

Never put passwords or tokens in your code. GitHub lets you store them as encrypted Secrets and reference them as `${{ secrets.NAME }}`.

You need to add these secrets in GitHub → Settings → Secrets:
- `DOCKER_USERNAME` — your Docker Hub username
- `DOCKER_PASSWORD` — your Docker Hub password (or access token)
- `KUBECONFIG` — the contents of your Kubernetes cluster config file

```yaml
env:
  DOCKER_USERNAME: ${{ secrets.DOCKER_USERNAME }}
  DOCKER_PASSWORD: ${{ secrets.DOCKER_PASSWORD }}
```

---

### Job 1 — Build and Test

**What is it?**
For each of the 7 services, check out the code, set up Java 17, build it with Maven, and run the tests. The `matrix` strategy runs all 7 in parallel.

```yaml
jobs:
  build:
    name: Build & Test
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [eureka-server, api-gateway, user-service, product-service,
                  order-service, payment-service, notification-service]
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Build ${{ matrix.service }}
        run: mvn -f ${{ matrix.service }}/pom.xml clean package -DskipTests

      - name: Test ${{ matrix.service }}
        run: mvn -f ${{ matrix.service }}/pom.xml test
```

> **Learn:** What is a **build matrix** in GitHub Actions? What is `actions/checkout`? What does `cache: maven` do and why does it speed up builds?

---

### Job 2 — Build Docker Images and Push to Docker Hub

**What is it?**
Only runs when code is pushed to `main` (not on pull requests). For each service, build a Docker image and push it to Docker Hub with a tag matching the Git commit hash (`${{ github.sha }}`).

Using the commit hash as the image tag means every push creates a uniquely identified image — you can always roll back to any previous version.

```yaml
  docker-build:
    name: Docker Build & Push
    runs-on: ubuntu-latest
    needs: build                              # only runs if the build job succeeded
    if: github.ref == 'refs/heads/main'       # only on pushes to main
    strategy:
      matrix:
        service: [eureka-server, api-gateway, user-service, product-service,
                  order-service, payment-service, notification-service]
    steps:
      - uses: actions/checkout@v3

      - name: Login to Docker Hub
        uses: docker/login-action@v2
        with:
          username: ${{ env.DOCKER_USERNAME }}
          password: ${{ env.DOCKER_PASSWORD }}

      - name: Build and push ${{ matrix.service }}
        uses: docker/build-push-action@v4
        with:
          context: ./${{ matrix.service }}
          push: true
          tags: ${{ env.DOCKER_USERNAME }}/${{ matrix.service }}:${{ github.sha }}
```

> **Learn:** What is Docker Hub? What is `${{ github.sha }}`? Why use the commit hash as an image tag instead of `latest`?

---

### Job 3 — Deploy to Kubernetes

**What is it?**
Only runs after Docker images are pushed successfully. Uses `kubectl apply` to tell Kubernetes to update all services to the new image. The `KUBECONFIG` secret holds the credentials your pipeline needs to connect to the cluster.

```yaml
  deploy:
    name: Deploy to Kubernetes
    runs-on: ubuntu-latest
    needs: docker-build
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v3

      - name: Set up kubectl
        uses: azure/setup-kubectl@v3

      - name: Configure kubeconfig
        run: echo "${{ secrets.KUBECONFIG }}" > kubeconfig.yml

      - name: Deploy all services
        run: kubectl apply -f k8s/ --kubeconfig=kubeconfig.yml
```

> **Learn:** What is `kubectl apply`? What is a `kubeconfig` file? What is the difference between `kubectl apply` and `kubectl create`?

---

## Topics to Learn (Infrastructure)

### Docker and Docker Compose
- What is a **Docker image** vs a **Docker container**?
- What is a **Dockerfile** and what does a multi-stage build look like?
- What does `docker-compose up -d` do? What does `-d` mean?
- What is a Docker **volume** and why is the prometheus.yml mounted as one?
- What is a Docker **network** and how do containers find each other by name?

### Kubernetes
- What is the difference between a **Pod**, a **Deployment**, and a **Service**?
- What is a **liveness probe** vs a **readiness probe**?
- What is a **ConfigMap** and a **Secret** in Kubernetes?
- What is a **Namespace** in Kubernetes?
- What is the difference between **ClusterIP**, **NodePort**, and **LoadBalancer** service types?
- What is a **HorizontalPodAutoscaler** and when does it add/remove pods?

### CI/CD
- What is the difference between **Continuous Integration** and **Continuous Deployment**?
- What is a **GitHub Actions workflow** and what triggers it?
- What is a **build matrix** and why does it run jobs in parallel?
- Why should you never use `latest` as a Docker image tag in production?
- What is a **rolling deployment** vs a **blue-green deployment**?
