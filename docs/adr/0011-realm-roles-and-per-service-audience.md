# 0011. Realm roles for `user`/`admin`, an audience per service

| | |
|---|---|
| Status | Proposed |
| Date | 2026-10-24 (recorded). Decided 2026-10-03, #01 Phase 4, PR #4 |
| Category | security |
| Impact | high |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5, retroactive) |
| Drivers | T10, T17; Phase 4 Keycloak cleanup |
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
| One shared audience (`secure-shop-api`) | Simpler | Any future service accepts every token |
| No audience check, issuer only | Nothing to configure | Any token from the realm is accepted everywhere |

## Decision outcome

**Chosen: realm roles for who the user is, scopes for what the app may do, and a per-service audience.**

- Realm roles `user` and `admin`. `user` comes through the `default-roles-secure-shop` composite, so every registered user has it; admins have both
- `KeycloakRealmRoleConverter` maps `realm_access.roles` to `ROLE_*` while keeping the `SCOPE_*` authorities
- Scopes `orders:read` and `orders:write` say what the app may do; Order Service requires scope and role together
- The `secure-shop-audience` client scope is a default scope on `secure-shop-gateway` and `secure-shop-test-client`; each service sets `spring.security.oauth2.resourceserver.jwt.audiences` to its own name and answers `401` otherwise

Client roles were rejected for now because they would split one meaning into three; they come back when a second kind of admin appears. One shared audience was rejected because it makes every new service accept every existing token.

## One-minute answer

Roles that describe the user, `user` and `admin`, are realm roles, because a scope is granted to the client and would reach everyone who logs in through it. Scopes say what the app may do, and every token carries a per-service audience that each service checks, so a token minted for some other client is rejected. The cost is that the roles are global and one user token is valid at all three services.

## Consequences

### Positive

- "Admin only" follows the user, whatever client they use
- Tokens for unrelated clients are rejected everywhere (`401`)
- Registration needs no role code

### Negative (residual risk)

- **One token, three services.** User tokens name all three services in `aud`, so a token leaked from one service can be replayed against the other two. Narrowing it needs token exchange or separate tokens per service (resource indicators)
- **`admin` is global.** Catalog admin is admin everywhere; no least privilege per service
- **`orders:read`/`orders:write` are realm default client scopes**, so every client gets them, including `user-service-admin-client`, whose service account token also carries the `user` role through the default composite. Only the audience check stops that token at Order Service (T17, 6 Low; step 4 removes the scopes from the realm defaults)
- **Keycloak-specific converter**, duplicated in Order and Product Service ([0001](0001-keycloak-as-the-only-authorization-server.md), finding #13)

## Verification

- `TokenValidationTest` in each service: `tokenForAnotherService_returns401`, `tokenWithKeycloakDefaultAudienceOnly_returns401` (Order, User), `adminTokenForAnotherService_returns401`, `publicRead_withTokenForAnotherService_returns401` (Product)
- `KeycloakRealmRoleConverterTest.mapsRealmRolesToRoleAuthorities`, `keepsScopeAuthoritiesAlongsideRoles`
- `OrderControllerSecurityTest.getOrders_withReadScopeButNoUserRole_returns403`, `getOrders_withUserRoleButNoScope_returns403`

## Migration path

A second kind of admin (for example support with read-only access): client roles per service, with the converter extended to read `resource_access`. Narrower tokens: Keycloak token exchange at the Gateway, one token per downstream service.

## Revisit trigger

- A role whose meaning differs between services
- A service that must not accept tokens meant for the others

Review date: 2027-10-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
