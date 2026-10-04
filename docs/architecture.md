# Architecture

How the parts fit together, who is trusted with what, and what happens on each kind of request.

## The parts

```mermaid
flowchart LR
    subgraph Client side
        B[Browser / Postman]
    end
    subgraph Edge
        G[Gateway :8090]
    end
    subgraph Internal network
        P[Product Service :8082]
        O[Order Service :8080]
        U[User Service :8083]
        DB[(Postgres :5432<br/>one schema per service)]
    end
    K[(Keycloak :8081<br/>realm secure-shop)]

    B -- "JSESSIONID + XSRF-TOKEN cookies" --> G
    G -- "Bearer access token" --> P & O & U
    O -- "GET /products/{id}<br/>no credentials" --> P
    G -. "Auth Code + PKCE,<br/>refresh, logout" .-> K
    U -. "Admin API<br/>(service account)" .-> K
    P & O & U --> DB
```

| Part | Holds | Trusts |
|---|---|---|
| Browser | Two cookies: `JSESSIONID` (HttpOnly) and `XSRF-TOKEN`. No tokens | Nothing. Treated as hostile |
| Gateway | The user's session, and inside it the access, refresh and ID tokens | Keycloak's tokens, after checking the ID token |
| Services | Nothing per user between requests (stateless) | Only a token whose signature, issuer, expiry and audience check out |
| Keycloak | Users, passwords, roles, sessions, signing keys | Its own admin config |
| Postgres | `product_schema`, `order_schema`, `user_schema` | Only the services |

Each service owns its schema and its Flyway migrations. No service reads another service's tables.

## Identity model

Three questions, three different answers in the token.

| Question | Answered by | Example | Checked by |
|---|---|---|---|
| What may this app do for the user? | Scope (`scope` claim) | `orders:read`, `orders:write` | Order Service |
| Who is this user? | Realm role (`realm_access.roles`) | `user`, `admin` | Order Service, Product Service |
| Which rows are theirs? | Subject (`sub`) | `7663bcb4-...` | Order Service, User Service |
| Is this token meant for me? | Audience (`aud`) | `order-service` | All three services |

<details>
<summary>Clients in Keycloak</summary>

A client in Keycloak is a caller, not an API. Adding a service adds an audience, never a new login client.

| Client | Used by | Grant | Notes |
|---|---|---|---|
| `secure-shop-gateway` | Gateway, real browser logins | Authorization Code + PKCE `S256` | Confidential (has a secret) |
| `secure-shop-test-client` | Gateway dev login, Postman, curl | Password (direct access grant) | Dev only, never in production |
| `user-service-admin-client` | User Service | Client Credentials | Service account with only `manage-users` and `view-users` |

</details>

<details>
<summary>Why roles are realm roles, not client scopes or client roles</summary>

`testuser` and `adminuser` log in through the same client. A scope is granted to the client, so every user of that client would get it, and "admin only" would mean nothing. A role belongs to the user, so it travels with them whichever client they use.

Realm roles rather than client roles because `admin` and `user` mean the same thing to every service. There is one `admin`, not one per service.

Every user gets `user` automatically through `default-roles-secure-shop`, so `/users/register` needs no role logic. Admins hold `admin` as well. An admin is still a user.

</details>

<details>
<summary>Why every token carries an audience</summary>

The `secure-shop-audience` client scope adds `order-service`, `product-service` and `user-service` to the `aud` claim of tokens issued to the two user-facing clients. Each service rejects a token that doesn't name it (`401`).

Without it, any valid token from the realm would be accepted everywhere, including tokens minted for some unrelated client. With it, a token is only accepted by the services it was issued for.

</details>

<details>
<summary>What each service checks</summary>

| Service | Endpoint | Needs | Fails with |
|---|---|---|---|
| Gateway | `/orders/**`, `/users/**`, writes on `/products/**`, `/auth/me` | A session | `401` |
| Product | `GET /products/**` | Nothing (but a token, if sent, must be valid) | `401` for a bad token |
| Product | `POST`, `PUT`, `DELETE` | Role `admin` | `401` / `403` |
| Order | `GET` | Scope `orders:read` and role `user` | `401` / `403` |
| Order | writes | Scope `orders:write` and role `user` | `401` / `403` |
| Order | `/orders/{id}` | The order's owner is the token's `sub` | `404` |
| User | `POST /users/register` | Nothing | |
| User | `/users/me` | Any valid token. The profile is looked up by `sub` | `401` |
| All three | every authenticated call | `aud` contains the service's own name | `401` |

</details>

## Request flows

### Browser login

