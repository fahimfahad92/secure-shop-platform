package com.fahim.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * A failed browser login (usually the code-for-token exchange after Keycloak redirects back) would
 * otherwise go to {@code /login?error}, a page this Gateway doesn't serve, with nothing logged.
 * This logs the OAuth2 error and answers 401 with it, plus a hint when Keycloak refused the
 * Gateway's own client credentials.
 */
final class LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginFailureHandler.class);

    private final ObjectMapper json = new ObjectMapper();

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        String code = "login_failed";
        String description = exception.getMessage();
        if (exception instanceof OAuth2AuthenticationException oauth2) {
            OAuth2Error error = oauth2.getError();
            code = error.getErrorCode();
            if (error.getDescription() != null) {
                description = error.getDescription();
            }
        }
        log.warn("Browser login failed: {} - {}", code, description);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("description", description);
        if (refusedClientCredentials(code, description)) {
            body.put(
                    "hint",
                    "Keycloak refused the Gateway's client. Check that"
                            + " KEYCLOAK_GATEWAY_CLIENT_SECRET matches secure-shop-gateway's"
                            + " secret in Keycloak.");
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        this.json.writeValue(response.getOutputStream(), body);
    }

    /**
     * The token endpoint answers 401 only when client authentication fails (RFC 6749 section 5.2,
     * {@code invalid_client}); a bad or expired code is a 400. Spring's default HTTP client drops
     * the body of a 401, so the status is the reliable signal, not Keycloak's error text.
     */
    private static boolean refusedClientCredentials(String code, String description) {
        if (description == null) {
            return false;
        }
        return description.contains("invalid_client")
                || description.contains("unauthorized_client")
                || ("invalid_token_response".equals(code) && description.contains("401"));
    }
}
