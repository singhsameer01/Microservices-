package com.learn.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record OrderItemDto(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity,
        @NotNull @Positive BigDecimal price  // fetched from product-service once Feign is implemented
) {}
