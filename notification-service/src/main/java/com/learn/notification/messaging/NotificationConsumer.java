package com.learn.notification.messaging;

import com.learn.notification.event.OrderPlacedEvent;
import com.learn.notification.event.PaymentProcessedEvent;
import com.learn.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for notification events.
 *
 * Uses manual acknowledgment (MANUAL_IMMEDIATE) — the offset is only committed after
 * successful processing. Failed messages are retried 3 times by KafkaConfig's error
 * handler before being routed to the Dead Letter Topic ({topic}.DLT).
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;

    public NotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "order-placed", groupId = "notification-service-group")
    public void handleOrderPlaced(OrderPlacedEvent event, Acknowledgment ack) {
        MDC.put("correlationId", "order-" + event.orderId());
        try {
            log.info("Received OrderPlacedEvent for orderId={} customerId={} amount={}",
                    event.orderId(), event.customerId(), event.amount());
            notificationService.sendOrderConfirmation(event.customerId(), event.orderId());
            ack.acknowledge();
        } finally {
            MDC.clear();
        }
    }

    @KafkaListener(topics = "payment-processed", groupId = "notification-service-group")
    public void handlePaymentProcessed(PaymentProcessedEvent event, Acknowledgment ack) {
        MDC.put("correlationId", "order-" + event.orderId());
        try {
            log.info("Received PaymentProcessedEvent for orderId={} paymentId={} status={}",
                    event.orderId(), event.paymentId(), event.status());
            notificationService.sendPaymentReceipt(
                    String.valueOf(event.orderId()),
                    event.orderId(),
                    String.valueOf(event.status())
            );
            ack.acknowledge();
        } finally {
            MDC.clear();
        }
    }
}
