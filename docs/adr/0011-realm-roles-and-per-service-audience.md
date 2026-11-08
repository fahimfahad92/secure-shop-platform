# 0011. Realm roles for `user`/`admin`, an audience per service

| | |
|---|---|
| Status | Accepted |
| Date | 2026-10-24 (recorded). Decided 2026-10-03, #01 Phase 4, PR #4 |
| Category | security |
| Impact | high |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5, retroactive) |
| Drivers | T10, T17, T23; Phase 4 Keycloak cleanup |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

Three questions need answers in the token: what may the app do for the user, who is the user, and is this token meant for this service. Before Phase 4 the answers were mixed up: "admin" was modelled as something granted to the client, so every user of the same client got it, and services accepted any valid token from the realm.

`testuser` and `adminuser` log in through the same client. A client scope is granted to the client, so a scope like `admin` would reach every user of that client. And without an audience check, a token minted for any client in the realm, including unrelated ones, is accepted by every service.

How are user roles and token audience modelled?

## Decision drivers

- "Admin only" must depend on the user, not on the client they log in with
- `admin` and `user` mean the same thing to every service today
- A service rejects tokens that weren't issued for it
- Registration gives the `user` role without any code in User Service
- Minimal Keycloak configuration

## Considered options

**Roles**

| Option | Pros | Cons |
|---|---|---|
| **Realm roles `user` and `admin` in `realm_access.roles`** | Belong to the user; one meaning across services; one place to grant | Global: an admin is admin everywhere; Keycloak-specific claim |
| Client roles per service (`resource_access.<service>.roles`) | Per-service admins, least privilege | More configuration; three roles that mean the same thing today |
| Client scopes as permissions (`scope=admin`) | Standard `scope` claim, no custom converter | Scopes are granted to the client, so every user of the client gets them |
| Groups claim | Flexible hierarchies | Groups still need mapping to permissions; overkill for two roles |

**Audience**

| Option | Pros | Cons |
|---|---|---|
| **Per-service audience: `secure-shop-audience` scope adds `order-service`, `product-service`, `user-service` to `aud`; each service requires its own name** | A token for an unrelated client is rejected; standard check (`jwt.audiences`) | Tokens from the user-facing clients carry all three names, so one token works at all three services |
| One shared audience (`secure-shop-api`) | Simpler: one mapper, one name | Any future service accepts every token |
| Token exchange at the Gateway (RFC 8693): one token per downstream service, each with a single `aud` | Contains replay: a token a service receives works only at that service | Build work, not runtime: the three audiences become Keycloak clients (today they are custom-audience strings), standard token exchange is enabled on `secure-shop-gateway` and limited to them, and the Gateway replaces `tokenRelay()` with its own relay filter (one registration per service, a cache per session and service, cleared on logout and refresh, dev-login sessions included), with tests against the Keycloak stub. Runtime: one extra Keycloak call per service per 300 s, on top of the refresh the Gateway already makes at that rate, so no new outage exposure (T30: sessions already stop within one token lifetime when Keycloak is down) |
| No audience check, issuer only | Nothing to configure | Any token from the realm is accepted everywhere |

