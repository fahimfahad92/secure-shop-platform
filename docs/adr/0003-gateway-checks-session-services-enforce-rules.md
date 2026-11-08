# 0003. The Gateway checks only "is there a session"; services enforce the rules

| | |
|---|---|
| Status | Accepted |
| Date | 2026-10-24 (recorded). Decided 2026-10-04, #01 Phase 4, PR #5 |
| Category | security |
| Impact | high |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5, retroactive) |
| Drivers | T8, T13, T40; trust boundary TB7 (service ports reachable directly) |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

With a Gateway in front ([0002](0002-bff-tokens-stay-in-the-gateway.md)), authorization rules could live in two places: at the edge, per route, or in each service, on the token it receives. Rules split between both drift apart, and rules only at the edge disappear for any caller who reaches a service directly.

Today every service port is reachable on the local network, bypassing the Gateway (TB7). In the intended deployment only the Gateway is published, but that is not true until step 3.

Which rules does the Gateway enforce, and which belong to the services?

## Decision drivers

- A service called directly must apply exactly the same rules as one called through the Gateway
- Ownership checks need data only the service has (which order belongs to whom)
- One place per rule, so rules can't drift
- Each service testable on its own, with tokens only
- Deny by default at the edge for paths nobody planned

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **Gateway checks only "logged in"; each service checks audience plus its own scopes, roles and ownership on the token** | Services are safe when called directly; every rule in one place; tests per service | Each service carries security config; unauthorized calls reach services before being refused; a service's rules have no second layer behind them |
| Gateway enforces roles and scopes per route; services trust the Gateway (identity in headers such as `X-User-Id`) | One security config; services are plain | Anyone reaching a service port can forge the header; ownership still has to be in the service; the Gateway has to know every service's rules |
| Both: Gateway enforces coarse role/scope rules per route, services enforce all rules | A second layer: if a service's `SecurityConfig` regresses (say `anyRequest().permitAll()`), the edge still refuses Gateway traffic; unauthorized calls are shed before they reach a service | Every role/scope rule written twice, and the copies drift; the Gateway has to know every service's rules, so a rule change touches two deployables; the second layer only covers Gateway traffic, so direct callers skip it: external ones while service ports are reachable (TB7), internal ones even after step 3; ownership can't be checked at the edge at all |

## Decision outcome

**Chosen: the Gateway authenticates the browser session; each service authorizes the token.**

Gateway rules (`gateway/.../config/SecurityConfig.java`):

- Public: `GET /products/**`, `POST /users/register`, `GET /`, `/error`, and `POST /auth/dev-login`. The dev-login matcher is `permitAll` in every profile; without the flag no handler exists and the call gets `404` ([0010](0010-dev-login-password-grant-behind-a-flag.md))
- Session required: other methods on `/products/**`, `/orders/**`, `/users/**`, `/auth/me`
- Anything else: `denyAll`
- No API call gets a redirect to Keycloak: a missing session is a `401`

Service rules, on the relayed JWT. All three services check signature, issuer, expiry and `aud` (their own name). On top of that, each checks only what its endpoints need:

| Service | Authorization rules |
|---|---|
| Order | `orders:read` (GET) or `orders:write` (anything else) scope **and** realm role `user`; ownership by `sub` |
| Product | `GET /products/**` public; everything else needs realm role `admin` |
| User | `POST /users/register` public; everything else any authenticated token with its audience; ownership by `sub` |

User Service checks no role or scope on purpose: its only protected endpoints are `/users/me` (GET, PUT, DELETE), and they act on the caller's own `sub` ([0004](0004-owner-from-token-sub-404-for-others.md)). A token holder can only reach their own profile, so a role check would add nothing.

A non-admin with a session is forwarded on `POST /products`; Product Service answers `403`. That is the intended split, not a gap.

Header-based identity was rejected because it is forgeable while service ports are reachable. "Both" was rejected on cost, not on value. It does buy a second layer against a regressed service config, but:

