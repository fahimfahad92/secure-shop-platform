package com.fahim.orderservice.dto;

import com.fahim.orderservice.model.OrderStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderRequest(@NotNull @Min(1) Integer quantity, @NotNull OrderStatus status) {}
