package com.learn.order.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.order.event.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox_events table every 5 seconds and publishes unpublished events to Kafka.
 *
 * This runs in a separate transaction from the order placement — it only marks events as
 * published after Kafka confirms receipt, guaranteeing at-least-once delivery even if
 * the service crashes between saving the order and publishing the event.
 */
@Component
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.order-placed}")
    private String orderPlacedTopic;

    public OutboxEventPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findByPublishedFalse();
        if (pending.isEmpty()) return;

        log.debug("Processing {} pending outbox events", pending.size());
        for (OutboxEvent outboxEvent : pending) {
            try {
                OrderPlacedEvent event = objectMapper.readValue(outboxEvent.getPayload(), OrderPlacedEvent.class);
                kafkaTemplate.send(orderPlacedTopic, String.valueOf(outboxEvent.getAggregateId()), event)
                        .get(5, TimeUnit.SECONDS);
                outboxEvent.setPublished(true);
                log.info("Published outbox event id={} type={} aggregateId={}",
                        outboxEvent.getId(), outboxEvent.getEventType(), outboxEvent.getAggregateId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={}: {}", outboxEvent.getId(), e.getMessage(), e);
            }
        }
    }
}
