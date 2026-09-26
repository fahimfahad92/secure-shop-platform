package com.fahim.orderservice.controller;

import com.fahim.orderservice.dto.CreateOrderRequest;
import com.fahim.orderservice.dto.OrderResponse;
import com.fahim.orderservice.dto.UpdateOrderRequest;
import com.fahim.orderservice.model.Order;
import com.fahim.orderservice.service.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The owner always comes from the token's {@code sub}, never from the request body or a path
 * variable, so there is nothing for a client to substitute.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.create(jwt.getSubject(), request);
        return ResponseEntity.created(URI.create("/orders/" + order.getId()))
                .body(OrderResponse.from(order));
    }

    @GetMapping
    public List<OrderResponse> getAll(@AuthenticationPrincipal Jwt jwt) {
        return orderService.getAll(jwt.getSubject()).stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return OrderResponse.from(orderService.getById(jwt.getSubject(), id));
    }

    @PutMapping("/{id}")
    public OrderResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderRequest request) {
        return OrderResponse.from(orderService.update(jwt.getSubject(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        orderService.delete(jwt.getSubject(), id);
        return ResponseEntity.noContent().build();
    }
}
