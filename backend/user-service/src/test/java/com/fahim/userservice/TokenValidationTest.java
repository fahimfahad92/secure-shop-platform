package com.fahim.userservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fahim.userservice.client.KeycloakAdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Real signed tokens through the real decoder, so the audience check actually runs. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TokenValidationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private KeycloakAdminClient keycloakAdminClient;

    @Test
    void tokenForThisService_isAccepted() throws Exception {
        String token = TestJwts.token("user-service", "orders:read", "user");

        // No profile exists for this random sub, so 404 proves the token got past authentication.
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void tokenForAnotherService_returns401() throws Exception {
        String token = TestJwts.token("order-service", "orders:read", "user");

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithKeycloakDefaultAudienceOnly_returns401() throws Exception {
        String token = TestJwts.token("account", "orders:read", "user");

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
