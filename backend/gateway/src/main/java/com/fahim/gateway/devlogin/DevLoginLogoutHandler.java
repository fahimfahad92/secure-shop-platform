package com.fahim.gateway.devlogin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/**
 * On {@code POST /logout} of a dev-login session, ends the Keycloak session too. Runs before the
 * Gateway session is invalidated, while the refresh token can still be read from it.
 */
@Component
@ConditionalOnDevLogin
public class DevLoginLogoutHandler implements LogoutHandler {

    private final KeycloakPasswordGrant passwordGrant;

    private final OAuth2AuthorizedClientRepository authorizedClients;

    DevLoginLogoutHandler(
            KeycloakPasswordGrant passwordGrant,
            OAuth2AuthorizedClientRepository authorizedClients) {
        this.passwordGrant = passwordGrant;
        this.authorizedClients = authorizedClients;
    }

    @Override
    public void logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)
                || !KeycloakPasswordGrant.REGISTRATION_ID.equals(
                        token.getAuthorizedClientRegistrationId())) {
            return;
        }
        OAuth2AuthorizedClient client =
                this.authorizedClients.loadAuthorizedClient(
                        KeycloakPasswordGrant.REGISTRATION_ID, authentication, request);
        if (client != null) {
            this.passwordGrant.endSession(client.getRefreshToken());
        }
    }
}
