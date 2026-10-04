package com.fahim.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "keycloak.admin")
public record KeycloakAdminProperties(
        String baseUrl,
        String realm,
        String clientId,
        String clientSecret,
        int connectTimeoutMs,
        int readTimeoutMs) {

    /**
     * Fails binding, and so startup, when the secret is missing. Boot binds an unresolvable {@code
     * ${KEYCLOAK_ADMIN_CLIENT_SECRET}} as literal text rather than failing, so without this the
     * service starts and only the first registration breaks, with Keycloak rejecting the
     * placeholder string as the secret.
     */
    public KeycloakAdminProperties {
        if (!StringUtils.hasText(clientSecret) || clientSecret.contains("${")) {
            throw new IllegalStateException(
                    "keycloak.admin.client-secret is not set. Set KEYCLOAK_ADMIN_CLIENT_SECRET"
                            + " (see docker/.env) and restart. An IDE only sees environment"
                            + " variables that existed when the IDE itself was started.");
        }
    }

    public String tokenUri() {
        return "/realms/" + realm + "/protocol/openid-connect/token";
    }

    public String usersUri() {
        return "/admin/realms/" + realm + "/users";
    }
}
