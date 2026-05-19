package com.learn.payment.exception;

public class PaymentAlreadyExistsException extends RuntimeException {
    public PaymentAlreadyExistsException(Long orderId) {
        super("Payment already exists for order: " + orderId);
    }
}