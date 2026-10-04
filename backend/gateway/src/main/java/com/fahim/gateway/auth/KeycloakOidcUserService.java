package com.fahim.gateway.auth;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Adds Keycloak realm roles to the logged-in user. Keycloak puts them in the access token only, not
 * the ID token or userinfo, so the default user service never sees them.
 */
public class KeycloakOidcUserService extends OidcUserService {

    public static final String USERNAME_CLAIM = "preferred_username";

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser user = super.loadUser(userRequest);
        Set<GrantedAuthority> authorities = new LinkedHashSet<>(user.getAuthorities());
        authorities.addAll(
                KeycloakRoles.fromAccessToken(userRequest.getAccessToken().getTokenValue()));
        return new DefaultOidcUser(
                authorities, user.getIdToken(), user.getUserInfo(), USERNAME_CLAIM);
    }
}
