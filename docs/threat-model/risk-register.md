# Risk Register

One row per threat. Details of each threat: the STRIDE file in "Where". Scoring, statuses and rules: [README.md](README.md).

**Scoring basis (2026-10-10):** v1 as if it ran unchanged in a shared, network-reachable environment (the intended deployment, not just one laptop). DREAD columns read `D/R/E/A/D = total`; bands 12–15 **High**, 8–11 **Medium**, 5–7 **Low**. Threats that v1 already controls *and* proves with a test are `Verified (v1)` and not scored: the score would only describe what happens if the control broke. **Residual** is filled when a fix lands.

## Top risks

| Rank | ID | Threat | DREAD | Fixed in |
|---|---|---|---|---|
| 1 | T25 | Password guessing, no brute-force protection | 15 High | 4 |
| 1 | T27 | Keycloak admin console `admin`/`admin` on the network | 15 High | 4 |
| 1 | T32 | Postgres `postgres`/`postgres` on the network | 15 High | 2 → 4 |
| 1 | T42 | Anyone acts as the operator with default credentials | 15 High | 4 |
| 5 | T12 | Unpaginated anonymous catalog reads | 14 High | 2 → 6 |
| 5 | T20 | Registration abuse | 14 High | 6 |
| 7 | T5 | Session flooding via login start | 13 High | 6 |
| 7 | T19 | Username enumeration on registration | 13 High | 6 |
| 7 | T40 | Direct service calls bypass the Gateway | 13 High | 3 |
| 10 | T1 | Session cookie readable on the network | 12 High | 4 |
| 10 | T26 | Password grant live at Keycloak via test client | 12 High | 4 |
| 10 | T37 | No TLS on any flow | 12 High | 4 |

Twelve High, all with a step. Order → Product (T38), which #05 set out to score, lands at **8 Medium**: it needs a position on the internal network, which v1 doesn't give away (see the notes below).

## Register

