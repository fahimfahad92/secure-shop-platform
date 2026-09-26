package com.fahim.userservice.client;

import com.fahim.userservice.config.KeycloakAdminProperties;
import com.fahim.userservice.dto.RegisterRequest;
import com.fahim.userservice.exception.KeycloakAdminException;
import com.fahim.userservice.exception.UsernameAlreadyTakenException;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Talks to Keycloak's Admin REST API as the {@code user-service-admin-client} service account
 * (Client Credentials grant), which holds only {@code manage-users} and {@code view-users} on
 * {@code realm-management}.
 */
@Component
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

    /** Refresh slightly early so a request never races the expiry. */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final KeycloakAdminProperties properties;

    private volatile CachedToken cachedToken;

    public KeycloakAdminClient(
            RestClient keycloakAdminRestClient, KeycloakAdminProperties properties) {
        this.restClient = keycloakAdminRestClient;
        this.properties = properties;
    }

    /**
     * Creates the credential-holding user in Keycloak.
     *
     * @return the new user's {@code sub}, taken from the {@code Location} header
     */
    public String createUser(RegisterRequest request) {
        Map<String, Object> body =
                Map.of(
                        "username", request.username(),
                        "email", request.email(),
                        "enabled", true,
                        "emailVerified", true,
                        "credentials",
                                List.of(
                                        Map.of(
                                                "type",
                                                "password",
                                                "value",
                                                request.password(),
                                                "temporary",
                                                false)));

        URI location;
        try {
            location =
                    restClient
                            .post()
                            .uri(properties.usersUri())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .onStatus(
                                    HttpStatusCode::isError,
                                    (req, response) -> {
                                        if (response.getStatusCode().value()
                                                == HttpStatus.CONFLICT.value()) {
                                            throw new UsernameAlreadyTakenException(
                                                    request.username());
                                        }
                                        throw new KeycloakAdminException(
                                                "Keycloak returned "
                                                        + response.getStatusCode().value()
                                                        + " while creating a user");
                                    })
                            .toBodilessEntity()
                            .getHeaders()
                            .getLocation();
        } catch (ResourceAccessException ex) {
            throw new KeycloakAdminException("Keycloak Admin API is unreachable", ex);
        }

        if (location == null) {
            throw new KeycloakAdminException(
                    "Keycloak created the user but returned no Location header, so its id is"
                            + " unknown");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * Deletes a Keycloak user. Used as compensation when the local profile write fails after the
     * Keycloak user was already created. A 404 is treated as success — the goal is absence.
     */
    public void deleteUser(String keycloakSub) {
        try {
            restClient
                    .delete()
                    .uri(properties.usersUri() + "/{id}", keycloakSub)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError,
                            (req, response) -> {
                                if (response.getStatusCode().value()
                                        == HttpStatus.NOT_FOUND.value()) {
                                    log.warn(
                                            "Keycloak user {} was already absent when deleting",
                                            keycloakSub);
                                    return;
                                }
                                throw new KeycloakAdminException(
                                        "Keycloak returned "
                                                + response.getStatusCode().value()
                                                + " while deleting user "
                                                + keycloakSub);
                            })
                    .toBodilessEntity();
        } catch (ResourceAccessException ex) {
            throw new KeycloakAdminException(
                    "Keycloak Admin API is unreachable while deleting user " + keycloakSub, ex);
        }
    }

    private String accessToken() {
        CachedToken current = cachedToken;
        if (current != null && current.isValid()) {
            return current.value();
        }
        synchronized (this) {
            if (cachedToken != null && cachedToken.isValid()) {
                return cachedToken.value();
            }
            cachedToken = fetchToken();
            return cachedToken.value();
        }
    }

    private CachedToken fetchToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());

        TokenResponse response;
        try {
            response =
                    restClient
                            .post()
                            .uri(properties.tokenUri())
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .retrieve()
                            .onStatus(
                                    HttpStatusCode::isError,
                                    (req, res) -> {
                                        throw new KeycloakAdminException(
                                                "Could not obtain a service account token from"
                                                        + " Keycloak: HTTP "
                                                        + res.getStatusCode().value());
                                    })
                            .body(TokenResponse.class);
        } catch (ResourceAccessException ex) {
            throw new KeycloakAdminException(
                    "Keycloak is unreachable while obtaining a service account token", ex);
        }

        if (response == null || response.accessToken() == null) {
            throw new KeycloakAdminException("Keycloak returned an empty token response");
        }
        return new CachedToken(
                response.accessToken(),
                Instant.now().plusSeconds(response.expiresIn()).minus(EXPIRY_MARGIN));
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {}

    private record CachedToken(String value, Instant expiresAt) {

        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
