package com.learn.payment.service;

import com.learn.payment.dto.PaymentRequest;
import com.learn.payment.dto.PaymentResponse;
import com.learn.payment.event.PaymentProcessedEvent;
import com.learn.payment.exception.PaymentAlreadyExistsException;
import com.learn.payment.exception.PaymentNotFoundException;
import com.learn.payment.model.Payment;
import com.learn.payment.model.PaymentStatus;
import com.learn.payment.repository.PaymentRepository;
import com.learn.payment.messaging.PaymentEventPublisher;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Core payment processing service.
 *
 * <p>Guarantees idempotency: if a payment for the same orderId already exists,
 * {@link PaymentAlreadyExistsException} is thrown and the Kafka consumer will
 * acknowledge without re-processing.</p>
 *
 * <p>The simulated bank gateway call is wrapped in a Resilience4j circuit breaker
 * (name: "bank-gateway"). Configuration lives in application.yml under
 * {@code resilience4j.circuitbreaker.instances.bank-gateway}.</p>
 */
@Service
@Transactional(readOnly = true)
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    public PaymentService(PaymentRepository paymentRepository, PaymentEventPublisher paymentEventPublisher) {
        this.paymentRepository = paymentRepository;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    /**
     * Processes a payment for an order.
     * Calls the (simulated) bank gateway through a circuit breaker so that
     * repeated failures open the circuit and prevent thundering-herd on the gateway.
     *
     * @param request payment details (orderId, customerId, amount)
     * @return the saved payment record
     * @throws PaymentAlreadyExistsException if a payment for request.orderId() already exists
     */
    @Transactional
    public PaymentResponse process(PaymentRequest request) {
        if (paymentRepository.existsByOrderId(request.orderId())) {
            throw new PaymentAlreadyExistsException(request.orderId());
        }
        Payment payment = new Payment(request.orderId(), request.customerId(), request.amount());

        // Delegate to circuit-breaker-protected bank gateway call
        PaymentStatus status = callBankGateway(request);
        payment.setStatus(status);

        Payment savedPayment = paymentRepository.save(payment);
        paymentEventPublisher.publish(new PaymentProcessedEvent(
            savedPayment.getId(),
            savedPayment.getOrderId(),
            savedPayment.getStatus(),
            Instant.now()
        ));

        return toResponse(savedPayment);
    }

    /**
     * Simulates calling an external bank gateway.
     * Protected by a Resilience4j circuit breaker (name="bank-gateway").
     * When the circuit is OPEN (too many failures), falls back to {@link #bankGatewayFallback}.
     *
     * <p>In a real system, this would make an HTTP call to the bank's API.</p>
     *
     * @param request the payment request to forward
     * @return {@link PaymentStatus#SUCCESS} on success
     */
    @CircuitBreaker(name = "bank-gateway", fallbackMethod = "bankGatewayFallback")
    public PaymentStatus callBankGateway(PaymentRequest request) {
        log.info("Calling bank gateway for orderId={} amount={}", request.orderId(), request.amount());
        // Simulated successful bank response
        return PaymentStatus.SUCCESS;
    }

    /**
     * Fallback invoked when the bank-gateway circuit is OPEN or the call throws an exception.
     * Returns FAILED so the payment is persisted (not retried endlessly) and the event
     * carries the failure status to downstream services.
     *
     * @param request   the original payment request
     * @param throwable the exception that triggered the fallback
     * @return {@link PaymentStatus#FAILED}
     */
    public PaymentStatus bankGatewayFallback(PaymentRequest request, Throwable throwable) {
        log.warn("Bank gateway fallback triggered for orderId={}: {}", request.orderId(), throwable.getMessage());
        return PaymentStatus.FAILED;
    }

    public PaymentResponse findById(Long id) {
        return paymentRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    public PaymentResponse findByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(this::toResponse)
                .orElseThrow(() -> new PaymentNotFoundException("orderId", orderId));
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getCustomerId(),
                p.getAmount(), p.getStatus(), p.getTransactionId(), p.getCreatedAt());
    }
}