| ID | Threat | Where | STRIDE | DREAD initial | DREAD residual | Fixed in | Status | Evidence |
|---|---|---|---|---|---|---|---|---|
| T1 | Session cookie read off the network (no TLS) | [gateway](stride/gateway.md) | S | 3/2/2/2/3 = **12 High** | | 4 (TLS, `Secure` cookie) | Open | |
| T2 | Session fixation | [gateway](stride/gateway.md) | S | — | | 2 (add test) | Mitigated (v1) | Spring Security session id change on login; `DevLoginController.startSession` |
| T3 | CSRF on writes; login CSRF on dev login | [gateway](stride/gateway.md) | T | — | | — | Verified (v1) | `GatewayRoutingTest.write_withSessionButNoCsrfToken_returns403_andNothingIsForwarded`, `AuthFlowTest.logout_withoutCsrfToken_returns403`. Dev-login login CSRF: dev profile only |
| T4 | Tokens leak to browser or downstream | [gateway](stride/gateway.md) | I | — | | 6 (strip client `Authorization` on public routes, carried-over list) | Verified (v1) | `AuthFlowTest.me_withSession_returnsUserAndRoles_butNoToken`, `DevLoginTest.validCredentials_createSession_andReturnUserWithoutTokens`, `GatewayRoutingTest.publicProductRead_isForwardedWithoutAnyBrowserCredentials` |
| T5 | Session flooding via login start | [gateway](stride/gateway.md) | D | 2/3/3/3/2 = **13 High** | | 6 (rate limit + session cap) | Open | |
| T6 | No upstream timeouts | [gateway](stride/gateway.md) | D | 2/2/2/3/1 = 10 Medium | | 6 | Open | |
| T7 | Dev login enabled outside local | [gateway](stride/gateway.md) | E | 3/1/2/3/2 = 11 Medium | | 4 (startup guard) | Open | |
| T8 | Public route reaches private behaviour | [gateway](stride/gateway.md) | E | — | | — | Verified (v1) | `GatewayRoutingTest.unknownPath_isDenied_withOrWithoutSession`, `productWrite_needsASession_unlikeProductRead` |
| T9 | Non-admin changes the catalog | [product-service](stride/product-service.md) | E | — | | 2 (add `PUT` test) | Mitigated (v1) | `ProductControllerSecurityTest.createProduct_withoutAdminRole_returns403`, `deleteProduct_withoutAdminRole_returns403`; `PUT` untested |
| T10 | Token for another service accepted (Product) | [product-service](stride/product-service.md) | S | — | | — | Verified (v1) | `TokenValidationTest.adminTokenForAnotherService_returns401`, `publicRead_withTokenForAnotherService_returns401` |
| T11 | Mass assignment on products | [product-service](stride/product-service.md) | T | — | | — | Verified (v1) | `CreateProductRequest`/`UpdateProductRequest` records; `ProductControllerSecurityTest.createProduct_withAdminRole_invalidPayload_returns400` |
| T12 | Unpaginated anonymous catalog reads | [product-service](stride/product-service.md) | D | 2/3/3/3/3 = **14 High** | | 2 (pagination, finding #3) → 6 (rate limit) | Open | |
| T13 | BOLA on orders | [order-service](stride/order-service.md) | E | — | | — | Verified (v1) | `OrderOwnershipTest.getById_anotherUsersOrder_returns404NotForbidden`, `update_anotherUsersOrder_returns404AndLeavesItUnchanged`, `delete_anotherUsersOrder_returns404AndKeepsIt` |
| T14 | Client sets its own price | [order-service](stride/order-service.md) | T | — | | — | Verified (v1) | `OrderProductLookupTest.createOrder_pricesFromProductServiceAndIgnoresClientSuppliedPrice` |
| T15 | Client sets order status / quantity without stock check | [order-service](stride/order-service.md) | T | 2/3/3/1/2 = 11 Medium | | 6 (Phase 7, system-owned status) | Open | |
| T16 | Overselling (stock never decremented) | [order-service](stride/order-service.md) | T | 2/2/2/2/2 = 10 Medium | | 5 (`reserve-stock`) | Open | |
| T17 | Admin service account token nearly accepted by Order | [order-service](stride/order-service.md) | S | 2/1/1/1/1 = 6 Low | | 4 (realm: drop `orders:*` from realm default scopes) | Open | Blocked today by audience: `TokenValidationTest.tokenForAnotherService_returns401` |
| T18 | Connection pool drained by slow Product calls | [order-service](stride/order-service.md) | D | 2/2/2/3/1 = 10 Medium | | 2 (finding #2) | Open | |
| T19 | Username enumeration on registration | [user-service](stride/user-service.md) | I | 1/3/3/3/3 = **13 High** | | 6 (rate limit; generic message decided in the rate-limit ADR) | Open | |
| T20 | Registration abuse | [user-service](stride/user-service.md) | D | 2/3/3/3/3 = **14 High** | | 6 (rate limit) | Open | |
| T21 | Keycloak user / profile out of sync | [user-service](stride/user-service.md) | T | 2/1/1/1/1 = 6 Low | | 2 (finding #1) | Open | Register path: `ProfileServiceTest.register_profileWriteFails_deletesTheKeycloakUserAgainAndRethrows` |
| T22 | New user's password in logs | [user-service](stride/user-service.md) | I | — | | — | Verified (v1) | `RegisterRequest.toString()` masks it; no request-body logging |
| T23 | Stolen admin-client secret (can it grant `admin`?) | [user-service](stride/user-service.md) | E | 3/1/2/3/1 = 10 Medium | | 4 (secrets delivery) | Open | **Open question:** verify against a running Keycloak whether `manage-users` can map the `admin` realm role. If yes, re-score D and A |
| T24 | Mass assignment on profiles | [user-service](stride/user-service.md) | T | — | | — | Verified (v1) | `UserControllerSecurityTest.updateMyProfile_changesOnlyProfileFields` |
| T25 | Password guessing (no brute-force protection, no policy, no MFA) | [keycloak](stride/keycloak.md) | S | 3/3/3/3/3 = **15 High** | | 4 (realm hardening) → 6 (MFA, stretch) | Open | |
| T26 | Password grant live at Keycloak via test client | [keycloak](stride/keycloak.md) | S | 3/2/2/3/2 = **12 High** | | 4 (realm hardening: test client out of non-dev imports) | Open | |
| T27 | Keycloak admin console `admin`/`admin` on the network | [keycloak](stride/keycloak.md) | E | 3/3/3/3/3 = **15 High** | | 4 (credentials from `.env`, port bound to localhost) | Open | |
| T28 | No login or admin events | [keycloak](stride/keycloak.md) | R | 2/3/1/3/1 = 10 Medium | | 4 (realm: events on) | Open | |
| T29 | Stolen refresh token usable up to 10 h | [keycloak](stride/keycloak.md) | E | 3/1/1/1/1 = 7 Low | | 6 (Phase 6 rotation) | Open | |
| T30 | Keycloak down | [keycloak](stride/keycloak.md) | D | 2/2/2/3/1 = 10 Medium | | Accepted (ADR in step 1 Phase 5) | Open | Single instance by design for this project |
| T31 | Shared Postgres superuser | [data-stores](stride/data-stores.md) | E/T | 3/1/2/3/2 = 11 Medium | | 2 (finding #6) | Open | |
| T32 | Postgres `postgres`/`postgres` on the network | [data-stores](stride/data-stores.md) | E | 3/3/3/3/3 = **15 High** | | 2 (credentials + per-service roles, finding #6) → 4 (port not published) | Open | |
| T33 | PII stored unencrypted | [data-stores](stride/data-stores.md) | I | 2/1/2/3/1 = 9 Medium | | Accepted (ADR in step 1 Phase 5) | Open | Revisit trigger: any real deployment |
| T34 | Tokens exposed by a Gateway memory dump | [data-stores](stride/data-stores.md) | I | 3/1/1/3/1 = 9 Medium | | 4 (Actuator lockdown keeps `heapdump` off) | Open | No Actuator on the classpath today |
| T35 | Host files leak (Keycloak H2, `.env`) | [data-stores](stride/data-stores.md) | I | 3/1/2/3/1 = 10 Medium | | 3 (secret scanning) → 4 (Docker secrets) | Open | `.env`, `keycloak-data/` gitignored |
| T36 | No change history in the database | [data-stores](stride/data-stores.md) | R | 1/2/2/3/1 = 9 Medium | | 6 (Phase 7 audit logging) | Open | |
| T37 | No TLS on any flow | [data-flows](stride/data-flows.md) | I/T | 3/2/2/3/2 = **12 High** | | 4 (TLS) | Open | |
| T38 | Order price from an unauthenticated, unencrypted reply | [data-flows](stride/data-flows.md) | S/T | 2/1/1/3/1 = 8 Medium | | 5 (M2M auth) + 4 (TLS) | Open | |
| T39 | Signing keys fetched over plain HTTP | [data-flows](stride/data-flows.md) | S | 3/1/1/3/1 = 9 Medium | | 4 (TLS to Keycloak) | Open | |
| T40 | Direct service calls bypass Gateway-only controls | [data-flows](stride/data-flows.md) | E | 2/3/3/3/2 = **13 High** | | 3 (Phase 0: only the Gateway port published) | Open | |
| T41 | User denies an action (no audit trail) | [external-entities](stride/external-entities.md) | R | 1/2/2/2/1 = 8 Medium | | 6 (Phase 7 audit logging) | Open | |
| T42 | Anyone acts as the operator with default credentials | [external-entities](stride/external-entities.md) | S | 3/3/3/3/3 = **15 High** | | 4 (same fix as T27, T32) | Open | |

## Notes on the scores

- **DREAD rewards "easy" over "bad".** T19 (username enumeration, 13) outranks T23 (stolen admin secret, 10) because enumeration is trivial and public while the secret is hard to get. In practice T23 is the more serious threat if its open question resolves badly. The register keeps the DREAD order; this note is the judgment on top.
- **Damage for denial of service** (T5, T12, T18, T20): scored 2 (a service outage, no data lost), never 3.
- **T38 at 8:** forging Product Service's reply needs a man-in-the-middle position on the internal network. That rarely happens until the network is shared, but when it does, every order is mispriced. #03 still closes it, because its `reserve-stock` write makes the unauthenticated channel a real problem.
- **Four 15s share one root cause:** default credentials on reachable ports (T25 partly, T27, T32, T42). One change in step 4 (credentials from `.env`, ports bound to localhost or not published) closes three of them.
- **Accepted, pending ADRs (T30, T33):** move to `Accepted` when their ADRs are written in this step's Phase 5.
