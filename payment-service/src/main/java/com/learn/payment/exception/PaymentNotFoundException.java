package com.learn.payment.exception;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(Long id) {
        super("Payment not found with id: " + id);
    }
    public PaymentNotFoundException(String field, Long value) {
        super("Payment not found for " + field + ": " + value);
    }
}
