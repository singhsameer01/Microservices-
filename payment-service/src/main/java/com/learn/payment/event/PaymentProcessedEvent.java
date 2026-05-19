package com.learn.payment.event;

import com.learn.payment.model.PaymentStatus;
import java.time.Instant;

public record PaymentProcessedEvent(
        Long paymentId,
        Long orderId,
        PaymentStatus status,
        Instant timestamp
) {}
