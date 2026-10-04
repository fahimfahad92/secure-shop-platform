package com.fahim.gateway;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Client;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2ClientRequestPostProcessor;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Gateway tests run against {@link StubServer} for both Keycloak and the services.
 *
 * <p>Client registrations come from a test bean instead of {@code issuer-uri}: Boot runs OIDC
 * discovery at startup whenever {@code issuer-uri} is set, which would need a live Keycloak. Boot's
 * own registration bean backs off when this one exists.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(AbstractGatewayTest.TestClientRegistrations.class)
@TestPropertySource(properties = "KEYCLOAK_GATEWAY_CLIENT_SECRET=test-gateway-secret")
abstract class AbstractGatewayTest {

    static final StubServer STUB = StubServer.INSTANCE;

    @Autowired MockMvc mockMvc;

    @Autowired ClientRegistrationRepository clientRegistrations;

    @DynamicPropertySource
    static void downstreamServices(DynamicPropertyRegistry registry) {
        registry.add("order-service.base-url", STUB::baseUrl);
        registry.add("product-service.base-url", STUB::baseUrl);
        registry.add("user-service.base-url", STUB::baseUrl);
    }

    @BeforeEach
    void resetStub() {
        STUB.reset();
    }

    /**
     * A logged-in browser session (registration "keycloak") holding the given access token. {@code
     * oauth2Login()} sets the logged-in user; {@code oauth2Client()} then stores the authorized
     * client with our access token, replacing the placeholder one {@code oauth2Login()} stored.
     */
    RequestPostProcessor browserSession(String accessToken, String... realmRoles) {
        Instant now = Instant.now();
        OidcIdToken idToken =
                OidcIdToken.withTokenValue("id-token")
                        .subject("11111111-1111-1111-1111-111111111111")
                        .claim("preferred_username", "testuser")
                        .claim("email", "testuser@example.com")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(300))
                        .build();
        GrantedAuthority[] authorities =
                Stream.concat(
                                Stream.of(new OidcUserAuthority(idToken)),
                                Arrays.stream(realmRoles)
                                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role)))
                        .toArray(GrantedAuthority[]::new);
        ClientRegistration registration = this.clientRegistrations.findByRegistrationId("keycloak");
        OAuth2LoginRequestPostProcessor login =
                oauth2Login()
                        .clientRegistration(registration)
                        .oauth2User(
                                new DefaultOidcUser(
                                        Arrays.asList(authorities), idToken, "preferred_username"));
        OAuth2ClientRequestPostProcessor client =
                oauth2Client()
                        .clientRegistration(registration)
                        .principalName("testuser")
                        .accessToken(
                                new OAuth2AccessToken(
                                        OAuth2AccessToken.TokenType.BEARER,
                                        accessToken,
                                        now,
                                        now.plusSeconds(300)));
        return request -> client.postProcessRequest(login.postProcessRequest(request));
    }

    /**
     * What a JavaScript client does: send the {@code XSRF-TOKEN} cookie and echo its raw value in
     * the {@code X-XSRF-TOKEN} header. ({@code csrf().asHeader()} from spring-security-test sends
     * the masked form, which the SPA handler correctly rejects in a header.)
     */
    static RequestPostProcessor xsrfToken() {
        String token = "test-xsrf-token";
        return request -> {
            request.setCookies(new Cookie("XSRF-TOKEN", token));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClientRegistrations {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(
                    keycloak("keycloak", "secure-shop-gateway")
                            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                            .build(),
                    keycloak("keycloak-test", "secure-shop-test-client")
                            .authorizationGrantType(new AuthorizationGrantType("password"))
                            .build());
        }

        private static ClientRegistration.Builder keycloak(String registrationId, String clientId) {
            String keycloak = STUB.keycloakUrl();
            return ClientRegistration.withRegistrationId(registrationId)
                    .clientId(clientId)
                    .clientSecret(clientId + "-secret")
                    .scope("openid")
                    .issuerUri(keycloak)
                    .authorizationUri(keycloak + "/auth")
                    .tokenUri(keycloak + "/token")
                    .jwkSetUri(keycloak + "/certs")
                    .userInfoUri(keycloak + "/userinfo")
                    .userNameAttributeName("preferred_username")
                    .providerConfigurationMetadata(
                            Map.of("end_session_endpoint", keycloak + "/logout"));
        }
    }
}
