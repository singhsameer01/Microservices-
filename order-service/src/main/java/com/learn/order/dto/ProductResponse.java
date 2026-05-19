package com.learn.order.dto;

import java.math.BigDecimal;

/**
 * DTO mirroring product-service's ProductResponse.
 * Only the fields that order-service actually uses are included.
 */
public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        String category
) {}
