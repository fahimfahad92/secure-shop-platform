package com.fahim.orderservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrderControllerSecurityTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private static final String ORDER_JSON = "{\"productId\":1,\"quantity\":2,\"unitPrice\":9.99}";

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
    void getOrders_withReadScope_returns200() throws Exception {
        mockMvc.perform(
                        get("/orders")
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "SCOPE_orders:read"))))
                .andExpect(status().isOk());
    }

    @Test
    void getOrders_withOnlyWriteScope_returns403() throws Exception {
        mockMvc.perform(
                        get("/orders")
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "SCOPE_orders:write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createOrder_withWriteScope_returns201() throws Exception {
        mockMvc.perform(
                        post("/orders")
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "SCOPE_orders:write")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ORDER_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void createOrder_withOnlyReadScope_returns403() throws Exception {
        mockMvc.perform(
                        post("/orders")
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "SCOPE_orders:read")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ORDER_JSON))
                .andExpect(status().isForbidden());
    }
}
