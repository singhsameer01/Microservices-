# ── Stage 1: Build ────────────────────────────────────────────────────────────
# Use the full JDK image to compile and package the application.
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and pom.xml first so that dependency download is cached
# separately from source changes (Docker layer cache optimisation).
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Download dependencies (cached as a separate layer)
RUN ./mvnw dependency:go-offline -q

# Copy source code and build the fat JAR, skipping tests (run separately in CI)
COPY src src
RUN ./mvnw clean package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
# Use the smaller JRE image — no compiler or tools needed at runtime.
FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

# Copy only the packaged JAR from the builder stage
COPY --from=builder /build/target/*.jar app.jar

# JVM container flags:
#   UseContainerSupport — respects Docker/Kubernetes memory limits (not host RAM)
#   MaxRAMPercentage    — uses 75% of the container's memory limit for heap
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]

EXPOSE 8761