- **It never sees internal callers.** Step 3 stops publishing service ports outside the network; it does not route east-west traffic through the Gateway. Order → Product ([0009](0009-order-to-product-unauthenticated-in-v1.md)) and any future service-account caller reach services directly, before and after step 3
- **It moves the discipline, it doesn't remove it.** An edge role rule covers only routes someone wrote a matcher for, so a new endpoint needs a second rule in a second deployable. The Gateway's `denyAll` on unknown paths is the coarse edge check worth having, and it exists
- **Neither authorization bug is visible at the edge.** A permissive wildcard matcher (a new `GET /products/admin/stats` under `GET /products/**`) and a missing ownership filter (a new `GET /orders/all` that forgets `sub`) both arrive with a valid session and a valid role. A coarse edge check forwards them

The guarantee belongs in the service, as mechanisms rather than discipline: fail-closed defaults, exact matchers, and tests that cover every route without anyone adding them. Today only Product fails closed (`anyRequest()` needs `admin`). Order's default (`orders:write` + `user`) looks strict but is held by every logged-in user: `orders:read` and `orders:write` are realm default client scopes, and `default-roles-secure-shop` includes `user` ([0011](0011-realm-roles-and-per-service-audience.md)). So Order fails open for new endpoints, like User Service. The rest is scheduled below.

### Scheduled hardening (step 2, review findings)

Decided here, implemented in step 2 of the build sequence, after #05. Not in Verification until the code exists.

1. **User and Order Service fail closed.** User: explicit matchers for `GET`, `PUT`, `DELETE /users/me`, then `anyRequest().denyAll()`. Order: explicit matchers for `POST /orders`, `GET /orders`, `GET`, `PUT`, `DELETE /orders/{id}` with today's scope + role rules, then `anyRequest().denyAll()`. A new endpoint in either service returns `403` until it gets a rule
2. **Exact matchers on permissive rules.** `GET /products/**` becomes `GET /products` and `GET /products/{id}` in Product Service, in the Gateway's `SecurityConfig`, and in the Gateway's `RouteConfig` public route, which never relays a token. Narrowing only the service would leave a future admin `GET` under `/products` unreachable through the Gateway. `GET /orders/**` becomes the exact Order routes
3. **Route-manifest fitness test** in each service: reads every route from the handler mapping and compares it with a checked-in manifest of route → rule. A route missing from the manifest fails the build; every non-public route must answer `401` without a token. The public allowlist holds exact patterns, never prefixes
4. **Cross-user ownership test** in Order and User Service: seed data for user A, call every owner-scoped route as user B, assert A's data never appears
5. **`deleteMyProfile_noToken_returns401`** in `UserControllerSecurityTest`

Tracked as step 2 review findings #18 (items 1, 5), #19 (item 2) and #20 (items 3, 4) in [`.review/best-practices-findings.md`](../../.review/best-practices-findings.md). Ownership backstop beyond tests: Postgres row-level security on `user_sub`, already the migration path in [0004](0004-owner-from-token-sub-404-for-others.md). It needs `SET LOCAL` of the caller's `sub` per transaction, because connections are pooled.

## One-minute answer

The Gateway only checks that there is a logged-in session; each service checks the audience and its own scopes, roles and ownership on the JWT it receives. A service called directly, without the Gateway, applies exactly the same rules, so the Gateway isn't a single wall that everything depends on. The cost is security config in every service, unauthorized requests reaching services before they are refused, and no second layer behind a service's rules. So each service has to fail closed and test every route, which step 2 makes mechanical.

## Consequences

### Positive

- No dependency on the Gateway: bypassing it gains nothing for business rules, for external and internal callers alike
- A new service brings its own rules; the Gateway only needs a route
- Authorization is tested where it is enforced (`OrderControllerSecurityTest`, `ProductControllerSecurityTest`)

### Negative (residual risk)