The Gateway sends the browser to Keycloak, gets a one-time code back, and swaps it for tokens over a direct server-to-server call. The tokens stay in the Gateway.

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser
    participant G as Gateway
    participant K as Keycloak

    B->>G: GET /oauth2/authorization/keycloak
    G->>G: Make state, nonce, PKCE verifier. Save in session
    G-->>B: 302 to Keycloak with code_challenge
    B->>K: Login page, user enters username + password
    K-->>B: 302 to /login/oauth2/code/keycloak?code&state
    B->>G: GET /login/oauth2/code/keycloak?code&state
    G->>G: Check state matches the session
    G->>K: POST /token with code, code_verifier, client secret
    K-->>G: Access token, refresh token, ID token
    G->>G: Validate ID token (signature, issuer, audience, expiry, nonce)
    G->>K: GET /userinfo
    G->>G: Read realm roles, store tokens in session, new session id
    G-->>B: 302 to /auth/me, Set-Cookie JSESSIONID (HttpOnly)
```

<details>
<summary>What each check protects against</summary>

| Check | Stops |
|---|---|
| `state` | Login CSRF: an attacker making your browser finish their login |
| PKCE (`code_verifier`) | A stolen authorization code being used by someone else |
| Client secret on the token call | Anyone other than the Gateway swapping the code |
| `nonce` in the ID token | Replaying an old ID token |
| New session id after login | Session fixation |

The token call in step 8 is a direct HTTP call from the Gateway to Keycloak. The browser never sees the response.

</details>

### API call through the Gateway

The browser sends its cookie. The Gateway swaps it for the user's access token, refreshing it first if it has expired, and forwards the call.

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser
    participant G as Gateway
    participant K as Keycloak
    participant O as Order Service

    B->>G: POST /orders with cookie + X-XSRF-TOKEN header
    G->>G: Session exists? CSRF token matches?
    alt access token expired (or expires within 60 s)
        G->>K: POST /token with refresh token
        K-->>G: New access + refresh token, saved in session
    end
    G->>O: POST /orders, Authorization: Bearer (Cookie and CSRF headers removed)
    O->>O: Validate JWT: signature (JWKS), issuer, expiry, aud has order-service
    O->>O: Check scope orders:write and role user
    O-->>G: 201 Created
    G-->>B: 201 Created
```

<details>
<summary>Failure cases</summary>

| What went wrong | Who answers | Status |
|---|---|---|
| No session | Gateway | `401` (never a redirect, so API clients can react) |
| Write without a matching CSRF token | Gateway | `403`, nothing is forwarded |
| Refresh token expired or revoked | Gateway | `401`, log in again |
| Path the Gateway doesn't know | Gateway | `401` without a session, `403` with one (deny by default) |
| Token missing the service's audience | Service | `401` |
| Missing scope or role | Service | `403` |
| Someone else's order | Order Service | `404` |

</details>

### Browsing products (public)

`GET /products/**` needs no login. The Gateway forwards it without a token, and Product Service lets it through.

```mermaid
sequenceDiagram
    participant B as Browser
    participant G as Gateway
    participant P as Product Service
    B->>G: GET /products
    G->>P: GET /products (no token, no cookie)
    P-->>G: 200 product list
    G-->>B: 200 product list
```

### Placing an order

The client sends only a product id and a quantity. Order Service asks Product Service for the real price and stock before it saves anything.

```mermaid
sequenceDiagram
    autonumber
    participant G as Gateway
    participant O as Order Service
    participant P as Product Service
    participant DB as Postgres

    G->>O: POST /orders {productId, quantity} + Bearer token
    O->>P: GET /products/{id} (no credentials, internal call)
    alt product not found
        P-->>O: 404
        O-->>G: 400 Bad Request
    else Product Service down or erroring
        O-->>G: 503 Service Unavailable
    else quantity > stock
        P-->>O: 200 {price, stock}
        O-->>G: 409 Conflict
    else ok
        P-->>O: 200 {price, stock}
        O->>DB: Insert order: owner = token sub, price from catalog
        O-->>G: 201 Created
    end
```

<details>
<summary>Why the status codes are chosen this way</summary>

| Case | Status | Reason |
|---|---|---|
| Product does not exist | `400` | The bad input is the `productId` in the body. The `/orders` resource is fine, so `404` would mislead |
| Not enough stock | `409` | The request is valid but conflicts with current catalog state |
| Product Service unavailable | `503` | The order isn't wrong, it just can't be priced right now |

Stock is checked, not reserved. Placing an order does not reduce the product's stock.

</details>

### Registration

