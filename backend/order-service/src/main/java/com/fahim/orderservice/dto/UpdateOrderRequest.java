package com.fahim.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The only field a client may change on a placed order. Status is not here: it changes through
 * {@code POST /orders/{id}/confirm} and {@code /cancel} only (ADR-0016). Unknown fields, such as a
 * {@code status} from an older client, are rejected with 400
 * (spring.jackson.deserialization.fail-on-unknown-properties).
 */
public record UpdateOrderRequest(@NotNull @Min(1) Integer quantity) {}
