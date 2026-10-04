package com.fahim.gateway.devlogin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Talks to Keycloak for dev login: password grant to get tokens, and back-channel logout to end the
 * Keycloak session again.
 *
 * <p>Uses a plain {@link RestClient} rather than Spring Security's password-grant support, which is
 * deprecated in 6.x and removed in Spring Security 7 (Spring Boot 4).
 */
@Component
@ConditionalOnDevLogin
public class KeycloakPasswordGrant {

    static final String REGISTRATION_ID = "keycloak-test";

    private static final Logger log = LoggerFactory.getLogger(KeycloakPasswordGrant.class);

    private static final String END_SESSION_ENDPOINT = "end_session_endpoint";

    private static final String INVALID_GRANT = "invalid_grant";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ClientRegistration registration;

    private final RestClient restClient =
            RestClient.builder()
                    .messageConverters(
                            converters ->
                                    converters.add(
                                            0, new OAuth2AccessTokenResponseHttpMessageConverter()))
                    .build();

    KeycloakPasswordGrant(ClientRegistrationRepository clientRegistrations) {
        this.registration = clientRegistrations.findByRegistrationId(REGISTRATION_ID);
        if (this.registration == null) {
            throw new IllegalStateException(
                    "Dev login is enabled but client registration '"
                            + REGISTRATION_ID
                            + "' is missing. Run with the dev profile, which defines it.");
        }
        log.warn(
                "DEV LOGIN ENABLED: POST /auth/dev-login accepts a username and password. "
                        + "Local testing only, never enable this in a shared environment.");
    }

    ClientRegistration registration() {
        return this.registration;
    }

    /**
     * Keycloak answers 401 both for a wrong password ({@code invalid_grant}) and for a wrong client
     * secret ({@code unauthorized_client}/{@code invalid_client}). Only the first is the caller's
     * fault: it becomes {@link BadCredentialsException}. Anything else is the Gateway's own
     * misconfiguration and becomes {@link DevLoginClientException}, with Keycloak's error code
     * logged so it can be told apart.
     */
    OAuth2AccessTokenResponse requestTokens(String username, String password) {
        MultiValueMap<String, String> form = clientCredentials();
        form.add(OAuth2ParameterNames.GRANT_TYPE, "password");
        form.add(OAuth2ParameterNames.USERNAME, username);
        form.add(OAuth2ParameterNames.PASSWORD, password);
        form.add(OAuth2ParameterNames.SCOPE, String.join(" ", this.registration.getScopes()));
        return this.restClient
                .post()
                .uri(this.registration.getProviderDetails().getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        (request, response) -> {
                            String error = oauthErrorCode(response.getBody().readAllBytes());
                            if (INVALID_GRANT.equals(error)) {
                                throw new BadCredentialsException(
                                        "Keycloak rejected the dev login credentials");
                            }
                            log.warn(
                                    "Keycloak refused the dev login client '{}': HTTP {} error={}."
                                            + " Check KEYCLOAK_TEST_CLIENT_SECRET.",
                                    this.registration.getClientId(),
                                    response.getStatusCode().value(),
                                    error);
                            throw new DevLoginClientException(
                                    this.registration.getClientId(), error);
                        })
                .body(OAuth2AccessTokenResponse.class);
    }

    /** The {@code error} field of an OAuth2 error response, or {@code "unknown"}. */
    private static String oauthErrorCode(byte[] body) {
        try {
            String error = JSON.readTree(body).path("error").asText();
            return error.isEmpty() ? "unknown" : error;
        } catch (IOException ex) {
            return "unknown";
        }
    }

    /**
     * Ends the Keycloak session behind a dev login. A browser logout does this with a redirect to
     * the end-session endpoint; a Postman client has no browser, so the Gateway posts the refresh
     * token there itself. Failure only means the Keycloak session times out on its own.
     */
    void endSession(OAuth2RefreshToken refreshToken) {
        Object endSessionEndpoint =
                this.registration
                        .getProviderDetails()
                        .getConfigurationMetadata()
                        .get(END_SESSION_ENDPOINT);
        if (refreshToken == null || endSessionEndpoint == null) {
            return;
        }
        MultiValueMap<String, String> form = clientCredentials();
        form.add(OAuth2ParameterNames.REFRESH_TOKEN, refreshToken.getTokenValue());
        try {
            this.restClient
                    .post()
                    .uri(endSessionEndpoint.toString())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("Could not end the Keycloak session for a dev login: {}", ex.getMessage());
        }
    }

    private MultiValueMap<String, String> clientCredentials() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add(OAuth2ParameterNames.CLIENT_ID, this.registration.getClientId());
        form.add(OAuth2ParameterNames.CLIENT_SECRET, this.registration.getClientSecret());
        return form;
    }
}
