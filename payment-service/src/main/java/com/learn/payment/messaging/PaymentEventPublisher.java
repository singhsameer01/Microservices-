package com.learn.payment.messaging;

import com.learn.payment.event.PaymentProcessedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {

    private final KafkaTemplate<String, PaymentProcessedEvent> kafkaTemplate;
    private final String paymentProcessedTopic;

    public PaymentEventPublisher(
            KafkaTemplate<String, PaymentProcessedEvent> kafkaTemplate,
            @Value("${app.kafka.topics.payment-processed:payment-processed}") String paymentProcessedTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.paymentProcessedTopic = paymentProcessedTopic;
    }

    public void publish(PaymentProcessedEvent event) {
        kafkaTemplate.send(paymentProcessedTopic, String.valueOf(event.orderId()), event);
    }
}
