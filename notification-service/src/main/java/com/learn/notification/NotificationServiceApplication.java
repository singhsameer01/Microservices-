package com.learn.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Entry point for notification-service.
 *
 * <p>This service has no REST API — it is a pure Kafka consumer.
 * It subscribes to "order-placed" and "payment-processed" topics and
 * dispatches notifications (currently via structured logging) to customers.</p>
 *
 * <p>{@code @EnableDiscoveryClient} registers the service with Eureka so that
 * it appears on the dashboard and can participate in health-check monitoring.</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
