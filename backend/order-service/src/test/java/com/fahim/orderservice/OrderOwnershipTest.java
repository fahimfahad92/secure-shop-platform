package com.fahim.orderservice;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fahim.orderservice.client.ProductClient;
import com.fahim.orderservice.client.ProductSnapshot;
import com.fahim.orderservice.repository.OrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Orders belong to the {@code sub} that placed them; another user's order is simply not there. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrderOwnershipTest extends AbstractIntegrationTest {

    private static final String OWNER = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_USER = "22222222-2222-2222-2222-222222222222";

    @Autowired private MockMvc mockMvc;

    @Autowired private OrderRepository orderRepository;

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

    private long placeOrderAs(String sub) throws Exception {
        String response =
                mockMvc.perform(
                                as(post("/orders"), sub)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"productId\":1,\"quantity\":2}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return body.get("id").asLong();
    }

    @Test
    void getAll_showsOnlyTheCallersOwnOrders() throws Exception {
        placeOrderAs(OWNER);
        placeOrderAs(OWNER);
        placeOrderAs(OTHER_USER);

        mockMvc.perform(as(get("/orders"), OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(as(get("/orders"), OTHER_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getById_anotherUsersOrder_returns404NotForbidden() throws Exception {
        long orderId = placeOrderAs(OWNER);

        mockMvc.perform(as(get("/orders/" + orderId), OTHER_USER)).andExpect(status().isNotFound());
    }

    @Test
    void update_anotherUsersOrder_returns404AndLeavesItUnchanged() throws Exception {
        long orderId = placeOrderAs(OWNER);

        mockMvc.perform(
                        as(put("/orders/" + orderId), OTHER_USER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":99,\"status\":\"CANCELLED\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(as(get("/orders/" + orderId), OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void delete_anotherUsersOrder_returns404AndKeepsIt() throws Exception {
        long orderId = placeOrderAs(OWNER);

        mockMvc.perform(as(delete("/orders/" + orderId), OTHER_USER))
                .andExpect(status().isNotFound());

        mockMvc.perform(as(get("/orders/" + orderId), OWNER)).andExpect(status().isOk());
    }

    @Test
    void ownerCanReadUpdateAndDeleteTheirOwnOrder() throws Exception {
        long orderId = placeOrderAs(OWNER);

        mockMvc.perform(as(get("/orders/" + orderId), OWNER)).andExpect(status().isOk());
        mockMvc.perform(
                        as(put("/orders/" + orderId), OWNER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":4,\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(4))
                .andExpect(jsonPath("$.totalPrice").value(39.96));
        mockMvc.perform(as(delete("/orders/" + orderId), OWNER)).andExpect(status().isNoContent());
        mockMvc.perform(as(get("/orders/" + orderId), OWNER)).andExpect(status().isNotFound());
    }
}