Registration writes to two systems that share no transaction: Keycloak (the account) and Postgres (the profile). If the second write fails, the first is undone.

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser
    participant G as Gateway
    participant U as User Service
    participant K as Keycloak
    participant DB as Postgres

    B->>G: POST /users/register (no session, no CSRF token needed)
    G->>U: Forward (no token)
    U->>K: POST /token, client_credentials (cached until 30 s before expiry)
    U->>K: POST /admin/realms/secure-shop/users
    K-->>U: 201, Location header ends with the new user's sub
    U->>DB: Insert profile keyed by sub
    alt insert failed
        U->>K: DELETE the user again (compensation)
        U-->>B: Error
    else ok
        U-->>B: 201 profile
    end
```

<details>
<summary>Details and edge cases</summary>

- The new user is not logged in after registering. They log in through the normal flow.
- The password goes straight to Keycloak and is never stored or logged by User Service.
- Username taken in Keycloak: `409`. Keycloak unreachable: `503`.
- If the compensating delete also fails, the user is left in Keycloak without a profile. User Service logs `ORPHANED KEYCLOAK USER <sub>` at ERROR. Nothing retries it. An outbox is the planned fix.
- `register` is not `@Transactional` on purpose. The Keycloak call can't take part in a database transaction, and the annotation would suggest it does.

</details>

### Logout

The Gateway ends its own session, then sends the browser to Keycloak so the Keycloak session ends too. Without the second step, the next login would skip the password.

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser
    participant G as Gateway
    participant K as Keycloak

    B->>G: POST /logout + X-XSRF-TOKEN
    G->>G: Invalidate session (tokens are gone with it)
    G-->>B: 302 to Keycloak end-session, with id_token_hint
    B->>K: End SSO session
    K-->>B: 302 to http://localhost:8090/
    B->>G: GET / (landing page)
```

A dev-login session (Postman) has no browser to redirect. There the Gateway calls Keycloak's end-session endpoint itself with the refresh token and answers `204`. See [gateway.md](services/gateway.md).

## Design decisions

<details>
<summary>BFF: tokens stay in the Gateway, not in the browser</summary>

A token in `localStorage` can be read by any script on the page: your code, every npm dependency, third-party widgets. If an XSS bug lets someone steal it, they can use it from their own machine until it expires.

With the BFF pattern the browser holds only an HttpOnly session cookie. XSS can still make requests while the victim's tab is open, but it can't take a token away.

The cost: the Gateway keeps state (sessions), it must handle CSRF because cookies are sent automatically, and it sits in the path of every call.

</details>

<details>
<summary>The Gateway only checks "is there a session"</summary>

Scopes, roles, audience and ownership are checked by each service on the token it receives. A service called directly, without the Gateway, applies exactly the same rules. The Gateway is not the security boundary for business rules; it's the boundary for "is this a logged-in browser".

</details>

<details>
<summary>Owner from the token, 404 for someone else's data</summary>

There is no `/orders?user=` or `/users/{id}`. The owner always comes from the token's `sub`.

For orders, ownership is part of the database query (`findByIdAndUserSub`). Another user's order looks exactly like an order that doesn't exist, so ids can't be probed. That is why it's `404`, not `403`.

</details>

<details>
<summary>Price comes from the catalog, never from the client</summary>

`CreateOrderRequest` has no price field. A client can't order a 99.99 keyboard for 0.01 because there is nowhere to send 0.01.

</details>

<details>
<summary>Realm as code</summary>

The realm is a committed file, `docker/keycloak/secure-shop-realm.json`, imported on first start. Client secrets in it are `${...}` placeholders filled from `docker/.env`, so no secret is committed. See [keycloak-setup.md](keycloak-setup.md).

</details>

<details>
<summary>Fail at startup on a missing secret</summary>

Spring Boot keeps an unresolved `${KEYCLOAK_..._CLIENT_SECRET}` as literal text instead of failing. The service would start and only break at the first login or registration, with a confusing `401` from Keycloak. The Gateway (`ClientSecretCheck`) and User Service (`KeycloakAdminProperties`) refuse to start instead, and the error names the variable to set.

</details>

## Next plans

<details>
<summary>What isn't built yet, and the plan for it</summary>

| Gap | Today | Plan |
|---|---|---|
| Service-to-service auth | Order Service calls Product Service with no credentials | Client Credentials or mTLS (project #03) |
| Back-channel logout | Logging out in Keycloak directly doesn't end the Gateway session | OIDC back-channel logout |
| Stock | Checked when ordering, never reduced | Reservation or decrement |
| Order status | The owner can set any status (`PENDING`, `CONFIRMED`, `CANCELLED`) through `PUT` | Status changes owned by the system, not the client |
| Registration abuse | No rate limit on `POST /users/register` | Rate limit at the Gateway |
| Gateway upstream calls | No timeouts or circuit breaker | Timeouts, `503` mapping |
| Refresh token rotation | Off in Keycloak | Turn on, after handling parallel refreshes |
| TLS | Plain HTTP on localhost | TLS and `Secure` cookies in any shared environment |

</details>
