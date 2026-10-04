package com.fahim.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fahim.gateway.StubServer.RecordedRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** What reaches the services: which routes need a session, and which headers travel. */
class GatewayRoutingTest extends AbstractGatewayTest {

    private static final String ORDER_JSON = "{\"productId\":1,\"quantity\":2}";

    @Test
    void publicProductRead_isForwardedWithoutAnyBrowserCredentials() throws Exception {
        mockMvc.perform(
                        get("/products/7")
                                .cookie(new Cookie("JSESSIONID", "browser-session"))
                                .header("X-XSRF-TOKEN", "browser-csrf"))
                .andExpect(status().isOk());

        RecordedRequest forwarded = STUB.lastRequest();
        assertThat(forwarded.path()).isEqualTo("/products/7");
        assertThat(forwarded.header(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(forwarded.header(HttpHeaders.COOKIE)).isNull();
        assertThat(forwarded.header("X-XSRF-TOKEN")).isNull();
    }

    @Test
    void productList_isPublicToo() throws Exception {
        mockMvc.perform(get("/products")).andExpect(status().isOk());

        assertThat(STUB.lastRequest().path()).isEqualTo("/products");
    }

    @Test
    void orders_withoutSession_returns401_andNothingIsForwarded() throws Exception {
        mockMvc.perform(get("/orders")).andExpect(status().isUnauthorized());

        assertThat(STUB.requests()).isEmpty();
    }

    @Test
    void orders_withSession_relaysTheSessionsAccessToken() throws Exception {
        mockMvc.perform(
                        get("/orders")
                                .cookie(new Cookie("JSESSIONID", "browser-session"))
                                .with(browserSession("access-123", "user")))
                .andExpect(status().isOk());

        RecordedRequest forwarded = STUB.lastRequest();
        assertThat(forwarded.path()).isEqualTo("/orders");
        assertThat(forwarded.header(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-123");
        assertThat(forwarded.header(HttpHeaders.COOKIE)).isNull();
    }

    @Test
    void write_withSessionButNoCsrfToken_returns403_andNothingIsForwarded() throws Exception {
        mockMvc.perform(
                        post("/orders")
                                .with(browserSession("access-123", "user"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ORDER_JSON))
                .andExpect(status().isForbidden());

        assertThat(STUB.requests()).isEmpty();
    }

    @Test
    void write_withSessionAndCsrfToken_isForwardedWithBearerButWithoutCsrfHeader()
            throws Exception {
        mockMvc.perform(
                        post("/orders")
                                .with(browserSession("access-123", "user"))
                                .with(xsrfToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ORDER_JSON))
                .andExpect(status().isOk());

        RecordedRequest forwarded = STUB.lastRequest();
        assertThat(forwarded.method()).isEqualTo("POST");
        assertThat(forwarded.body()).isEqualTo(ORDER_JSON);
        assertThat(forwarded.header(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-123");
        assertThat(forwarded.header("X-XSRF-TOKEN")).isNull();
    }

    @Test
    void productWrite_needsASession_unlikeProductRead() throws Exception {
        mockMvc.perform(delete("/products/7").with(xsrfToken()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        delete("/products/7")
                                .with(browserSession("admin-access", "user", "admin"))
                                .with(xsrfToken()))
                .andExpect(status().isOk());

        assertThat(STUB.lastRequest().header(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer admin-access");
    }

    @Test
    void register_isForwardedWithoutSessionOrCsrfToken() throws Exception {
        mockMvc.perform(
                        post("/users/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"newuser\"}"))
                .andExpect(status().isOk());

        RecordedRequest forwarded = STUB.lastRequest();
        assertThat(forwarded.path()).isEqualTo("/users/register");
        assertThat(forwarded.header(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void ownProfile_withSession_relaysToken() throws Exception {
        mockMvc.perform(get("/users/me").with(browserSession("access-123", "user")))
                .andExpect(status().isOk());

        assertThat(STUB.lastRequest().header(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer access-123");
    }

    @Test
    void unknownPath_isDenied_withOrWithoutSession() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env").with(browserSession("access-123", "user")))
                .andExpect(status().isForbidden());

        assertThat(STUB.requests()).isEmpty();
    }
}
