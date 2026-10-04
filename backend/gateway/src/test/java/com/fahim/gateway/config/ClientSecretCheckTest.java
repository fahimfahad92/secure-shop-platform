package com.fahim.gateway.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

class ClientSecretCheckTest {

    @Test
    void realSecret_passes() {
        assertThatCode(() -> check("a-real-secret")).doesNotThrowAnyException();
    }

    @Test
    void unresolvedPlaceholder_failsStartup_namingTheVariable() {
        assertThatThrownBy(() -> check("${KEYCLOAK_GATEWAY_CLIENT_SECRET}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KEYCLOAK_GATEWAY_CLIENT_SECRET");
    }

    @Test
    void blankSecret_failsStartup() {
        assertThatThrownBy(() -> check(" ")).isInstanceOf(IllegalStateException.class);
    }

    private static void check(String secret) {
        ClientRegistration registration =
                ClientRegistration.withRegistrationId("keycloak")
                        .clientId("secure-shop-gateway")
                        .clientSecret(secret)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                        .authorizationUri("http://kc/auth")
                        .tokenUri("http://kc/token")
                        .build();
        new ClientSecretCheck(new InMemoryClientRegistrationRepository(registration))
                .afterPropertiesSet();
    }
}
