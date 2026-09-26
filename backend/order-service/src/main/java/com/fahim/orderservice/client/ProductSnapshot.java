package com.fahim.orderservice.client;

import java.math.BigDecimal;

/**
 * The subset of a product that Order Service needs when placing an order. Unknown fields in the
 * Product Service response are ignored, so the catalog can grow fields without breaking orders.
 */
public record ProductSnapshot(Long id, String name, BigDecimal price, Integer stock) {}
