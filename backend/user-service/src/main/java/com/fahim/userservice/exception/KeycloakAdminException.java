package com.fahim.userservice.exception;

/** Keycloak's Admin API was unreachable or answered with an unexpected status. */
public class KeycloakAdminException extends RuntimeException {

    public KeycloakAdminException(String message) {
        super(message);
    }

    public KeycloakAdminException(String message, Throwable cause) {
        super(message, cause);
    }
}