**Per-service and shared audience behave the same for today's tokens.** Every token that reaches a service comes from `secure-shop-gateway` or `secure-shop-test-client`, and both carry all three names, so a service that checks its own name accepts exactly the tokens a service checking `secure-shop-api` would. No other client in the realm has `secure-shop-audience`: not `user-service-admin-client`, and not Keycloak's built-in clients, including `admin-cli` (public, direct access grants on, so anyone with a user's password can get a token from it). Both designs reject their tokens. The two designs only differ for future clients and services: with per-service names a new service accepts only tokens that were given its name, and a client can be given a subset of names; with one shared name every new service accepts every existing token.

## Decision outcome

**Chosen: realm roles for who the user is, scopes for what the app may do, and a per-service audience.**

- Realm roles `user` and `admin`. `user` comes through the `default-roles-secure-shop` composite, so every registered user has it; admins have both
- `KeycloakRealmRoleConverter` maps `realm_access.roles` to `ROLE_*` while keeping the `SCOPE_*` authorities
- Scopes `orders:read` and `orders:write` say what the app may do; Order Service requires scope and role together (`hasAuthority("SCOPE_orders:read")` and `hasRole("user")`, same for writes)
- Each service checks what it needs: Product Service requires `ROLE_admin` for writes and no scope; User Service checks neither role nor scope, only a valid token for `user-service`, and scopes the data to the token's `sub` ([0004](0004-owner-from-token-sub-404-for-others.md)). So the `user` role is enforced only at Order Service
- The `secure-shop-audience` client scope is a default scope on `secure-shop-gateway` and `secure-shop-test-client`; each service sets `spring.security.oauth2.resourceserver.jwt.audiences` to its own name and answers `401` otherwise

Client roles were rejected for now because they would split one meaning into three; they come back when a second kind of admin appears. One shared audience was rejected because, although it behaves the same today, it makes every new service accept every existing token; per-service names cost two extra mappers and keep that choice per service. Token exchange was rejected for v1: it is the only option that contains replay, but it needs three new Keycloak clients and a custom relay filter, about a phase of work, and the replay it removes gives less than the Postgres superuser already does (accepted below). It is owned by #03 (step 5), which creates service clients in Keycloak anyway; if step 2's database roles land first, the replay acceptance is reopened then.

## One-minute answer

Roles that describe the user, `user` and `admin`, are realm roles, because a scope is granted to the client and would reach everyone who logs in through it. Scopes say what the app may do, and every token carries a per-service audience that each service checks, so a token minted for some other client is rejected. The cost is that the roles are global and one user token is valid at all three services, so a compromised service can replay the tokens it receives. That is accepted for now: today a compromised service already reaches every schema as the Postgres superuser, which is worse, and it stops being acceptable once step 2's per-service database roles remove that path.

## Consequences

### Positive

- "Admin only" follows the user, whatever client they use
- Tokens for unrelated clients are rejected everywhere (`401`)
- Registration needs no role code

### Negative (residual risk)

- **One token, three services: replay by a compromised service is accepted.** The Gateway relays the same user token to every service, and it names all three in `aud`. A compromised service can replay any token it receives against the other two. One token lasts 300 s, but the Gateway sends a fresh one with every request, so the real window is the user's session, as long as they stay active: Order Service receiving an admin user's token can call Product Service's admin writes; any service can read and change that user's profile at User Service.
  - **Why accepted:** the replay gives less than a compromised service already has. All services connect to Postgres as the superuser ([0008](0008-schema-per-service-on-one-postgres.md), T31, T32), so any of them can already read and change every schema, orders, catalog and profiles, without a token. A compromised User Service also holds the admin-client secret (T23). And this is a local R&D project with every service on one machine
  - **When it stops being acceptable:** when step 2's per-service database roles land, token replay becomes the main way a compromised service reaches the others' data. Also when any service runs outside the developer's machine or is run by someone else. Then token exchange (Considered options) or resource indicators come back
- **`admin` is global.** Catalog admin is admin everywhere; no least privilege per service
- **`orders:read`/`orders:write` are realm default client scopes**, so every client gets them, including `user-service-admin-client`, whose service account token also carries the `user` role through the default composite. Only the audience check stops that token at Order Service (T17, 6 Low; step 4 removes the scopes from the realm defaults)
- **Keycloak-specific converter**, duplicated in Order and Product Service ([0001](0001-keycloak-as-the-only-authorization-server.md), finding #13). The Gateway has a third copy of the `realm_access.roles` parsing (`KeycloakRoles`), used only to show roles in `/auth/me`; it skips the signature check because the token comes straight from Keycloak's token endpoint, and no access decision uses it
- **Who can grant `admin` is not settled.** Roles follow the user, so the risk moves to whoever can assign them. User Service's `user-service-admin-client` holds `manage-users` and `view-users` on `realm-management`. Whether `manage-users` can map the `admin` realm role is still an open question (T23, 10 Medium, "verify against a running Keycloak"). If it can, a stolen admin-client secret is a path to catalog admin

## Verification

- `TokenValidationTest`, per service:
  - Order Service and User Service: `tokenForAnotherService_returns401`, `tokenWithKeycloakDefaultAudienceOnly_returns401`
  - Product Service: `adminTokenForAnotherService_returns401`, `publicRead_withTokenForAnotherService_returns401` (a token for another service is rejected even on the public `GET`, not ignored)
- `KeycloakRealmRoleConverterTest` in Order and Product Service (one per copy): `mapsRealmRolesToRoleAuthorities`, `keepsScopeAuthoritiesAlongsideRoles`
- `OrderControllerSecurityTest.getOrders_withReadScopeButNoUserRole_returns403`, `getOrders_withUserRoleButNoScope_returns403`
- Realm file (by inspection, no test): `secure-shop-audience` is a default scope on `secure-shop-gateway` and `secure-shop-test-client` only, not on `user-service-admin-client`; `orders:read`/`orders:write` are in `defaultDefaultClientScopes`; `default-roles-secure-shop` includes `user`; only `adminuser` has `admin`; `accessTokenLifespan` is 300 s

## Migration path

A second kind of admin (for example support with read-only access): client roles per service, with the converter extended to read `resource_access`. Narrower tokens: Keycloak token exchange at the Gateway, one token per downstream service.

## Revisit trigger

- A role whose meaning differs between services
- A service that must not accept tokens meant for the others
- Step 2's per-service database roles land, or any service runs off the developer's machine: the accepted replay risk is reopened (token exchange)
- T23 resolved: if `manage-users` can map `admin`, role assignment needs its own control (fine-grained admin permissions or a separate client)

Review date: 2027-10-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 0 | 2026-10-24 | architecture-kata-reviewer | 6/10 (capped) | REVISE (G3) |
| 1 (pre) | 2026-10-24 | architecture-kata-reviewer | 9/10 | ACCEPT PENDING DEFENSE |
| 1 | 2026-11-08 | architecture-kata-reviewer | 8.5/10 | ACCEPT PENDING DEFENSE |
| 1 (defense) | 2026-11-08 | architecture-kata-reviewer | 8.5/10 | REVISE. Defense failed: replay of a user token by a compromised service was dismissed ("not a valid scenario") instead of accepted with a reason, which contradicts this ADR's own residual risk; shared vs per-service audience left unanswered. Required: (1) accept the replay risk with a reason (subsumed by the T23 blast radius; local R&D) and a trigger that makes it unacceptable; (2) token exchange as a weighed audience option, costed (one cached Keycloak round trip per downstream call, exchange feature setup, runtime dependency); (3) state that per-service and shared audiences behave the same for today's tokens. Optional: realm-JSON assertion test; fix the test-name grouping in Verification |
| 2 | 2026-11-08 | architecture-kata-reviewer | 8.5/10 | ACCEPT PENDING DEFENSE. All three round-1 required changes made. New: token-exchange outage cost overstated (Gateway already refreshes via Keycloak every 300 s; exchanged tokens cached same lifetime); its real setup cost (three audience clients, custom relay filter) missing; "only other client" ignores `admin-cli`; replay window is the session, not 300 s. Optional: fix those; realm-JSON assertion test |
| 2 (defense) | 2026-11-08 | architecture-kata-reviewer | 8.5/10 | ACCEPT. Defense: passed 2026-11-08. Conceded the token-exchange outage cost (refresh already ties sessions to Keycloak at 300 s); real cost named as build work (audience clients, exchange on the Gateway client, custom relay filter and cache, tests); replay accepted as subsumed by the Postgres superuser path until step 2. Recommended: fold this into the ADR (F1–F3) and name the step that owns token exchange |
