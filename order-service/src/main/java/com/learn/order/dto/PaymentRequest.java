package com.learn.order.dto;

import java.math.BigDecimal;

/**
 * DTO sent to payment-service to initiate payment for an order.
 */
public record PaymentRequest(
        Long orderId,
        String customerId,
        BigDecimal amount
) {}
