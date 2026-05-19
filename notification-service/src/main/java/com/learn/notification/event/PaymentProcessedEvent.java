package com.learn.notification.event;

import java.time.Instant;

/**
 * Event consumed from the "payment-processed" Kafka topic.
 * Must mirror the structure published by payment-service's PaymentProcessedEvent.
 *
 * @param paymentId the ID of the payment record
 * @param orderId   the order this payment belongs to
 * @param status    final payment status (SUCCESS or FAILED)
 * @param timestamp when the event was created
 */
public record PaymentProcessedEvent(
        Long paymentId,
        Long orderId,
        Object status,   // PaymentStatus enum value as string from payment-service
        Instant timestamp
) {}
