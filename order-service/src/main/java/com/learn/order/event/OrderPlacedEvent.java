package com.learn.order.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published to the "order-placed" Kafka topic when an order is successfully placed.
 *
 * <p>payment-service and notification-service both consume this event.
 * The trusted package {@code com.learn.order.event} must be listed in each
 * consumer's {@code spring.kafka.consumer.properties.spring.json.trusted.packages}.</p>
 *
 * @param orderId    the database ID of the saved order
 * @param customerId the customer who placed the order
 * @param amount     total amount to be charged
 * @param timestamp  when the event was created
 */
public record OrderPlacedEvent(
        Long orderId,
        String customerId,
        BigDecimal amount,
        Instant timestamp
) {}
