package com.learn.notification.messaging;

import com.learn.notification.event.OrderPlacedEvent;
import com.learn.notification.event.PaymentProcessedEvent;
import com.learn.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for notification events.
 *
 * <p>Listens on two topics and delegates to {@link NotificationService}:
 * <ul>
 *   <li>{@code order-placed} — triggers an order confirmation notification</li>
 *   <li>{@code payment-processed} — triggers a payment receipt notification</li>
 * </ul>
 * </p>
 *
 * <p>MDC (Mapped Diagnostic Context) enriches every log line produced during a listener
 * invocation with a {@code correlationId} (the orderId) so that all log lines for the
 * same order can be correlated in Zipkin / log aggregation tools.</p>
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;

    public NotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Handles order-placed events from the "order-placed" Kafka topic.
     * Sends an order confirmation to the customer.
     *
     * @param event the deserialized OrderPlacedEvent
     */
    @KafkaListener(topics = "order-placed", groupId = "notification-service-group")
    public void handleOrderPlaced(OrderPlacedEvent event) {
        MDC.put("correlationId", "order-" + event.orderId());
        try {
            log.info("Received OrderPlacedEvent for orderId={} customerId={} amount={}",
                    event.orderId(), event.customerId(), event.amount());
            notificationService.sendOrderConfirmation(event.customerId(), event.orderId());
        } finally {
            MDC.clear();
        }
    }

    /**
     * Handles payment-processed events from the "payment-processed" Kafka topic.
     * Sends a payment receipt to the customer.
     *
     * @param event the deserialized PaymentProcessedEvent
     */
    @KafkaListener(topics = "payment-processed", groupId = "notification-service-group")
    public void handlePaymentProcessed(PaymentProcessedEvent event) {
        MDC.put("correlationId", "order-" + event.orderId());
        try {
            log.info("Received PaymentProcessedEvent for orderId={} paymentId={} status={}",
                    event.orderId(), event.paymentId(), event.status());
            notificationService.sendPaymentReceipt(
                    String.valueOf(event.orderId()),
                    event.orderId(),
                    String.valueOf(event.status())
            );
        } finally {
            MDC.clear();
        }
    }
}
