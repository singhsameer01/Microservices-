package com.learn.order.dto;

import com.learn.order.model.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        List<OrderItemResponse> items
) {}
