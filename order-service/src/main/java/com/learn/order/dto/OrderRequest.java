package com.learn.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record OrderRequest(
        @NotBlank String customerId,
        @NotEmpty @Valid List<OrderItemDto> items
) {}
