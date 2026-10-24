# 0003. The Gateway checks only "is there a session"; services enforce the rules

| | |
|---|---|
| Status | Proposed |
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
| **Gateway checks only "logged in"; services check scopes, roles, audience and ownership on the token** | Services are safe when called directly; every rule in one place; tests per service | Each service carries security config; unauthorized calls reach services before being refused |
| Gateway enforces roles and scopes per route; services trust the Gateway (identity in headers such as `X-User-Id`) | One security config; services are plain | Anyone reaching a service port can forge the header; ownership still has to be in the service; the Gateway has to know every service's rules |
| Both: Gateway enforces coarse rules, services enforce all rules | Earlier refusal at the edge; less load on services | The same rule written twice; the copies drift; no security gain, since the service check is the one that counts |

## Decision outcome

**Chosen: the Gateway authenticates the browser session; each service authorizes the token.**

Gateway rules (`gateway/.../config/SecurityConfig.java`):

- Public: `GET /products/**`, `POST /users/register`, `GET /`, `/error`, and `POST /auth/dev-login` when dev login is enabled ([0010](0010-dev-login-password-grant-behind-a-flag.md))
- Session required: other methods on `/products/**`, `/orders/**`, `/users/**`, `/auth/me`
- Anything else: `denyAll`
- No API call gets a redirect to Keycloak: a missing session is a `401`

Service rules, on the relayed JWT: signature, issuer, expiry and `aud` (all three services); scope plus role on Order Service; role `admin` for catalog writes on Product Service; ownership by `sub` in Order and User Service ([0004](0004-owner-from-token-sub-404-for-others.md)).

A non-admin with a session is forwarded on `POST /products`; Product Service answers `403`. That is the intended split, not a gap.

Header-based identity was rejected because it is forgeable while service ports are reachable. Duplicated rules were rejected because the edge copy adds no protection, only a second place to update.

## One-minute answer

The Gateway only checks that there is a logged-in session; every service checks scopes, roles, audience and ownership itself on the JWT it receives. A service called directly, without the Gateway, applies exactly the same rules, so the Gateway isn't a single wall that everything depends on. The cost is security config in every service and unauthorized requests reaching services before they are refused.

## Consequences

### Positive

- Defense in depth: bypassing the Gateway gains nothing for business rules
- A new service brings its own rules; the Gateway only needs a route
- Authorization is tested where it is enforced (`OrderControllerSecurityTest`, `ProductControllerSecurityTest`)

### Negative (residual risk)

- **Gateway-only controls are bypassable** while service ports are reachable. Today that is only CSRF (meaningless for bearer calls), but any rate limit added at the Gateway in step 6 would be skipped (T40, 13 High, fixed by step 3 publishing only the Gateway port)
- **Duplicated security code.** `KeycloakRealmRoleConverter` exists in Order and Product Service; each service has its own `SecurityConfig` and `TokenValidationTest`. Shared code vs duplication is a step 2 ADR (finding #13)
- **Unauthorized traffic reaches services.** A logged-in non-admin can send catalog writes to Product Service. Load only, no access
- **Gateway rules must be kept in sync with the route list.** A new route without a matcher is denied, which is the safe failure

## Verification

- `GatewayRoutingTest.unknownPath_isDenied_withOrWithoutSession`
- `GatewayRoutingTest.productWrite_needsASession_unlikeProductRead`
- `GatewayRoutingTest.orders_withoutSession_returns401_andNothingIsForwarded`
- `OrderControllerSecurityTest` (`*_returns403` cases for missing scope or role)
- `ProductControllerSecurityTest.createProduct_withoutAdminRole_returns403`
- `TokenValidationTest` in each service

## Migration path

None planned. When only the Gateway port is published (step 3), the decision stays: network isolation is a second layer, not a reason to move rules to the edge.

## Revisit trigger

- A rule that can't be decided from the token or the service's own data
- Edge rate limits or WAF rules that must also apply to internal callers

Review date: 2027-10-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
