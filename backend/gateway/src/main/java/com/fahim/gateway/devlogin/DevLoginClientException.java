package com.fahim.gateway.devlogin;

/**
 * Keycloak refused the Gateway's own dev login client (wrong or missing secret, client disabled,
 * direct access grants off). Not the caller's fault, so it must not look like a wrong password.
 */
class DevLoginClientException extends RuntimeException {

    private final String keycloakError;

    DevLoginClientException(String clientId, String keycloakError) {
        super("Keycloak refused client '" + clientId + "': " + keycloakError);
        this.keycloakError = keycloakError;
    }

    String keycloakError() {
        return this.keycloakError;
    }
}
