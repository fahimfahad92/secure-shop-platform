package com.fahim.orderservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Finding #29: when no connection is free within the pool's {@code connection-timeout}, the caller
 * gets a {@code 503} with {@code Retry-After}, not a {@code 500} after Hikari's default 30 s. A
 * pool of one, taken by the test, makes the timeout certain; 250 ms is Hikari's minimum.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.datasource.hikari.maximum-pool-size=1",
            "spring.datasource.hikari.minimum-idle=1",
            "spring.datasource.hikari.connection-timeout=250"
        })
class PoolTimeoutTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private DataSource dataSource;

    @Test
    void exhaustedPoolAnswers503() throws Exception {
        try (Connection held = dataSource.getConnection()) {
            mockMvc.perform(
                            get("/orders")
                                    .with(
                                            jwt().authorities(
                                                            new SimpleGrantedAuthority(
                                                                    "SCOPE_orders:read"),
                                                            new SimpleGrantedAuthority(
                                                                    "ROLE_user"))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.message").value("Database busy, try again"));
        }
    }
}
