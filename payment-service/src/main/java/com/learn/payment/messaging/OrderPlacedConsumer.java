package com.learn.payment.messaging;

import com.learn.payment.dto.PaymentRequest;
import com.learn.payment.event.OrderPlacedEvent;
import com.learn.payment.exception.PaymentAlreadyExistsException;
import com.learn.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacedConsumer.class);

    private final PaymentService paymentService;

    public OrderPlacedConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-placed:order-placed}")
    public void consume(OrderPlacedEvent event, Acknowledgment acknowledgment) {
        try {
            paymentService.process(new PaymentRequest(event.orderId(), event.customerId(), event.amount()));
            acknowledgment.acknowledge();
        } catch (PaymentAlreadyExistsException ex) {
            log.info("Skipping duplicate payment event for orderId {}", event.orderId());
            acknowledgment.acknowledge();
        }
    }
}
