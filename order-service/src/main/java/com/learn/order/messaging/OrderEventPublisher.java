package com.learn.order.messaging;

import com.learn.order.event.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes order domain events to Kafka topics.
 *
 * <p>Uses the orderId as the message key so that all events for the same order
 * land on the same partition — guaranteeing ordered delivery within an order.</p>
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @Value("${app.kafka.topics.order-placed}")
    private String orderPlacedTopic;

    public OrderEventPublisher(KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends an {@link OrderPlacedEvent} to the "order-placed" topic.
     * Publish is fire-and-forget with a callback that logs failures.
     *
     * @param event the event to publish
     */
    public void publishOrderPlaced(OrderPlacedEvent event) {
        CompletableFuture<SendResult<String, OrderPlacedEvent>> future =
                kafkaTemplate.send(orderPlacedTopic, String.valueOf(event.orderId()), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish OrderPlacedEvent for orderId={}: {}",
                        event.orderId(), ex.getMessage(), ex);
            } else {
                log.debug("Published OrderPlacedEvent for orderId={} to partition={} offset={}",
                        event.orderId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
