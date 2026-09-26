package com.fahim.productservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class ProductControllerSecurityTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private static final String PRODUCT_JSON =
            "{\"name\":\"Keyboard\",\"description\":\"Mechanical\",\"price\":99.99,\"stock\":10}";

    private static final SimpleGrantedAuthority PRODUCT_ADMIN =
            new SimpleGrantedAuthority("ROLE_product-admin");

    private static final SimpleGrantedAuthority ORDERS_READ =
            new SimpleGrantedAuthority("SCOPE_orders:read");

    @Test
    void getProducts_noToken_returns200() throws Exception {
        mockMvc.perform(get("/products")).andExpect(status().isOk());
    }

    @Test
    void getProductById_noToken_returns404NotUnauthorized() throws Exception {
        mockMvc.perform(get("/products/99999")).andExpect(status().isNotFound());
    }

    @Test
    void createProduct_noToken_returns401() throws Exception {
        mockMvc.perform(
                        post("/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PRODUCT_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createProduct_withProductAdminRole_returns201() throws Exception {
        mockMvc.perform(
                        post("/products")
                                .with(jwt().authorities(PRODUCT_ADMIN))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PRODUCT_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    void createProduct_withoutProductAdminRole_returns403() throws Exception {
        mockMvc.perform(
                        post("/products")
                                .with(jwt().authorities(ORDERS_READ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PRODUCT_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteProduct_withoutProductAdminRole_returns403() throws Exception {
        mockMvc.perform(delete("/products/1").with(jwt().authorities(ORDERS_READ)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProduct_withProductAdminRole_invalidPayload_returns400() throws Exception {
        mockMvc.perform(
                        post("/products")
                                .with(jwt().authorities(PRODUCT_ADMIN))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\",\"price\":-1,\"stock\":-5}"))
                .andExpect(status().isBadRequest());
    }
}
