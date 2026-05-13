package com.learn.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal price
) {}
