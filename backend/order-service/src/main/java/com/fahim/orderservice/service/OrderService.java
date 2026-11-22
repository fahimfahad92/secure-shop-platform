package com.fahim.orderservice.service;

import com.fahim.orderservice.client.ProductClient;
import com.fahim.orderservice.client.ProductSnapshot;
import com.fahim.orderservice.config.OrderProperties;
import com.fahim.orderservice.dto.CreateOrderRequest;
import com.fahim.orderservice.dto.UpdateOrderRequest;
import com.fahim.orderservice.exception.InsufficientStockException;
import com.fahim.orderservice.exception.OrderNotFoundException;
import com.fahim.orderservice.exception.OrderStateConflictException;
import com.fahim.orderservice.exception.ProductNoLongerAvailableException;
import com.fahim.orderservice.exception.ProductNotFoundException;
import com.fahim.orderservice.model.Order;
import com.fahim.orderservice.model.OrderStatus;
import com.fahim.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every method is scoped to the caller's Keycloak {@code sub}. A valid token is not enough — it has
 * to be the token of the user who owns the order.
 *
 * <p>Status changes only through {@link #confirm} and {@link #cancel}, and a placed order's
 * quantity only through {@link #update} (ADR-0016). Each is a conditional update in {@link
 * OrderRepository} with no transaction here (ADR-0015): if no row changed, the order is re-read to
 * tell "not found", "already done" and "not allowed" apart.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final OrderProperties properties;
    private final Clock clock;

    public OrderService(
            OrderRepository orderRepository,
            ProductClient productClient,
            OrderProperties properties,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
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

    /**
     * Changes the quantity of a {@code PENDING} order. Stock is checked again with Product Service;
     * the price stays the one from when the order was placed. No connection is held during the
     * Product call.
     */
    public Order update(String userSub, Long id, UpdateOrderRequest request) {
        Order order = getById(userSub, id);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw refuse(order, "change the quantity of");
        }

        ProductSnapshot product;
        try {
            product = productClient.fetchProduct(order.getProductId());
        } catch (ProductNotFoundException ex) {
            log.warn(
                    "Order {} of {}: quantity change refused, product {} no longer exists",
                    id,
                    userSub,
                    order.getProductId());
            throw new ProductNoLongerAvailableException(order.getProductId());
        }
        if (product.stock() < request.quantity()) {
            throw new InsufficientStockException(
                    order.getProductId(), request.quantity(), product.stock());
        }

        BigDecimal totalPrice =
                order.getUnitPrice().multiply(BigDecimal.valueOf(request.quantity()));
        int changed =
                orderRepository.updateQuantity(
                        id, userSub, request.quantity(), totalPrice, clock.instant());
        Order current = getById(userSub, id);
        if (changed == 0) {
            // The status changed while Product Service was answering (e.g. a cancel).
            throw refuse(current, "change the quantity of");
        }
        return current;
    }

    /** {@code PENDING} → {@code CONFIRMED}. Confirming a confirmed order changes nothing. */
    public Order confirm(String userSub, Long id) {
        int changed = orderRepository.confirm(id, userSub, clock.instant());
        Order current = getById(userSub, id);
        if (changed == 0 && current.getStatus() != OrderStatus.CONFIRMED) {
            throw refuse(current, "confirm");
        }
        return current;
    }

    /**
     * {@code PENDING} or recently {@code CONFIRMED} → {@code CANCELLED}. A confirmed order may be
     * cancelled only within {@code orders.cancel-window} of its confirmation. Cancelling a
     * cancelled order changes nothing.
     */
    public Order cancel(String userSub, Long id) {
        Instant now = clock.instant();
        int changed =
                orderRepository.cancel(id, userSub, now, now.minus(properties.cancelWindow()));
        Order current = getById(userSub, id);
        if (changed == 0 && current.getStatus() != OrderStatus.CANCELLED) {
            if (current.getStatus() == OrderStatus.CONFIRMED) {
                log.warn("Order {} of {}: cancel refused, window closed", id, userSub);
                throw new OrderStateConflictException(
                        "Order "
                                + id
                                + " was confirmed more than "
                                + properties.cancelWindow().toMinutes()
                                + " minutes ago and can no longer be cancelled");
            }
            throw refuse(current, "cancel");
        }
        return current;
    }

    @Transactional
    public void delete(String userSub, Long id) {
        Order order = getById(userSub, id);
        orderRepository.delete(order);
    }

    private static OrderStateConflictException refuse(Order order, String action) {
        log.warn(
                "Order {} of {}: {} refused, status is {}",
                order.getId(),
                order.getUserSub(),
                action,
                order.getStatus());
        return new OrderStateConflictException(
                "Cannot " + action + " order " + order.getId() + ": it is " + order.getStatus());
    }
}
