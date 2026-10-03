package com.fahim.orderservice.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Maps Keycloak's {@code realm_access.roles} claim to Spring Security {@code ROLE_*} authorities.
 *
 * <p>Spring Security's default converter only reads the {@code scope}/{@code scp} claim, so realm
 * roles are invisible to {@code hasRole(...)} without this. Scope authorities are still produced
 * too, so both {@code SCOPE_*} and {@code ROLE_*} checks work.
 */
class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";

    private final JwtGrantedAuthoritiesConverter scopeConverter =
            new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>(scopeConverter.convert(jwt));
        realmRoles(jwt)
                .forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        return authorities;
    }

    private Collection<String> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null) {
            return List.of();
        }
        Object roles = realmAccess.get(ROLES_CLAIM);
        if (!(roles instanceof Collection<?> rawRoles)) {
            return List.of();
        }
        return rawRoles.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }
}
