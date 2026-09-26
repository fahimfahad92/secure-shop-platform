package com.fahim.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fahim.orderservice.client.ProductClient;
import com.fahim.orderservice.client.ProductSnapshot;
import com.fahim.orderservice.dto.CreateOrderRequest;
import com.fahim.orderservice.dto.UpdateOrderRequest;
import com.fahim.orderservice.exception.InsufficientStockException;
import com.fahim.orderservice.exception.OrderNotFoundException;
import com.fahim.orderservice.exception.ProductNotFoundException;
import com.fahim.orderservice.model.Order;
import com.fahim.orderservice.model.OrderStatus;
import com.fahim.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;

    @Mock private ProductClient productClient;

    @InjectMocks private OrderService orderService;

    @Test
    void create_takesPriceFromProductServiceAndCalculatesTotalPrice() {
        when(productClient.fetchProduct(1L))
                .thenReturn(new ProductSnapshot(1L, "Keyboard", new BigDecimal("10.00"), 100));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.create(new CreateOrderRequest(1L, 3));

        assertThat(result.getProductId()).isEqualTo(1L);
        assertThat(result.getQuantity()).isEqualTo(3);
        assertThat(result.getUnitPrice()).isEqualByComparingTo("10.00");
        assertThat(result.getTotalPrice()).isEqualByComparingTo("30.00");
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void create_quantityEqualToStock_isAllowed() {
        when(productClient.fetchProduct(1L))
                .thenReturn(new ProductSnapshot(1L, "Keyboard", new BigDecimal("10.00"), 5));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.create(new CreateOrderRequest(1L, 5));

        assertThat(result.getQuantity()).isEqualTo(5);
    }

    @Test
    void create_quantityAboveStock_throwsAndPersistsNothing() {
        when(productClient.fetchProduct(1L))
                .thenReturn(new ProductSnapshot(1L, "Keyboard", new BigDecimal("10.00"), 2));

        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(1L, 3)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested 3")
                .hasMessageContaining("available 2");
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void create_unknownProduct_propagatesAndPersistsNothing() {
        when(productClient.fetchProduct(404L)).thenThrow(new ProductNotFoundException(404L));

        assertThatThrownBy(() -> orderService.create(new CreateOrderRequest(404L, 1)))
                .isInstanceOf(ProductNotFoundException.class);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void getById_found_returnsOrder() {
        Order order = new Order();
        order.setId(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Order result = orderService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getById_notFound_throwsOrderNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById(99L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void update_recalculatesTotalPriceAndUpdatesStatus() {
        Order existing = new Order();
        existing.setId(1L);
        existing.setUnitPrice(new BigDecimal("10.00"));
        existing.setQuantity(2);
        existing.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateOrderRequest request = new UpdateOrderRequest(5, OrderStatus.CONFIRMED);
        Order result = orderService.update(1L, request);

        assertThat(result.getQuantity()).isEqualTo(5);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getTotalPrice()).isEqualByComparingTo("50.00");
    }

    @Test
    void update_notFound_throwsOrderNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());
        UpdateOrderRequest request = new UpdateOrderRequest(1, OrderStatus.CONFIRMED);

        assertThatThrownBy(() -> orderService.update(99L, request))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void delete_removesExistingOrder() {
        Order existing = new Order();
        existing.setId(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(existing));

        orderService.delete(1L);

        verify(orderRepository).delete(existing);
    }

    @Test
    void delete_notFound_throwsOrderNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.delete(99L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void getAll_returnsAllOrders() {
        when(orderRepository.findAll()).thenReturn(List.of(new Order(), new Order()));

        List<Order> result = orderService.getAll();

        assertThat(result).hasSize(2);
    }
}
