package com.fahim.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One local HTTP server standing in for both the downstream services and Keycloak, recording every
 * request it receives so tests can assert on exactly what the Gateway forwarded.
 *
 * <ul>
 *   <li>{@code /kc/token}: Keycloak token endpoint, answers with {@link #keycloakTokenResponse}
 *   <li>{@code /kc/certs}: Keycloak JWKS, serves {@link TestKeys}' public key
 *   <li>{@code /kc/logout}: Keycloak end-session endpoint, answers 204
 *   <li>anything else: a downstream service, answers 200 with a small JSON body
 * </ul>
 */
final class StubServer {

    record RecordedRequest(
            String method, String path, Map<String, List<String>> headers, String body) {

        String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(entry -> entry.getValue().getFirst())
                    .findFirst()
                    .orElse(null);
        }
    }

    record StubResponse(int status, String body) {}

    static final StubServer INSTANCE = new StubServer();

    private final HttpServer server;

    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

    private volatile StubResponse keycloakTokenResponse = new StubResponse(500, "{}");

    private StubServer() {
        try {
            this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    String baseUrl() {
        return "http://localhost:" + this.server.getAddress().getPort();
    }

    String keycloakUrl() {
        return baseUrl() + "/kc";
    }

    void reset() {
        this.requests.clear();
        this.keycloakTokenResponse = new StubResponse(500, "{}");
    }

    void keycloakTokenResponse(int status, String body) {
        this.keycloakTokenResponse = new StubResponse(status, body);
    }

    List<RecordedRequest> requests() {
        return List.copyOf(this.requests);
    }

    List<RecordedRequest> requestsTo(String path) {
        return this.requests.stream().filter(request -> request.path().equals(path)).toList();
    }

    RecordedRequest lastRequest() {
        return this.requests.getLast();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        this.requests.add(
                new RecordedRequest(
                        exchange.getRequestMethod(),
                        path,
                        Map.copyOf(exchange.getRequestHeaders()),
                        body));
        switch (path) {
            case "/kc/token" ->
                    respond(
                            exchange,
                            this.keycloakTokenResponse.status(),
                            this.keycloakTokenResponse.body());
            case "/kc/certs" -> respond(exchange, 200, TestKeys.jwkSetJson());
            case "/kc/logout" -> respond(exchange, 204, null);
            default -> respond(exchange, 200, "{\"stub\":\"" + path + "\"}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
