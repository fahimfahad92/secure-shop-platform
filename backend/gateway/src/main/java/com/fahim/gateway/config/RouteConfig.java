package com.fahim.gateway.config;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.removeRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.TokenRelayFilterFunctions.tokenRelay;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.method;
import static org.springframework.web.servlet.function.RequestPredicates.methods;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Proxy routes, evaluated in declaration order. Access rules are in {@link SecurityConfig}; this
 * only decides where a request goes and whether the session's token travels with it.
 *
 * <p>Every route strips the browser's {@code Cookie} and CSRF header: they mean nothing to the
 * services, and the session cookie should not leave the Gateway.
 */
@Configuration
public class RouteConfig {

    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Bean
    public RouterFunction<ServerResponse> gatewayRoutes(
            @Value("${product-service.base-url}") String productServiceUrl,
            @Value("${user-service.base-url}") String userServiceUrl,
            @Value("${order-service.base-url}") String orderServiceUrl) {
        return publicRoute("product-read", HttpMethod.GET, "/products/**", productServiceUrl)
                .and(
                        relayRoute(
                                "product-write",
                                path("/products/**")
                                        .and(
                                                methods(
                                                        HttpMethod.POST,
                                                        HttpMethod.PUT,
                                                        HttpMethod.DELETE)),
                                productServiceUrl))
                .and(
                        publicRoute(
                                "user-register",
                                HttpMethod.POST,
                                "/users/register",
                                userServiceUrl))
                .and(relayRoute("users", path("/users/**"), userServiceUrl))
                .and(relayRoute("orders", path("/orders/**"), orderServiceUrl));
    }

    private static RouterFunction<ServerResponse> publicRoute(
            String id, HttpMethod httpMethod, String pattern, String serviceUrl) {
        return route(id)
                .route(path(pattern).and(method(httpMethod)), http())
                .before(uri(serviceUrl))
                .before(removeRequestHeader(HttpHeaders.COOKIE))
                .before(removeRequestHeader(CSRF_HEADER))
                .build();
    }

    private static RouterFunction<ServerResponse> relayRoute(
            String id, RequestPredicate predicate, String serviceUrl) {
        return route(id)
                .route(predicate, http())
                .before(uri(serviceUrl))
                .before(removeRequestHeader(HttpHeaders.COOKIE))
                .before(removeRequestHeader(CSRF_HEADER))
                .filter(tokenRelay())
                // Refresh token expired or revoked: the session can't get a token any more.
                // That is "log in again" (401), not a server error.
                .onError(ClientAuthorizationException.class, RouteConfig::sessionExpired)
                .build();
    }

    private static ServerResponse sessionExpired(Throwable error, ServerRequest request) {
        return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
    }
}
