package com.fahim.orderservice.service;

import com.fahim.orderservice.dto.CreateOrderRequest;
import com.fahim.orderservice.dto.UpdateOrderRequest;
import com.fahim.orderservice.exception.OrderNotFoundException;
import com.fahim.orderservice.model.Order;
import com.fahim.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order create(CreateOrderRequest request) {
        Order order = new Order();
        order.setProductId(request.productId());
        order.setQuantity(request.quantity());
        order.setUnitPrice(request.unitPrice());
        order.setTotalPrice(request.unitPrice().multiply(BigDecimal.valueOf(request.quantity())));
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<Order> getAll() {
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Order getById(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Order update(Long id, UpdateOrderRequest request) {
        Order order = getById(id);
        order.setQuantity(request.quantity());
        order.setStatus(request.status());
        order.setTotalPrice(order.getUnitPrice().multiply(BigDecimal.valueOf(request.quantity())));
        return orderRepository.save(order);
    }

    public void delete(Long id) {
        Order order = getById(id);
        orderRepository.delete(order);
    }
}
