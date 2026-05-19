package com.learn.order.service;

import com.learn.order.client.PaymentClient;
import com.learn.order.dto.PaymentRequest;
import com.learn.order.dto.PaymentResponse;
import com.learn.order.model.Order;
import com.learn.order.model.OrderStatus;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Isolated Spring bean wrapping the payment-service Feign call with three
 * Resilience4j decorators (must be a separate bean for Spring AOP to intercept them):
 *
 *  Bulkhead — limits concurrent payment calls (semaphore, max 10)
 *  Retry    — retries transient Feign failures up to 3 times with exponential backoff
 *  CircuitBreaker — opens after 50% failure rate; fallback cancels the order
 *
 * Execution order (outermost → innermost): CircuitBreaker → Retry → Bulkhead → actual call
 */
@Service
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final PaymentClient paymentClient;

    public PaymentProcessor(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    @CircuitBreaker(name = "payment-service", fallbackMethod = "paymentFallback")
    @Retry(name = "payment-service", fallbackMethod = "paymentFallback")
    @Bulkhead(name = "payment-service", fallbackMethod = "paymentFallback")
    public void processPayment(Order order) {
        PaymentResponse response = paymentClient.processPayment(
                new PaymentRequest(order.getId(), order.getCustomerId(), order.getTotalAmount())
        );
        log.info("Payment completed for orderId={} status={}", order.getId(), response.status());
        order.setStatus(OrderStatus.CONFIRMED);
    }

    public void paymentFallback(Order order, Throwable throwable) {
        log.warn("Payment fallback triggered for orderId={}: {}", order.getId(), throwable.getMessage());
        order.setStatus(OrderStatus.CANCELLED);
    }
}
