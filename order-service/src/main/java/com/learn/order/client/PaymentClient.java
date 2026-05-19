package com.learn.order.client;

import com.learn.order.dto.PaymentRequest;
import com.learn.order.dto.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for payment-service.
 *
 * <p>The {@code name} attribute must exactly match {@code spring.application.name}
 * in payment-service's application.yml ("payment-service").</p>
 *
 * <p>Calls to this client are wrapped by a Resilience4j circuit breaker
 * (name="payment-service") configured in application.yml. When the circuit is OPEN,
 * the fallback method in {@code OrderService} is invoked immediately.</p>
 */
@FeignClient(name = "payment-service")
public interface PaymentClient {

    /**
     * Initiates a payment for an order via payment-service.
     *
     * @param request payment details (orderId, customerId, amount)
     * @return the processed payment result
     */
    @PostMapping("/api/v1/payments")
    PaymentResponse processPayment(@RequestBody PaymentRequest request);
}
