package com.fahim.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keycloak.admin")
public record KeycloakAdminProperties(
        String baseUrl,
        String realm,
        String clientId,
        String clientSecret,
        int connectTimeoutMs,
        int readTimeoutMs) {

    public String tokenUri() {
        return "/realms/" + realm + "/protocol/openid-connect/token";
    }

    public String usersUri() {
        return "/admin/realms/" + realm + "/users";
    }
}
