# Gateway

The single entry point for every client. It logs users in against Keycloak, keeps their tokens in the server-side session, and forwards API calls to the services with the user's access token attached. The browser only ever gets a session cookie.

| | |
|---|---|
| Port | `8090` |
| Code | `backend/gateway` |
| Stack | Spring Cloud Gateway Server Web MVC (servlet, not reactive), Spring Security OAuth2 Client |
| Keycloak client | `secure-shop-gateway` (Authorization Code + PKCE). `secure-shop-test-client` for dev login only |
| State | HTTP session: security context and the user's tokens |
| Database | None |

## Endpoints

| Method | Path | Needs session | What it does |
|---|---|---|---|
| `GET` | `/oauth2/authorization/keycloak` | No | Starts browser login (redirect to Keycloak) |
| `GET` | `/login/oauth2/code/keycloak` | No | Keycloak redirects back here with the code |
| `GET` | `/auth/me` | Yes | Who is logged in: username, `sub`, name, email, roles. Never a token |
| `POST` | `/logout` | Yes | Ends the Gateway session and the Keycloak session |
| `GET` | `/` | No | Landing page after logout, lists the login and `/auth/me` links |
| `POST` | `/auth/dev-login` | No | Dev profile only. Username + password in, session out |

### Proxied routes

Checked in order, first match wins.

| Route | Match | Goes to | Session | Token sent |
|---|---|---|---|---|
| `product-read` | `GET /products/**` | Product `:8082` | Not needed | No |
| `product-write` | `POST`/`PUT`/`DELETE /products/**` | Product | Required | Yes |
| `user-register` | `POST /users/register` | User `:8083` | Not needed | No |
| `users` | `/users/**` | User | Required | Yes |
| `orders` | `/orders/**` | Order `:8080` | Required | Yes |
| anything else | | | Denied | |

Paths are forwarded unchanged: `/orders/5` on the Gateway is `/orders/5` on Order Service.

## How security works

The Gateway's only rule is "is there a logged-in session". It does not check scopes, roles or ownership. Each service does that on the token it receives, so a service called directly applies the same rules.

<details>
<summary>Login (Authorization Code + PKCE)</summary>

