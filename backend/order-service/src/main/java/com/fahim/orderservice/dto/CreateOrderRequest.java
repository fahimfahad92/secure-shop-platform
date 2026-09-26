package com.fahim.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Price is deliberately absent — it comes from Product Service at order time, never from the
 * client. A client-supplied price is a price-tampering hole.
 */
public record CreateOrderRequest(@NotNull Long productId, @NotNull @Min(1) Integer quantity) {}