- **One layer per rule.** A service's `SecurityConfig` is the only place its rules are enforced. If it regresses, nothing at the edge catches it: the Gateway forwards any request that has a session. The guard is each service's security tests, which run in CI and fail the build. Until step 2, they cover existing endpoints only: a new endpoint without a security test is guarded by the service's `anyRequest()` default (`admin` on Product; on Order, write scope + `user`, which every logged-in user holds; authenticated on User) and code review. Step 2 items 1 and 3 close this
- **Until step 2, User and Order Service fail open for new endpoints.** User Service: `anyRequest().authenticated()` lets any token with the `user-service` audience through. Order Service: `anyRequest()` needs `orders:write` + `user`, and every logged-in user's token carries both by default. Safe today because every existing endpoint has its own rule and acts on the caller's `sub`. Step 2 item 1 replaces both defaults with `denyAll`
- **Wildcard matchers on permissive rules.** Until step 2, a new route under `GET /products/**` is public and a new route under `GET /orders/**` needs only read scope + `user`. A new `GET /orders/all` that forgets `sub` would leak every user's orders. Step 2 items 2–4 close this; RLS ([0004](0004-owner-from-token-sub-404-for-others.md)) is the data-layer backstop if tests prove not enough
- **Gateway-only controls are bypassable** while service ports are reachable. Today that is only CSRF (meaningless for bearer calls), but any rate limit added at the Gateway in step 6 would be skipped (T40, 13 High, fixed by step 3 publishing only the Gateway port)
- **Duplicated security code.** `KeycloakRealmRoleConverter` exists in Order and Product Service; each service has its own `SecurityConfig` and `TokenValidationTest`. Shared code vs duplication is a step 2 ADR (finding #13)
- **Unauthorized traffic reaches services.** A logged-in non-admin can send catalog writes to Product Service. Load only, no access
- **Gateway rules must be kept in sync with the route list.** A new route without a matcher is denied, which is the safe failure

## Verification

- `GatewayRoutingTest.unknownPath_isDenied_withOrWithoutSession`
- `GatewayRoutingTest.productWrite_needsASession_unlikeProductRead`
- `GatewayRoutingTest.orders_withoutSession_returns401_andNothingIsForwarded`
- `OrderControllerSecurityTest` (`*_noToken_returns401`, and `*_returns403` cases for a missing scope or role)
- `ProductControllerSecurityTest.createProduct_noToken_returns401`, `createProduct_withoutAdminRole_returns403`, `deleteProduct_withoutAdminRole_returns403`
- `UserControllerSecurityTest.getMyProfile_noToken_returns401`, `updateMyProfile_noToken_returns401`, `register_withoutToken_succeeds`
- `OrderOwnershipTest` (`getAll_showsOnlyTheCallersOwnOrders`, `getById_anotherUsersOrder_returns404NotForbidden`, `update_…`, `delete_anotherUsersOrder_returns404AndKeepsIt`) for ownership by `sub` (T13)
- `TokenValidationTest` in each service: covers audience (`tokenForAnotherService_returns401`, `tokenWithKeycloakDefaultAudienceOnly_returns401`) and signature (test tokens are signed with the test key the decoder trusts)

Not verified by tests: issuer and expiry. Every `AbstractIntegrationTest` blanks `issuer-uri`, so the issuer check runs only in the real configuration, and no test sends an expired token. Both checks come from Spring Security's default JWT validators, not from code in this repo.

Not yet written (step 2): route-manifest fitness test, cross-user ownership test, `deleteMyProfile_noToken_returns401` (see Scheduled hardening)

## Migration path

None planned for the split itself; step 2 hardens it (see Scheduled hardening). When only the Gateway port is published (step 3), the decision stays: network isolation is a second layer, not a reason to move rules to the edge, because internal callers still reach services directly.

## Revisit trigger

- A rule that can't be decided from the token or the service's own data
- Edge rate limits or WAF rules that must also apply to internal callers
- A service authorization regression that reaches `main` despite the route manifest and the cross-user tests. Fix the mechanism that missed it first. Edge role rules come back into consideration only if every caller, internal ones included, goes through the Gateway

Review date: 2027-10-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 7.5/10 | ACCEPT PENDING DEFENSE (below target) |
| 2 | 2026-11-01 | architecture-kata-reviewer | 8/10 | ACCEPT PENDING DEFENSE |
| 2 (defense) | 2026-11-01 | architecture-kata-reviewer | 8/10 | Defense: passed 2026-11-01; required changes 1–5 open |
| 3 | 2026-11-01 | architecture-kata-reviewer | 8.5/10 | ACCEPT (same-session re-review; required changes 1–5 done). Order fail-closed claim corrected after the review, Order hardening scheduled for step 2 (#18) |
