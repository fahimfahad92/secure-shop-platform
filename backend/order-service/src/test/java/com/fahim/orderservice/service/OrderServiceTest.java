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

    private static final String SUB = "7663bcb4-5f41-4b73-a47c-534a052c5a93";

    @Mock private OrderRepository orderRepository;

    @Mock private ProductClient productClient;

    @InjectMocks private OrderService orderService;

    @Test
    void create_stampsTheCallersSubAndPricesFromProductService() {
        when(productClient.fetchProduct(1L))
                .thenReturn(new ProductSnapshot(1L, "Keyboard", new BigDecimal("10.00"), 100));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.create(SUB, new CreateOrderRequest(1L, 3));

        assertThat(result.getUserSub()).isEqualTo(SUB);
        assertThat(result.getProductId()).isEqualTo(1L);
        assertThat(result.getQuantity()).isEqualTo(3);
        assertThat(result.getUnitPrice()).isEqualByComparingTo("10.00");
        assertThat(result.getTotalPrice()).isEqualByComparingTo("30.00");
    }

    @Test
    void create_quantityEqualToStock_isAllowed() {
        when(productClient.fetchProduct(1L))
                .thenReturn(new ProductSnapshot(1L, "Keyboard", new BigDecimal("10.00"), 5));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(orderService.create(SUB, new CreateOrderRequest(1L, 5)).getQuantity())
                .isEqualTo(5);
    }

    @Test
    void create_quantityAboveStock_throwsAndPersistsNothing() {
        when(productClient.fetchProduct(1L))
                .thenReturn(new ProductSnapshot(1L, "Keyboard", new BigDecimal("10.00"), 2));

        assertThatThrownBy(() -> orderService.create(SUB, new CreateOrderRequest(1L, 3)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested 3")
                .hasMessageContaining("available 2");
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void create_unknownProduct_propagatesAndPersistsNothing() {
        when(productClient.fetchProduct(404L)).thenThrow(new ProductNotFoundException(404L));

        assertThatThrownBy(() -> orderService.create(SUB, new CreateOrderRequest(404L, 1)))
                .isInstanceOf(ProductNotFoundException.class);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void getAll_returnsOnlyTheCallersOrders() {
        when(orderRepository.findByUserSub(SUB)).thenReturn(List.of(new Order(), new Order()));

        assertThat(orderService.getAll(SUB)).hasSize(2);
    }

    @Test
    void getById_ownedByCaller_returnsOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setUserSub(SUB);
        when(orderRepository.findByIdAndUserSub(1L, SUB)).thenReturn(Optional.of(order));

        assertThat(orderService.getById(SUB, 1L).getId()).isEqualTo(1L);
    }

    @Test
    void getById_ownedBySomeoneElse_throwsOrderNotFoundException() {
        // The repository query includes the sub, so another user's order looks exactly like an
        // order that does not exist — no way to probe for its existence.
        when(orderRepository.findByIdAndUserSub(1L, "another-sub")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById("another-sub", 1L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void update_recalculatesTotalPriceAndUpdatesStatus() {
        Order existing = new Order();
        existing.setId(1L);
        existing.setUserSub(SUB);
        existing.setUnitPrice(new BigDecimal("10.00"));
        existing.setQuantity(2);
        existing.setStatus(OrderStatus.PENDING);
        when(orderRepository.findByIdAndUserSub(1L, SUB)).thenReturn(Optional.of(existing));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result =
                orderService.update(SUB, 1L, new UpdateOrderRequest(5, OrderStatus.CONFIRMED));

        assertThat(result.getQuantity()).isEqualTo(5);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getTotalPrice()).isEqualByComparingTo("50.00");
    }

    @Test
    void update_ownedBySomeoneElse_throwsAndSavesNothing() {
        when(orderRepository.findByIdAndUserSub(1L, "another-sub")).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                orderService.update(
                                        "another-sub",
                                        1L,
                                        new UpdateOrderRequest(5, OrderStatus.CONFIRMED)))
                .isInstanceOf(OrderNotFoundException.class);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void delete_removesTheCallersOwnOrder() {
        Order existing = new Order();
        existing.setId(1L);
        existing.setUserSub(SUB);
        when(orderRepository.findByIdAndUserSub(1L, SUB)).thenReturn(Optional.of(existing));

        orderService.delete(SUB, 1L);

        verify(orderRepository).delete(existing);
    }

    @Test
    void delete_ownedBySomeoneElse_throwsAndDeletesNothing() {
        when(orderRepository.findByIdAndUserSub(1L, "another-sub")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.delete("another-sub", 1L))
                .isInstanceOf(OrderNotFoundException.class);
        verify(orderRepository, never()).delete(any(Order.class));
    }
}
