package com.fahim.orderservice.dto;

import com.fahim.orderservice.model.Order;
import com.fahim.orderservice.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long id,
        Long productId,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getProductId(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalPrice(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
