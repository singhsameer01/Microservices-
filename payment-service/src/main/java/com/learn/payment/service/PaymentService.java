package com.learn.payment.service;

import com.learn.payment.dto.PaymentRequest;
import com.learn.payment.dto.PaymentResponse;
import com.learn.payment.exception.PaymentNotFoundException;
import com.learn.payment.model.Payment;
import com.learn.payment.model.PaymentStatus;
import com.learn.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public PaymentResponse process(PaymentRequest request) {
        if (paymentRepository.existsByOrderId(request.orderId())) {
            throw new IllegalStateException("Payment already exists for order: " + request.orderId());
        }
        Payment payment = new Payment(request.orderId(), request.customerId(), request.amount());

        // Once Circuit Breaker is implemented: wrap the bank gateway call below
        payment.setStatus(PaymentStatus.SUCCESS);

        // Once Kafka is implemented: publish PaymentProcessedEvent here

        return toResponse(paymentRepository.save(payment));
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
