package com.learn.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void sendOrderConfirmation(String customerId, Long orderId) {
        log.info("Order confirmation sent to customer={} for orderId={}", customerId, orderId);
    }

    public void sendPaymentReceipt(String customerId, Long orderId, String status) {
        log.info("Payment receipt sent to customer={} for orderId={} status={}", customerId, orderId, status);
    }
}
