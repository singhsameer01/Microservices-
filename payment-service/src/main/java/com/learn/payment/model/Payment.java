package com.learn.payment.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(unique = true)
    private String transactionId;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Payment() {}

    public Payment(Long orderId, String customerId, BigDecimal amount) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.transactionId = UUID.randomUUID().toString();
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public String getTransactionId() { return transactionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
