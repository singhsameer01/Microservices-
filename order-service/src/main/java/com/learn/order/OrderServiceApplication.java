package com.learn.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Entry point for order-service.
 *
 * <p>{@code @EnableDiscoveryClient} registers this service with Eureka so that
 * other services can discover it, and so that Feign can resolve {@code lb://} URIs.</p>
 *
 * <p>{@code @EnableFeignClients} scans for {@code @FeignClient} interfaces in this
 * package and creates proxy beans for them at startup.</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
