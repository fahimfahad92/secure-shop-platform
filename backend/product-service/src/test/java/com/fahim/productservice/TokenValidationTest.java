package com.fahim.productservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Real signed tokens through the real decoder: audience check, and the {@code admin} realm role
 * read from the {@code realm_access} claim by {@code KeycloakRealmRoleConverter}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TokenValidationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private static final String PRODUCT_JSON =
            "{\"name\":\"Keyboard\",\"description\":\"Mechanical\",\"price\":99.99,\"stock\":10}";

    @Test
    void adminTokenForThisService_returns201() throws Exception {
        String token = TestJwts.token("product-service", "orders:read", "user", "admin");

        mockMvc.perform(
                        post("/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PRODUCT_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void adminTokenForAnotherService_returns401() throws Exception {
        String token = TestJwts.token("order-service", "orders:read", "user", "admin");

        mockMvc.perform(
                        post("/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PRODUCT_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userTokenForThisService_withoutAdminRole_returns403() throws Exception {
        String token = TestJwts.token("product-service", "orders:read", "user");

        mockMvc.perform(
                        post("/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PRODUCT_JSON))
                .andExpect(status().isForbidden());
    }

    /**
     * Public reads still reject a bad token rather than ignoring it: the bearer filter validates
     * any token that is sent, before {@code permitAll} is consulted. Callers browsing anonymously
     * just send no token.
     */
    @Test
    void publicRead_withTokenForAnotherService_returns401() throws Exception {
        String token = TestJwts.token("order-service", "orders:read", "user");

        mockMvc.perform(get("/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
