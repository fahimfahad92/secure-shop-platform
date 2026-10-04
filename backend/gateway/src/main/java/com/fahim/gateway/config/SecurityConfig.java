package com.fahim.gateway.config;

import com.fahim.gateway.auth.KeycloakOidcUserService;
import com.fahim.gateway.devlogin.DevLoginLogoutHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * The Gateway's only access rule is "is there a logged-in session". Scopes, roles, audience and
 * ownership are checked by each service on the relayed token.
 */
@Configuration
public class SecurityConfig {

    /** Browser login registration; must match the redirect URI registered in Keycloak. */
    public static final String BROWSER_REGISTRATION_ID = "keycloak";

    public static final String DEV_LOGIN_PATH = "/auth/dev-login";

    private static final String REGISTER_PATH = "/users/register";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository clientRegistrations,
            ObjectProvider<DevLoginLogoutHandler> devLoginLogoutHandler)
            throws Exception {
        http.authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(HttpMethod.GET, "/products/**")
                                        .permitAll()
                                        // A new user has no session yet, so sign-up must be open.
                                        .requestMatchers(HttpMethod.POST, REGISTER_PATH)
                                        .permitAll()
                                        // Only has a handler when dev login is enabled; 404
                                        // otherwise.
                                        .requestMatchers(HttpMethod.POST, DEV_LOGIN_PATH)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/")
                                        .permitAll()
                                        .requestMatchers("/error")
                                        .permitAll()
                                        .requestMatchers(
                                                "/products/**",
                                                "/orders/**",
                                                "/users/**",
                                                "/auth/me")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .oauth2Login(
                        login ->
                                login.authorizationEndpoint(
                                                endpoint ->
                                                        endpoint.authorizationRequestResolver(
                                                                pkceAuthorizationRequestResolver(
                                                                        clientRegistrations)))
                                        .userInfoEndpoint(
                                                userInfo ->
                                                        userInfo.oidcUserService(
                                                                new KeycloakOidcUserService()))
                                        .defaultSuccessUrl("/auth/me", true)
                                        .failureHandler(new LoginFailureHandler()))
                // API callers get 401, not a 302 to Keycloak's login page. Login starts only when
                // a browser goes to /oauth2/authorization/keycloak on purpose.
                .exceptionHandling(
                        exceptions ->
                                exceptions.authenticationEntryPoint(
                                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // The session cookie authenticates the browser, so writes need a CSRF token.
                // Register and dev login are exempt: there is no session yet to forge.
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(
                                                CookieCsrfTokenRepository.withHttpOnlyFalse())
                                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                                        .ignoringRequestMatchers(REGISTER_PATH, DEV_LOGIN_PATH))
                .logout(
                        logout -> {
                            devLoginLogoutHandler.ifAvailable(logout::addLogoutHandler);
                            logout.logoutSuccessHandler(logoutSuccessHandler(clientRegistrations));
                        });
        return http.build();
    }

    /**
     * Tokens live in the HTTP session and die with it. Boot's default keeps them in an in-memory
     * map keyed by username, which outlives the session and is shared by a user's sessions.
     */
    @Bean
    public OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    /**
     * Used by the token relay filter to fetch the session's access token, refreshing it with the
     * refresh token when it has expired. Neither Boot 3.5 nor the Gateway defines one.
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrations,
            OAuth2AuthorizedClientRepository authorizedClients) {
        OAuth2AuthorizedClientProvider provider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .authorizationCode()
                        .refreshToken()
                        .build();
        DefaultOAuth2AuthorizedClientManager manager =
                new DefaultOAuth2AuthorizedClientManager(clientRegistrations, authorizedClients);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    /**
     * Spring Security only adds PKCE on its own for public clients. secure-shop-gateway is
     * confidential and Keycloak requires S256 for it, so without this every login is rejected.
     */
    private OAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrations) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrations,
                        OAuth2AuthorizationRequestRedirectFilter
                                .DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(
                OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }

    /**
     * Browser sessions are sent on to Keycloak's end-session endpoint as well, otherwise the
     * Keycloak SSO cookie survives and the next login skips the password. Dev-login sessions have
     * no browser to redirect; their Keycloak session is ended server-side by {@link
     * DevLoginLogoutHandler}, and the caller just gets 204.
     */
    private LogoutSuccessHandler logoutSuccessHandler(
            ClientRegistrationRepository clientRegistrations) {
        OidcClientInitiatedLogoutSuccessHandler browserLogout =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrations);
        browserLogout.setPostLogoutRedirectUri("{baseUrl}/");
        LogoutSuccessHandler noContent =
                new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT);
        return (request, response, authentication) -> {
            if (authentication instanceof OAuth2AuthenticationToken token
                    && BROWSER_REGISTRATION_ID.equals(token.getAuthorizedClientRegistrationId())) {
                browserLogout.onLogoutSuccess(request, response, authentication);
            } else {
                noContent.onLogoutSuccess(request, response, authentication);
            }
        };
    }
}
