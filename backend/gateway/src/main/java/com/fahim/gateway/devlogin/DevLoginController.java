package com.fahim.gateway.devlogin;

import com.fahim.gateway.auth.KeycloakOidcUserService;
import com.fahim.gateway.auth.KeycloakRoles;
import com.fahim.gateway.auth.MeResponse;
import com.fahim.gateway.config.SecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /auth/dev-login}: username + password in, Gateway session out, so Postman can test
 * without a browser. The session is the same shape a browser login produces (OIDC user in the
 * security context, authorized client in the session), so token relay, refresh, {@code /auth/me}
 * and logout behave identically. Only the response body and cookies reach the caller; never a
 * token.
 */
@RestController
@ConditionalOnDevLogin
class DevLoginController {

    private final KeycloakPasswordGrant passwordGrant;

    private final OAuth2AuthorizedClientRepository authorizedClients;

    private final JwtDecoderFactory<ClientRegistration> idTokenDecoders =
            new OidcIdTokenDecoderFactory();

    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    DevLoginController(
            KeycloakPasswordGrant passwordGrant,
            OAuth2AuthorizedClientRepository authorizedClients) {
        this.passwordGrant = passwordGrant;
        this.authorizedClients = authorizedClients;
    }

    @PostMapping(SecurityConfig.DEV_LOGIN_PATH)
    MeResponse login(
            @Valid @RequestBody DevLoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        ClientRegistration registration = this.passwordGrant.registration();
        OAuth2AccessTokenResponse tokens =
                this.passwordGrant.requestTokens(body.username(), body.password());
        OidcIdToken idToken = validateIdToken(registration, tokens);

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new OidcUserAuthority(idToken));
        authorities.addAll(KeycloakRoles.fromAccessToken(tokens.getAccessToken().getTokenValue()));
        OidcUser user =
                new DefaultOidcUser(authorities, idToken, KeycloakOidcUserService.USERNAME_CLAIM);
        OAuth2AuthenticationToken authentication =
                new OAuth2AuthenticationToken(user, authorities, registration.getRegistrationId());

        startSession(authentication, request, response);
        this.authorizedClients.saveAuthorizedClient(
                new OAuth2AuthorizedClient(
                        registration,
                        authentication.getName(),
                        tokens.getAccessToken(),
                        tokens.getRefreshToken()),
                authentication,
                request,
                response);
        return MeResponse.from(user);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Map<String, String>> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "invalid username or password"));
    }

    /** The Gateway's own client was refused: a config problem, so 502 and no blame on the user. */
    @ExceptionHandler(DevLoginClientException.class)
    ResponseEntity<Map<String, String>> clientRefused(DevLoginClientException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(
                        Map.of(
                                "error",
                                "Keycloak refused the Gateway's dev login client ("
                                        + ex.keycloakError()
                                        + "). Check that KEYCLOAK_TEST_CLIENT_SECRET matches"
                                        + " secure-shop-test-client's secret in Keycloak."));
    }

    /** Same checks a browser login applies: signature, issuer, audience, expiry. */
    private OidcIdToken validateIdToken(
            ClientRegistration registration, OAuth2AccessTokenResponse tokens) {
        Object idTokenValue = tokens.getAdditionalParameters().get(OidcParameterNames.ID_TOKEN);
        if (!(idTokenValue instanceof String value)) {
            throw new IllegalStateException(
                    "Keycloak returned no id_token; the dev registration must request 'openid'");
        }
        Jwt jwt = this.idTokenDecoders.createDecoder(registration).decode(value);
        return new OidcIdToken(
                jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getClaims());
    }

    private void startSession(
            OAuth2AuthenticationToken authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        // A fresh session id on login, as a normal login does (session fixation).
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        this.securityContextRepository.saveContext(context, request, response);
    }
}
