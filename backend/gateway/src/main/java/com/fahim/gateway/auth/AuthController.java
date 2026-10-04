package com.fahim.gateway.auth;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthController {

    /** "Am I logged in, and as whom?" for Postman and, later, the frontend. */
    @GetMapping("/auth/me")
    MeResponse me(@AuthenticationPrincipal OidcUser user) {
        return MeResponse.from(user);
    }

    /**
     * Landing page after logout: Keycloak's post-logout redirect URI for the Gateway client is
     * {@code http://localhost:8090/}.
     */
    @GetMapping("/")
    Map<String, String> home() {
        return Map.of(
                "service", "secure-shop-gateway",
                "login", "/oauth2/authorization/keycloak",
                "me", "/auth/me");
    }
}
