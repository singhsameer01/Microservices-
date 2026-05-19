package com.learn.payment.event;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderPlacedEvent(
        Long orderId,
        String customerId,
        BigDecimal amount,
        Instant timestamp
) {}
