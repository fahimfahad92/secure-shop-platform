package com.fahim.orderservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fahim.orderservice.client.ProductClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Real signed tokens through the real decoder: audience check, and realm roles read from the {@code
 * realm_access} claim by {@code KeycloakRealmRoleConverter}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TokenValidationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProductClient productClient;

    @Test
    void tokenForThisService_withScopeAndUserRole_returns200() throws Exception {
        String token = TestJwts.token("order-service", "orders:read", "user");

        mockMvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void tokenForAnotherService_returns401() throws Exception {
        String token = TestJwts.token("product-service", "orders:read", "user");

        mockMvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithKeycloakDefaultAudienceOnly_returns401() throws Exception {
        String token = TestJwts.token("account", "orders:read", "user");

        mockMvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenForThisService_withoutUserRole_returns403() throws Exception {
        String token = TestJwts.token("order-service", "orders:read");

        mockMvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
