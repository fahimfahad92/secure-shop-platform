package com.fahim.orderservice;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fahim.orderservice.client.ProductClient;
import com.fahim.orderservice.client.ProductSnapshot;
import com.fahim.orderservice.exception.ProductNotFoundException;
import com.fahim.orderservice.exception.ProductServiceUnavailableException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** How each outcome of the internal Order to Product call surfaces to the API caller. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OrderProductLookupTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProductClient productClient;

    private MockHttpServletRequestBuilder createOrder(String json) {
        return post("/orders")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    @Test
    void createOrder_pricesFromProductServiceAndIgnoresClientSuppliedPrice() throws Exception {
        given(productClient.fetchProduct(anyLong()))
                .willReturn(new ProductSnapshot(1L, "Keyboard", new BigDecimal("99.99"), 50));

        mockMvc.perform(createOrder("{\"productId\":1,\"quantity\":2,\"unitPrice\":0.01}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice").value(99.99))
                .andExpect(jsonPath("$.totalPrice").value(199.98));
    }

    @Test
    void createOrder_unknownProduct_returns400() throws Exception {
        willThrow(new ProductNotFoundException(404L)).given(productClient).fetchProduct(anyLong());

        mockMvc.perform(createOrder("{\"productId\":404,\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_quantityAboveStock_returns409() throws Exception {
        given(productClient.fetchProduct(anyLong()))
                .willReturn(new ProductSnapshot(1L, "Keyboard", new BigDecimal("99.99"), 3));

        mockMvc.perform(createOrder("{\"productId\":1,\"quantity\":4}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createOrder_productServiceUnreachable_returns503() throws Exception {
        willThrow(new ProductServiceUnavailableException(1L, new RuntimeException("timed out")))
                .given(productClient)
                .fetchProduct(anyLong());

        mockMvc.perform(createOrder("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isServiceUnavailable());
    }
}
