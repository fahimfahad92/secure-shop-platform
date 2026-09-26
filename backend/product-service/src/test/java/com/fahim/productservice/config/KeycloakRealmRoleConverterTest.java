package com.fahim.productservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    @Test
    void mapsRealmRolesToRoleAuthorities() {
        Jwt jwt =
                jwt(
                        Map.of(
                                "realm_access",
                                Map.of("roles", List.of("product-admin", "offline_access"))));

        assertThat(authorities(jwt)).contains("ROLE_product-admin", "ROLE_offline_access");
    }

    @Test
    void keepsScopeAuthoritiesAlongsideRoles() {
        Jwt jwt =
                jwt(
                        Map.of(
                                "scope",
                                "orders:read orders:write",
                                "realm_access",
                                Map.of("roles", List.of("product-admin"))));

        assertThat(authorities(jwt))
                .contains("SCOPE_orders:read", "SCOPE_orders:write", "ROLE_product-admin");
    }

    @Test
    void tokenWithoutRealmAccess_yieldsNoRoleAuthorities() {
        Jwt jwt = jwt(Map.of("scope", "orders:read"));

        assertThat(authorities(jwt)).containsExactly("SCOPE_orders:read");
    }

    @Test
    void realmAccessWithoutRolesEntry_yieldsNoRoleAuthorities() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("not-roles", "ignored")));

        assertThat(authorities(jwt)).isEmpty();
    }

    private List<String> authorities(Jwt jwt) {
        return converter.convert(jwt).stream().map(GrantedAuthority::getAuthority).toList();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder =
                Jwt.withTokenValue("token").header("alg", "none").subject("test-user");
        claims.forEach(builder::claim);
        return builder.build();
    }
}
