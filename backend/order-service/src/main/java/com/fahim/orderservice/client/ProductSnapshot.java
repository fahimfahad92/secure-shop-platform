package com.fahim.orderservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * The subset of a product that Order Service needs when placing an order. Unknown fields in the
 * Product Service response are ignored, so the catalog can grow fields without breaking orders.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductSnapshot(Long id, String name, BigDecimal price, Integer stock) {}
