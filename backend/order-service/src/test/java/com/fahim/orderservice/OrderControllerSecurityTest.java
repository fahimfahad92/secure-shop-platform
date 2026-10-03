package com.fahim.orderservice;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fahim.orderservice.client.ProductClient;
import com.fahim.orderservice.client.ProductSnapshot;
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

/** Every order endpoint needs both the scope (what the app may do) and the user role (who). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrderControllerSecurityTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProductClient productClient;

    private static final String ORDER_JSON = "{\"productId\":1,\"quantity\":2}";

    private static final SimpleGrantedAuthority ORDERS_READ =
            new SimpleGrantedAuthority("SCOPE_orders:read");

    private static final SimpleGrantedAuthority ORDERS_WRITE =
            new SimpleGrantedAuthority("SCOPE_orders:write");

    private static final SimpleGrantedAuthority USER_ROLE = new SimpleGrantedAuthority("ROLE_user");

    @BeforeEach
    void stubProductLookup() {
        given(productClient.fetchProduct(anyLong()))
                .willReturn(new ProductSnapshot(1L, "Keyboard", new BigDecimal("9.99"), 50));
    }

    @Test
    void getOrders_noToken_returns401() throws Exception {
        mockMvc.perform(get("/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void createOrder_noToken_returns401() throws Exception {
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOrders_withReadScopeAndUserRole_returns200() throws Exception {
        mockMvc.perform(get("/orders").with(jwt().authorities(ORDERS_READ, USER_ROLE)))
                .andExpect(status().isOk());
    }

    @Test
    void getOrders_withOnlyWriteScope_returns403() throws Exception {
        mockMvc.perform(get("/orders").with(jwt().authorities(ORDERS_WRITE, USER_ROLE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOrders_withUserRoleButNoScope_returns403() throws Exception {
        mockMvc.perform(get("/orders").with(jwt().authorities(USER_ROLE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOrders_withReadScopeButNoUserRole_returns403() throws Exception {
        mockMvc.perform(get("/orders").with(jwt().authorities(ORDERS_READ)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createOrder_withWriteScopeAndUserRole_returns201() throws Exception {
        mockMvc.perform(
                        post("/orders")
                                .with(jwt().authorities(ORDERS_WRITE, USER_ROLE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ORDER_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void createOrder_withOnlyReadScope_returns403() throws Exception {
        mockMvc.perform(
                        post("/orders")
                                .with(jwt().authorities(ORDERS_READ, USER_ROLE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ORDER_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void createOrder_withUserRoleButNoScope_returns403() throws Exception {
        mockMvc.perform(
                        post("/orders")
                                .with(jwt().authorities(USER_ROLE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ORDER_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void createOrder_withWriteScopeButNoUserRole_returns403() throws Exception {
        mockMvc.perform(
                        post("/orders")
                                .with(jwt().authorities(ORDERS_WRITE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ORDER_JSON))
                .andExpect(status().isForbidden());
    }
}
