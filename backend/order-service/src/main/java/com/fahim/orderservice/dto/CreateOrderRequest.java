package com.fahim.orderservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal unitPrice) {}
