package com.fahim.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fahim.gateway.StubServer.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** {@code POST /auth/dev-login} with the dev flag on: a real session, same as a browser login. */
@TestPropertySource(properties = "secure-shop.dev-login.enabled=true")
class DevLoginTest extends AbstractGatewayTest {

    private static final String SUB = "22222222-2222-2222-2222-222222222222";

    private static final String CREDENTIALS = "{\"username\":\"testuser\",\"password\":\"test\"}";

    private final String accessToken = TestKeys.accessToken(SUB, "user");

    private void keycloakAcceptsThePassword() {
        String idToken =
                TestKeys.idToken(STUB.keycloakUrl(), "secure-shop-test-client", SUB, "testuser");
        STUB.keycloakTokenResponse(
                200,
                """
                {"access_token":"%s","id_token":"%s","refresh_token":"refresh-1",
                 "token_type":"Bearer","expires_in":300,"scope":"openid orders:read"}
                """
                        .formatted(accessToken, idToken));
    }

    private MockHttpSession devLogin() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/auth/dev-login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(CREDENTIALS))
                        .andExpect(status().isOk())
                        .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void validCredentials_createSession_andReturnUserWithoutTokens() throws Exception {
        keycloakAcceptsThePassword();

        MvcResult result =
                mockMvc.perform(
                                post("/auth/dev-login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(CREDENTIALS))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.username").value("testuser"))
                        .andExpect(jsonPath("$.sub").value(SUB))
                        .andExpect(jsonPath("$.roles", contains("user")))
                        .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(accessToken)
                .doesNotContain("refresh-1");

        RecordedRequest tokenRequest = STUB.requestsTo("/kc/token").getFirst();
        assertThat(tokenRequest.body())
                .contains("grant_type=password")
                .contains("client_id=secure-shop-test-client")
                .contains("username=testuser");
    }

    @Test
    void devSession_relaysTheAccessTokenLikeABrowserSession() throws Exception {
        keycloakAcceptsThePassword();
        MockHttpSession session = devLogin();

        mockMvc.perform(get("/orders").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));

        RecordedRequest forwarded = STUB.requestsTo("/orders").getFirst();
        assertThat(forwarded.header(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + accessToken);
    }

    @Test
    void wrongPassword_returns401_andNoSession() throws Exception {
        STUB.keycloakTokenResponse(401, "{\"error\":\"invalid_grant\"}");

        MvcResult result =
                mockMvc.perform(
                                post("/auth/dev-login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(CREDENTIALS))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.error").value("invalid username or password"))
                        .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session != null) {
            mockMvc.perform(get("/auth/me").session(session)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void wrongClientSecret_returns502_notAWrongPasswordMessage() throws Exception {
        STUB.keycloakTokenResponse(
                401,
                "{\"error\":\"unauthorized_client\","
                        + "\"error_description\":\"Invalid client or Invalid client credentials\"}");

        mockMvc.perform(
                        post("/auth/dev-login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CREDENTIALS))
                .andExpect(status().isBadGateway())
                .andExpect(
                        jsonPath("$.error").value(containsString("KEYCLOAK_TEST_CLIENT_SECRET")));
    }

    @Test
    void missingPassword_returns400_withoutCallingKeycloak() throws Exception {
        mockMvc.perform(
                        post("/auth/dev-login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"testuser\"}"))
                .andExpect(status().isBadRequest());

        assertThat(STUB.requestsTo("/kc/token")).isEmpty();
    }

    @Test
    void logout_endsKeycloakSessionServerSide_andReturns204() throws Exception {
        keycloakAcceptsThePassword();
        MockHttpSession session = devLogin();

        mockMvc.perform(post("/logout").session(session).with(xsrfToken()))
                .andExpect(status().isNoContent());

        RecordedRequest endSession = STUB.requestsTo("/kc/logout").getFirst();
        assertThat(endSession.method()).isEqualTo("POST");
        assertThat(endSession.body())
                .contains("refresh_token=refresh-1")
                .contains("client_id=secure-shop-test-client");
        mockMvc.perform(get("/auth/me").session(session)).andExpect(status().isUnauthorized());
    }
}