Sequence diagram: [architecture.md → Browser login](../architecture.md#browser-login).

- Spring Security adds PKCE on its own only for public clients. `secure-shop-gateway` is confidential and Keycloak requires `S256` for it, so `SecurityConfig` switches PKCE on explicitly (`OAuth2AuthorizationRequestCustomizers.withPkce()`).
- Only `openid` is requested. Keycloak adds the client's default scopes (`profile`, `email`, `roles`, `orders:read`, `orders:write`, `secure-shop-audience`).
- Keycloak puts realm roles in the access token only. `KeycloakOidcUserService` reads them from there (`KeycloakRoles`) and adds them to the logged-in user as `ROLE_user` / `ROLE_admin`. They're used for `/auth/me` display only. The Gateway never authorizes on them.
- After login the browser always lands on `/auth/me`.
- A failed login answers `401` JSON with the OAuth2 error and logs it at WARN (`LoginFailureHandler`). Spring's default would redirect to a `/login?error` page this Gateway doesn't have. When Keycloak rejected the Gateway's own client secret, the response adds a hint.

</details>

<details>
<summary>Session and cookies</summary>

The browser holds two cookies and nothing else.

| Cookie | HttpOnly | Purpose |
|---|---|---|
| `JSESSIONID` | Yes, `SameSite=Lax` | The session. This is the user's credential |
| `XSRF-TOKEN` | No (JS must read it) | CSRF token, echoed back in a header |

- Tokens are stored in the session (`HttpSessionOAuth2AuthorizedClientRepository`). Boot's default stores them in an in-memory map keyed by username, which outlives the session and is shared between a user's sessions.
- The session id changes on login (session fixation protection).
- API calls without a session get `401`, never a redirect to Keycloak's login page. A `fetch` can't do anything useful with an HTML login page.

</details>

<details>
<summary>Token relay and refresh</summary>

On every route marked "Token sent" above, `tokenRelay()`:

1. Loads the user's tokens from the session.
2. If the access token has expired, or expires within 60 seconds, uses the refresh token to get new ones from Keycloak and saves them in the session.
3. Adds `Authorization: Bearer <access token>` to the forwarded request.

If the refresh fails (refresh token expired, Keycloak session ended), the route answers `401`. The user has to log in again.

`authorizedClientManager` in `SecurityConfig` is what does the refresh. Neither Boot nor the Gateway defines one, so without it there would be no refresh.

Every route also removes the `Cookie` and `X-XSRF-TOKEN` headers before forwarding. The session cookie is a credential and must not leave the Gateway.

</details>

<details>
<summary>CSRF</summary>

The browser sends the session cookie automatically, even on a request another site triggers. So every write through the Gateway needs proof it came from our own page: the `XSRF-TOKEN` cookie's value copied into an `X-XSRF-TOKEN` header. Another site can't read our cookie, so it can't build the header.

- Missing or wrong token on a write: `403`, nothing is forwarded.
- Exempt: `POST /users/register` and `POST /auth/dev-login`. There is no session yet to abuse.
- `POST /logout` needs the token too, so another site can't log you out.
- `SpaCsrfTokenRequestHandler` is the Spring Security reference setup for JavaScript clients. It accepts the raw token from the header (the cookie holds the raw value) and the masked one from forms. It also loads the token on every request so the cookie exists before the first write.

The services keep CSRF off. They only accept bearer tokens, which a browser never attaches by itself.

</details>

<details>
<summary>Logout</summary>

`POST /logout` with the CSRF header:

| Logged in via | What happens | Response |
|---|---|---|
| Browser | Session invalidated, browser redirected to Keycloak's end-session endpoint with `id_token_hint`, then back to `http://localhost:8090/` | `302` |
| Dev login | Session invalidated, the Gateway calls Keycloak's end-session endpoint itself with the refresh token (`DevLoginLogoutHandler`) | `204` |

Ending the Keycloak session matters. Otherwise the Keycloak SSO cookie survives and the next login goes through without asking for a password.

The post-logout URI `http://localhost:8090/` must match the one registered on the `secure-shop-gateway` client exactly.

</details>

<details>
<summary>Fail-fast checks at startup</summary>

- `ClientSecretCheck` stops startup if a client secret is empty or still an unresolved `${...}` placeholder. Without it the Gateway would start and only fail at the first login, with Keycloak answering `401`. The error names the environment variable to set.
- Keycloak must be running before the Gateway starts. Boot reads Keycloak's discovery document (`issuer-uri`) at startup.

</details>

## Dev login

A real login needs a browser for Keycloak's form. For Postman and curl there's `POST /auth/dev-login`, which exists **only** with the `dev` profile.

```bash
# log in, keep cookies in a jar
curl -c jar -b jar -X POST http://localhost:8090/auth/dev-login \
  -H 'Content-Type: application/json' -d '{"username":"testuser","password":"test"}'

# a write needs the CSRF cookie value in a header
curl -b jar -c jar -X POST http://localhost:8090/orders \
  -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: $(grep XSRF-TOKEN jar | awk '{print $7}')" \
  -d '{"productId":1,"quantity":2}'
```

<details>
<summary>How it works and why it's safe enough</summary>

- It runs a password grant against `secure-shop-test-client`, never against `secure-shop-gateway`, which stays Authorization Code only.
- It validates the ID token the same way a browser login does (signature, issuer, audience, expiry), then builds the same session: an OIDC user in the security context and the tokens in the session. Relay, refresh, `/auth/me` and logout all behave the same as after a browser login.
- Refreshes for a dev session go through `keycloak-test`, because a refresh token only works for the client that issued it.
- Wrong password: `401`. Keycloak refused the Gateway's dev client (bad secret): `502` with a hint, because that's a config problem, not the user's.
- Without the `dev` profile the beans don't exist and the path answers `404`. With it, the Gateway logs a `WARN` at startup.
- It uses a plain `RestClient` for the password grant. Spring Security's own password grant support is deprecated in 6.x and removed in 7.

</details>

## Configuration

<details>
<summary>Properties</summary>

`application.properties`:

| Property | Value | Note |
|---|---|---|
| `server.port` | `8090` | |
| `...registration.keycloak.client-id` | `secure-shop-gateway` | The registration id `keycloak` is part of the redirect URI, don't rename it |
| `...registration.keycloak.client-secret` | `${KEYCLOAK_GATEWAY_CLIENT_SECRET}` | From `docker/.env` |
| `...registration.keycloak.scope` | `openid` | |
| `...provider.keycloak.issuer-uri` | `http://localhost:8081/realms/secure-shop` | |
| `...provider.keycloak.user-name-attribute` | `preferred_username` | |
| `server.servlet.session.cookie.http-only` | `true` | |
| `server.servlet.session.cookie.same-site` | `lax` | |
| `order-service.base-url` etc. | `http://localhost:8080` / `8082` / `8083` | Route targets |

`application-dev.properties` adds `secure-shop.dev-login.enabled=true` and the `keycloak-test` registration (`secure-shop-test-client`, password grant, `${KEYCLOAK_TEST_CLIENT_SECRET}`).

</details>

<details>
<summary>Code map</summary>

| File | Job |
|---|---|
| `config/SecurityConfig` | Access rules, login, PKCE, CSRF, logout, token storage, refresh |
| `config/RouteConfig` | The five routes, header stripping, token relay |
| `config/SpaCsrfTokenRequestHandler` | CSRF for JavaScript clients |
| `config/LoginFailureHandler` | JSON `401` and a log line for failed logins |
| `config/ClientSecretCheck` | Startup check for client secrets |
| `auth/KeycloakOidcUserService` | Adds realm roles to the logged-in user |
| `auth/KeycloakRoles` | Reads `realm_access.roles` from the access token |
| `auth/AuthController`, `auth/MeResponse` | `/auth/me` and `/` |
| `devlogin/*` | Dev login and its logout, only with the `dev` profile |

</details>

## Tests

<details>
<summary>How the tests work</summary>

```bash
cd backend/gateway && ./mvnw verify
```

No Keycloak and no Docker needed. One local stub HTTP server (`StubServer`) plays both Keycloak (token, JWKS, end-session) and the three services. The tests check what the Gateway actually forwarded:

- bearer token present on relayed routes, absent on public ones
- `Cookie` and CSRF headers stripped
- PKCE parameters on the login redirect
- no token in any response body
- `401` without a session, `403` on a write without a CSRF token

Client registrations come from a test bean instead of `issuer-uri`, because with `issuer-uri` Boot runs OIDC discovery at startup.

</details>

## Try it

Postman: the **Gateway (BFF)** folder in [`postman/secure-shop-platform.postman_collection.json`](../../postman/secure-shop-platform.postman_collection.json) runs dev login, `/auth/me`, a public product browse, placing and listing orders, a missing-CSRF `403`, and logout.
