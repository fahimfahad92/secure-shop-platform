package com.fahim.orderservice.controller;

import com.fahim.orderservice.dto.CreateOrderRequest;
import com.fahim.orderservice.dto.OrderResponse;
import com.fahim.orderservice.dto.UpdateOrderRequest;
import com.fahim.orderservice.model.Order;
import com.fahim.orderservice.service.OrderService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.create(request);
        return ResponseEntity.created(URI.create("/orders/" + order.getId()))
                .body(OrderResponse.from(order));
    }

    @GetMapping
    public List<OrderResponse> getAll() {
        return orderService.getAll().stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable Long id) {
        return OrderResponse.from(orderService.getById(id));
    }

    @PutMapping("/{id}")
    public OrderResponse update(
            @PathVariable Long id, @Valid @RequestBody UpdateOrderRequest request) {
        return OrderResponse.from(orderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
