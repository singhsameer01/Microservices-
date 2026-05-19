package com.learn.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO received from payment-service after payment processing.
 * Uses LocalDateTime to match payment-service's serialization format.
 */
public record PaymentResponse(
        Long id,
        Long orderId,
        String customerId,
        BigDecimal amount,
        String status,
        String transactionId,
        LocalDateTime createdAt
) {}
