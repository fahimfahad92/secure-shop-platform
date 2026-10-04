package com.fahim.gateway.auth;

import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** Who is logged in. Deliberately has no token fields: tokens never leave the Gateway. */
public record MeResponse(
        String username, String sub, String name, String email, List<String> roles) {

    private static final String ROLE_PREFIX = "ROLE_";

    public static MeResponse from(OidcUser user) {
        List<String> roles =
                user.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(authority -> authority.startsWith(ROLE_PREFIX))
                        .map(authority -> authority.substring(ROLE_PREFIX.length()))
                        .sorted()
                        .toList();
        return new MeResponse(
                user.getName(), user.getSubject(), user.getFullName(), user.getEmail(), roles);
    }
}
