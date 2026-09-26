package com.fahim.orderservice.service;

import com.fahim.orderservice.client.ProductClient;
import com.fahim.orderservice.client.ProductSnapshot;
import com.fahim.orderservice.dto.CreateOrderRequest;
import com.fahim.orderservice.dto.UpdateOrderRequest;
import com.fahim.orderservice.exception.InsufficientStockException;
import com.fahim.orderservice.exception.OrderNotFoundException;
import com.fahim.orderservice.model.Order;
import com.fahim.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every method is scoped to the caller's Keycloak {@code sub}. A valid token is not enough — it has
 * to be the token of the user who owns the order.
 */
@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public OrderService(OrderRepository orderRepository, ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }

    public Order create(String userSub, CreateOrderRequest request) {
        ProductSnapshot product = productClient.fetchProduct(request.productId());
        if (product.stock() < request.quantity()) {
            throw new InsufficientStockException(
                    request.productId(), request.quantity(), product.stock());
        }

        Order order = new Order();
        order.setUserSub(userSub);
        order.setProductId(product.id());
        order.setQuantity(request.quantity());
        order.setUnitPrice(product.price());
        order.setTotalPrice(product.price().multiply(BigDecimal.valueOf(request.quantity())));
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<Order> getAll(String userSub) {
        return orderRepository.findByUserSub(userSub);
    }

    @Transactional(readOnly = true)
    public Order getById(String userSub, Long id) {
        return orderRepository
                .findByIdAndUserSub(id, userSub)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Order update(String userSub, Long id, UpdateOrderRequest request) {
        Order order = getById(userSub, id);
        order.setQuantity(request.quantity());
        order.setStatus(request.status());
        order.setTotalPrice(order.getUnitPrice().multiply(BigDecimal.valueOf(request.quantity())));
        return orderRepository.save(order);
    }

    public void delete(String userSub, Long id) {
        Order order = getById(userSub, id);
        orderRepository.delete(order);
    }
}
