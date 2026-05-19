package com.learn.notification.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event consumed from the "order-placed" Kafka topic.
 * Must mirror the structure published by order-service's OrderPlacedEvent.
 *
 * @param orderId    the database ID of the placed order
 * @param customerId the customer who placed the order
 * @param amount     total order amount
 * @param timestamp  when the event was created
 */
public record OrderPlacedEvent(
        Long orderId,
        String customerId,
        BigDecimal amount,
        Instant timestamp
) {}
