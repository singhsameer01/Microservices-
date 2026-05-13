package com.learn.payment.dto;

import com.learn.payment.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long orderId,
        String customerId,
        BigDecimal amount,
        PaymentStatus status,
        String transactionId,
        LocalDateTime createdAt
) {}
