package com.fahim.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.fahim.orderservice.exception.ProductServiceUnavailableException;
import com.fahim.orderservice.model.Order;
import com.fahim.orderservice.model.OrderStatus;
import com.fahim.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final String SUB = "7663bcb4-5f41-4b73-a47c-534a052c5a93";

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    private static final Duration CANCEL_WINDOW = Duration.ofMinutes(30);

    @Mock private OrderRepository orderRepository;

    @Mock private ProductClient productClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService =
                new OrderService(
                        orderRepository,
                        productClient,
                        new OrderProperties(CANCEL_WINDOW),
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(1L);
        order.setUserSub(SUB);
        order.setProductId(7L);
        order.setQuantity(2);
        order.setUnitPrice(new BigDecimal("10.00"));
        order.setTotalPrice(new BigDecimal("20.00"));
        order.setStatus(status);
        return order;
    }

    private static ProductSnapshot product(String price, int stock) {
        return new ProductSnapshot(7L, "Keyboard", new BigDecimal(price), stock);
    }

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
    void update_pending_checksStockAndChangesQuantityKeepingCreationPrice() {
        Order after = order(OrderStatus.PENDING);
        after.setQuantity(5);
        after.setTotalPrice(new BigDecimal("50.00"));
        when(orderRepository.findByIdAndUserSub(1L, SUB))
                .thenReturn(Optional.of(order(OrderStatus.PENDING)), Optional.of(after));
        // The catalog price has changed since the order was placed; the order keeps its own.
        when(productClient.fetchProduct(7L)).thenReturn(product("12.50", 10));
        when(orderRepository.updateQuantity(1L, SUB, 5, new BigDecimal("50.00"), NOW))
                .thenReturn(1);

        Order result = orderService.update(SUB, 1L, new UpdateOrderRequest(5));

        assertThat(result.getQuantity()).isEqualTo(5);
        assertThat(result.getTotalPrice()).isEqualByComparingTo("50.00");
        verify(orderRepository).updateQuantity(1L, SUB, 5, new BigDecimal("50.00"), NOW);
    }

    @Test
    void update_notPending_throwsConflictWithoutCallingProduct() {
        when(orderRepository.findByIdAndUserSub(1L, SUB))
                .thenReturn(Optional.of(order(OrderStatus.CONFIRMED)));

        assertThatThrownBy(() -> orderService.update(SUB, 1L, new UpdateOrderRequest(5)))
                .isInstanceOf(OrderStateConflictException.class)
                .hasMessageContaining("CONFIRMED");
        verify(productClient, never()).fetchProduct(anyLong());
        verify(orderRepository, never())
                .updateQuantity(anyLong(), anyString(), anyInt(), any(), any());
    }

    @Test
    void update_insufficientStock_throwsAndWritesNothing() {
        when(orderRepository.findByIdAndUserSub(1L, SUB))
                .thenReturn(Optional.of(order(OrderStatus.PENDING)));
        when(productClient.fetchProduct(7L)).thenReturn(product("10.00", 4));

        assertThatThrownBy(() -> orderService.update(SUB, 1L, new UpdateOrderRequest(5)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested 5")
                .hasMessageContaining("available 4");
        verify(orderRepository, never())
                .updateQuantity(anyLong(), anyString(), anyInt(), any(), any());
    }

    @Test
    void update_productGone_throwsProductNoLongerAvailable() {
        when(orderRepository.findByIdAndUserSub(1L, SUB))
                .thenReturn(Optional.of(order(OrderStatus.PENDING)));
        when(productClient.fetchProduct(7L)).thenThrow(new ProductNotFoundException(7L));

        assertThatThrownBy(() -> orderService.update(SUB, 1L, new UpdateOrderRequest(5)))
                .isInstanceOf(ProductNoLongerAvailableException.class);
        verify(orderRepository, never())
                .updateQuantity(anyLong(), anyString(), anyInt(), any(), any());
    }

    @Test
    void update_productServiceDown_throwsAndWritesNothing() {
        when(orderRepository.findByIdAndUserSub(1L, SUB))
                .thenReturn(Optional.of(order(OrderStatus.PENDING)));
        when(productClient.fetchProduct(7L))
                .thenThrow(new ProductServiceUnavailableException(7L, new RuntimeException()));

        assertThatThrownBy(() -> orderService.update(SUB, 1L, new UpdateOrderRequest(5)))
                .isInstanceOf(ProductServiceUnavailableException.class);
        verify(orderRepository, never())
                .updateQuantity(anyLong(), anyString(), anyInt(), any(), any());
    }

    @Test
    void update_statusChangedDuringProductCall_throwsConflict() {
        // Read as PENDING, cancelled in another tab while Product Service answered: the
        // conditional update matches no row, and the re-read shows why.
        when(orderRepository.findByIdAndUserSub(1L, SUB))
                .thenReturn(
                        Optional.of(order(OrderStatus.PENDING)),
                        Optional.of(order(OrderStatus.CANCELLED)));
        when(productClient.fetchProduct(7L)).thenReturn(product("10.00", 10));
        when(orderRepository.updateQuantity(1L, SUB, 5, new BigDecimal("50.00"), NOW))
                .thenReturn(0);

        assertThatThrownBy(() -> orderService.update(SUB, 1L, new UpdateOrderRequest(5)))
                .isInstanceOf(OrderStateConflictException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void update_ownedBySomeoneElse_throwsNotFound() {
        when(orderRepository.findByIdAndUserSub(1L, "another-sub")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.update("another-sub", 1L, new UpdateOrderRequest(5)))
                .isInstanceOf(OrderNotFoundException.class);
        verify(productClient, never()).fetchProduct(anyLong());
    }

    @Test
    void confirm_pending_confirms() {
        when(orderRepository.confirm(1L, SUB, NOW)).thenReturn(1);
        when(orderRepository.findByIdAndUserSub(1L, SUB))
                .thenReturn(Optional.of(order(OrderStatus.CONFIRMED)));

        assertThat(orderService.confirm(SUB, 1L).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void confirm_alreadyConfirmed_isIdempotent() {
        when(orderRepository.confirm(1L, SUB, NOW)).thenReturn(0);
        when(orderRepository.findByIdAndUserSub(1L, SUB))
                .thenReturn(Optional.of(order(OrderStatus.CONFIRMED)));

        assertThat(orderService.confirm(SUB, 1L).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void confirm_cancelled_throwsConflict() {
        when(orderRepository.confirm(1L, SUB, NOW)).thenReturn(0);
        when(orderRepository.findByIdAndUserSub(1L, SUB))
                .thenReturn(Optional.of(order(OrderStatus.CANCELLED)));

        assertThatThrownBy(() -> orderService.confirm(SUB, 1L))
                .isInstanceOf(OrderStateConflictException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void confirm_notFound_throws() {
        when(orderRepository.confirm(1L, SUB, NOW)).thenReturn(0);
        when(orderRepository.findByIdAndUserSub(1L, SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirm(SUB, 1L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void cancel_pending_cancels() {
        when(orderRepository.cancel(1L, SUB, NOW, NOW.minus(CANCEL_WINDOW))).thenReturn(1);
        when(orderRepository.findByIdAndUserSub(1L, SUB))
                .thenReturn(Optional.of(order(OrderStatus.CANCELLED)));

        assertThat(orderService.cancel(SUB, 1L).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_confirmedWithinWindow_cancels() {
        // The window itself is in the query (statusChangedAt > cutoff); the service's job is to
        // pass the right cutoff: now minus orders.cancel-window.
        when(orderRepository.cancel(1L, SUB, NOW, NOW.minus(CANCEL_WINDOW))).thenReturn(1);
        when(orderRepository.findByIdAndUserSub(1L, SUB))
                .thenReturn(Optional.of(order(OrderStatus.CANCELLED)));

        orderService.cancel(SUB, 1L);

        verify(orderRepository).cancel(1L, SUB, NOW, Instant.parse("2026-11-22T09:30:00Z"));
    }

    @Test
    void cancel_confirmedAfterWindow_throwsConflict() {
        when(orderRepository.cancel(1L, SUB, NOW, NOW.minus(CANCEL_WINDOW))).thenReturn(0);
        when(orderRepository.findByIdAndUserSub(1L, SUB))
                .thenReturn(Optional.of(order(OrderStatus.CONFIRMED)));

        assertThatThrownBy(() -> orderService.cancel(SUB, 1L))
                .isInstanceOf(OrderStateConflictException.class)
                .hasMessageContaining("30 minutes");
    }

    @Test
    void cancel_alreadyCancelled_isIdempotent() {
        when(orderRepository.cancel(1L, SUB, NOW, NOW.minus(CANCEL_WINDOW))).thenReturn(0);
        when(orderRepository.findByIdAndUserSub(1L, SUB))
                .thenReturn(Optional.of(order(OrderStatus.CANCELLED)));

        assertThat(orderService.cancel(SUB, 1L).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_notFound_throws() {
        when(orderRepository.cancel(1L, SUB, NOW, NOW.minus(CANCEL_WINDOW))).thenReturn(0);
        when(orderRepository.findByIdAndUserSub(1L, SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancel(SUB, 1L))
                .isInstanceOf(OrderNotFoundException.class);
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
