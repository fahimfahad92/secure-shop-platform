package com.fahim.gateway.auth;

import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Reads Keycloak realm roles from an access token the Gateway just received from Keycloak's token
 * endpoint.
 *
 * <p>The signature is not checked here. The token arrived over the Gateway's own authenticated
 * back-channel call to Keycloak, not from a client, and the roles are only used for display ({@code
 * /auth/me}). Every service still validates the token in full before acting on it.
 */
public final class KeycloakRoles {

    private KeycloakRoles() {}

    public static List<GrantedAuthority> fromAccessToken(String accessToken) {
        try {
            Map<String, Object> realmAccess =
                    JWTParser.parse(accessToken)
                            .getJWTClaimsSet()
                            .getJSONObjectClaim("realm_access");
            if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
                return List.of();
            }
            return roles.stream()
                    .filter(String.class::isInstance)
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        } catch (ParseException ex) {
            return List.of();
        }
    }
}
