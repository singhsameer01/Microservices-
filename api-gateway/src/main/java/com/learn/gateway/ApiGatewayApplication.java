package com.learn.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Entry point for api-gateway.
 *
 * <p>This service is the single entry point for all external traffic.
 * It uses Spring Cloud Gateway (WebFlux / reactive) to:</p>
 * <ul>
 *   <li>Route requests to downstream services using {@code lb://} URIs resolved via Eureka</li>
 *   <li>Validate JWT Bearer tokens on every request (via {@code JwtAuthenticationFilter})</li>
 *   <li>Skip auth for public paths like {@code /api/v1/auth/**}</li>
 * </ul>
 *
 * <p>{@code @EnableDiscoveryClient} enables Eureka-based load balancing so
 * {@code lb://product-service} etc. resolve to real instance addresses.</p>
 *
 * <p><strong>Important:</strong> do NOT add {@code spring-boot-starter-web} —
 * Spring Cloud Gateway is WebFlux-based and conflicts with the servlet stack.</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
