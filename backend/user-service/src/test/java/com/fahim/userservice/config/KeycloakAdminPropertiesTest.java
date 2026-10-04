package com.fahim.userservice.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KeycloakAdminPropertiesTest {

    @Test
    void realSecret_binds() {
        assertThatCode(() -> properties("a-real-secret")).doesNotThrowAnyException();
    }

    @Test
    void unresolvedPlaceholder_failsStartup_namingTheVariable() {
        assertThatThrownBy(() -> properties("${KEYCLOAK_ADMIN_CLIENT_SECRET}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KEYCLOAK_ADMIN_CLIENT_SECRET");
    }

    @Test
    void missingSecret_failsStartup() {
        assertThatThrownBy(() -> properties(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> properties(" ")).isInstanceOf(IllegalStateException.class);
    }

    private static KeycloakAdminProperties properties(String secret) {
        return new KeycloakAdminProperties(
                "http://localhost:8081",
                "secure-shop",
                "user-service-admin-client",
                secret,
                2000,
                5000);
    }
}
