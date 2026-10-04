package com.fahim.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

/** Browser login start (PKCE), who-am-I, logout, and dev login being off by default. */
class AuthFlowTest extends AbstractGatewayTest {

    @Test
    void loginStart_redirectsToKeycloakWithPkceS256() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/oauth2/authorization/keycloak"))
                        .andExpect(status().is3xxRedirection())
                        .andReturn();

        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location)
                .startsWith(STUB.keycloakUrl() + "/auth")
                .contains("client_id=secure-shop-gateway")
                .contains("code_challenge=")
                .contains("code_challenge_method=S256");
    }

    @Test
    void callback_whenKeycloakRefusesGatewayClient_returns401WithReasonAndHint() throws Exception {
        MvcResult start =
                mockMvc.perform(get("/oauth2/authorization/keycloak"))
                        .andExpect(status().is3xxRedirection())
                        .andReturn();
        MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);
        String state =
                UriComponentsBuilder.fromUriString(
                                start.getResponse().getHeader(HttpHeaders.LOCATION))
                        .build()
                        .getQueryParams()
                        .getFirst("state");
        STUB.keycloakTokenResponse(
                401,
                "{\"error\":\"unauthorized_client\","
                        + "\"error_description\":\"Invalid client or Invalid client credentials\"}");

        mockMvc.perform(
                        get("/login/oauth2/code/keycloak")
                                .session(session)
                                .param("code", "auth-code")
                                .param("state", URLDecoder.decode(state, StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(
                        jsonPath("$.hint").value(containsString("KEYCLOAK_GATEWAY_CLIENT_SECRET")));

        assertThat(STUB.requestsTo("/kc/token").getFirst().body())
                .contains("grant_type=authorization_code")
                .contains("code_verifier=");
    }

    @Test
    void me_withSession_returnsUserAndRoles_butNoToken() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                get("/auth/me")
                                        .with(browserSession("access-secret", "user", "admin")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.username").value("testuser"))
                        .andExpect(jsonPath("$.sub").value("11111111-1111-1111-1111-111111111111"))
                        .andExpect(jsonPath("$.email").value("testuser@example.com"))
                        .andExpect(jsonPath("$.roles", containsInAnyOrder("admin", "user")))
                        .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("access-secret")
                .doesNotContain("id-token");
    }

    @Test
    void me_withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void browserLogout_redirectsToKeycloakEndSession() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/logout")
                                        .with(browserSession("access-123", "user"))
                                        .with(xsrfToken()))
                        .andExpect(status().is3xxRedirection())
                        .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .startsWith(STUB.keycloakUrl() + "/logout")
                .contains("id_token_hint=id-token")
                .contains("post_logout_redirect_uri=");
    }

    @Test
    void logout_withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(post("/logout").with(browserSession("access-123", "user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void landingPage_isPublic() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void devLogin_doesNotExistByDefault() throws Exception {
        mockMvc.perform(
                        post("/auth/dev-login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"testuser\",\"password\":\"test\"}"))
                .andExpect(status().isNotFound());

        assertThat(STUB.requests()).isEmpty();
    }
}
