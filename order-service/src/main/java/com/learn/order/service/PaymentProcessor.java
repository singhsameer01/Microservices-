package com.learn.order.service;

import com.learn.order.client.PaymentClient;
import com.learn.order.dto.PaymentRequest;
import com.learn.order.dto.PaymentResponse;
import com.learn.order.model.Order;
import com.learn.order.model.OrderStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Isolated Spring bean that wraps the payment-service Feign call with a
 * Resilience4j circuit breaker.
 *
 * <p>This must be a separate bean from {@link OrderService} so that Spring AOP
 * can intercept the {@link CircuitBreaker} annotation. Calling an
 * {@code @CircuitBreaker}-annotated method via {@code this.method()} inside the
 * same bean bypasses the AOP proxy and silently disables the circuit breaker.</p>
 */
@Service
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final PaymentClient paymentClient;

    public PaymentProcessor(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    /**
     * Calls payment-service to process payment for a saved order.
     * Protected by a Resilience4j circuit breaker (name="payment-service").
     *
     * <p>If payment-service is unavailable or the circuit is OPEN, the fallback
     * {@link #paymentFallback} is invoked and the order is marked CANCELLED.</p>
     *
     * @param order the saved order to pay for
     */
    @CircuitBreaker(name = "payment-service", fallbackMethod = "paymentFallback")
    public void processPayment(Order order) {
        PaymentResponse response = paymentClient.processPayment(
                new PaymentRequest(order.getId(), order.getCustomerId(), order.getTotalAmount())
        );
        log.info("Payment completed for orderId={} status={}", order.getId(), response.status());
        order.setStatus(OrderStatus.CONFIRMED);
    }

    /**
     * Fallback invoked when the payment-service circuit is OPEN or the Feign call fails.
     * Marks the order CANCELLED so it is never left in a permanent PENDING state.
     *
     * @param order     the order that could not be paid
     * @param throwable the exception that triggered the fallback
     */
    public void paymentFallback(Order order, Throwable throwable) {
        log.warn("Payment fallback triggered for orderId={}: {}", order.getId(), throwable.getMessage());
        order.setStatus(OrderStatus.CANCELLED);
    }
}
