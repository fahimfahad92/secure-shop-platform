package com.fahim.orderservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fahim.orderservice.client.ProductClient;
import com.fahim.orderservice.client.ProductSnapshot;
import com.fahim.orderservice.model.Order;
import com.fahim.orderservice.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Order status changes only through confirm and cancel (ADR-0016), each a conditional update
 * against real Postgres: the {@code WHERE status = ...} is what makes a transition safe without a
 * transaction (ADR-0015).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrderStatusTransitionsTest extends AbstractIntegrationTest {

    private static final String OWNER = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_USER = "22222222-2222-2222-2222-222222222222";

    @Autowired private MockMvc mockMvc;

    @Autowired private OrderRepository orderRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ProductClient productClient;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        given(productClient.fetchProduct(anyLong()))
                .willReturn(new ProductSnapshot(1L, "Keyboard", new BigDecimal("9.99"), 500));
    }

    private static MockHttpServletRequestBuilder as(
            MockHttpServletRequestBuilder builder, String sub) {
        return builder.with(
                jwt().jwt(token -> token.subject(sub))
                        .authorities(
                                new SimpleGrantedAuthority("SCOPE_orders:read"),
                                new SimpleGrantedAuthority("SCOPE_orders:write"),
                                new SimpleGrantedAuthority("ROLE_user")));
    }

    private long placeOrder() throws Exception {
        String response =
                mockMvc.perform(
                                as(post("/orders"), OWNER)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"productId\":1,\"quantity\":2}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void confirm(long orderId) throws Exception {
        mockMvc.perform(as(post("/orders/" + orderId + "/confirm"), OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmThenCancel_withinWindow_endsCancelled() throws Exception {
        long orderId = placeOrder();
        confirm(orderId);

        mockMvc.perform(as(post("/orders/" + orderId + "/cancel"), OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_confirmedOutsideWindow_returns409() throws Exception {
        long orderId = placeOrder();
        confirm(orderId);
        // Confirmed 31 minutes ago: past orders.cancel-window (30 minutes).
        jdbcTemplate.update(
                "UPDATE order_schema.orders SET status_changed_at = now() - interval '31 minutes'"
                        + " WHERE id = ?",
                orderId);

        mockMvc.perform(as(post("/orders/" + orderId + "/cancel"), OWNER))
                .andExpect(status().isConflict());

        mockMvc.perform(as(get("/orders/" + orderId), OWNER))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirm_twice_secondIs200AndUnchanged() throws Exception {
        long orderId = placeOrder();
        confirm(orderId);
        Instant firstChange = orderRepository.findById(orderId).orElseThrow().getStatusChangedAt();

        confirm(orderId);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatusChangedAt())
                .isEqualTo(firstChange);
    }

    @Test
    void cancel_cancelled_isIdempotentAndConfirmIsRefused() throws Exception {
        long orderId = placeOrder();
        mockMvc.perform(as(post("/orders/" + orderId + "/cancel"), OWNER))
                .andExpect(status().isOk());

        mockMvc.perform(as(post("/orders/" + orderId + "/cancel"), OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(as(post("/orders/" + orderId + "/confirm"), OWNER))
                .andExpect(status().isConflict());
    }

    @Test
    void put_withStatusField_returns400() throws Exception {
        long orderId = placeOrder();

        mockMvc.perform(
                        as(put("/orders/" + orderId), OWNER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":3,\"status\":\"CANCELLED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(as(get("/orders/" + orderId), OWNER))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void put_onConfirmedOrder_returns409AndLeavesItUnchanged() throws Exception {
        long orderId = placeOrder();
        confirm(orderId);

        mockMvc.perform(
                        as(put("/orders/" + orderId), OWNER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":3}"))
                .andExpect(status().isConflict());

        mockMvc.perform(as(get("/orders/" + orderId), OWNER))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void put_aboveStock_returns409() throws Exception {
        long orderId = placeOrder();
        given(productClient.fetchProduct(anyLong()))
                .willReturn(new ProductSnapshot(1L, "Keyboard", new BigDecimal("9.99"), 3));

        mockMvc.perform(
                        as(put("/orders/" + orderId), OWNER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":4}"))
                .andExpect(status().isConflict());
    }

    @Test
    void put_whileCancelledDuringProductCall_staysCancelled() throws Exception {
        long orderId = placeOrder();
        // The order is cancelled while Product Service is answering the stock check. With a
        // load-modify-save, the PUT would write PENDING back over CANCELLED.
        given(productClient.fetchProduct(anyLong()))
                .willAnswer(
                        invocation -> {
                            Instant now = Instant.now();
                            orderRepository.cancel(
                                    orderId, OWNER, now, now.minus(Duration.ofMinutes(30)));
                            return new ProductSnapshot(1L, "Keyboard", new BigDecimal("9.99"), 500);
                        });

        mockMvc.perform(
                        as(put("/orders/" + orderId), OWNER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":3}"))
                .andExpect(status().isConflict());

        Order stored = orderRepository.findById(orderId).orElseThrow();
        assertThat(stored.getStatus().name()).isEqualTo("CANCELLED");
        assertThat(stored.getQuantity()).isEqualTo(2);
    }

    @Test
    void confirm_anotherUsersOrder_returns404() throws Exception {
        long orderId = placeOrder();

        mockMvc.perform(as(post("/orders/" + orderId + "/confirm"), OTHER_USER))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(post("/orders/" + orderId + "/cancel"), OTHER_USER))
                .andExpect(status().isNotFound());

        mockMvc.perform(as(get("/orders/" + orderId), OWNER))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void statusChangedAt_isSetOnCreateAndOnEachTransition() throws Exception {
        long orderId = placeOrder();
        Instant created = orderRepository.findById(orderId).orElseThrow().getStatusChangedAt();
        assertThat(created).isNotNull();

        confirm(orderId);
        Instant confirmed = orderRepository.findById(orderId).orElseThrow().getStatusChangedAt();
        assertThat(confirmed).isAfterOrEqualTo(created);

        mockMvc.perform(as(post("/orders/" + orderId + "/cancel"), OWNER))
                .andExpect(status().isOk());
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatusChangedAt())
                .isAfterOrEqualTo(confirmed);
    }
}
