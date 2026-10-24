# 0001. Keycloak as the only Authorization Server

| | |
|---|---|
| Status | Proposed |
| Date | 2026-10-24 (recorded). Decided 2026-08-23, #01 Phase 1, PR #1 |
| Category | security |
| Impact | high |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5, retroactive) |
| Drivers | #01 requirement: learn OAuth2/OIDC on a real IdP; T25, T27, T30 |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

secure-shop needs user accounts, login, and access tokens that three resource servers (Order, Product, User) and a BFF Gateway can trust. Someone has to issue those tokens, store passwords, run the login page, publish signing keys and handle refresh and logout.

The project exists to learn OAuth2 and OIDC as they are used at work. That creates a pull towards building the Authorization Server too, "to see how it works". Writing password storage, login, key rotation and token issuance is a separate project, and the place where a home-grown version is most likely to be wrong.

Which component issues tokens, and may any service issue its own?

## Decision drivers

- Standard OIDC end to end: discovery, JWKS, Authorization Code with PKCE, refresh, end-session, client credentials
- No password storage or token signing in code we write
- Runs locally, offline and free, inside docker compose
- An Admin API, so User Service can create accounts at registration
- Configuration that can be committed and reviewed (see [0006](0006-realm-as-code-secrets-as-placeholders.md))
- Experience that carries over to client projects, where Keycloak or a hosted IdP is the usual answer

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **Keycloak, self-hosted** (one realm) | Full OIDC, admin console and Admin API, realm export, free, runs in a container, common in Java shops | Heavy JVM process; insecure dev defaults; one more thing to run and patch; claim layout is Keycloak-specific (`realm_access.roles`) |
| Spring Authorization Server as its own service | Same stack as the rest; full control; good for learning the server side | We would write the user store, login page, password hashing, registration, admin UI and key rotation. That is building an AS |
| Hosted IdP (Auth0, Okta, Cognito) | No server to run; MFA, brute-force protection and HA included | Needs an account and internet; free-tier limits; config lives in a vendor console, harder to commit and review; less to learn about what an AS does |
| User Service signs its own JWTs | Fewest moving parts | Home-grown password storage and token issuance, no standard flows, no refresh or logout model. The classic mistake this project is meant to avoid |

## Decision outcome

**Chosen: Keycloak 26.5.2, realm `secure-shop`, as the only token issuer.**

Every service trusts exactly one issuer, `http://localhost:8081/realms/secure-shop`, through `spring.security.oauth2.resourceserver.jwt.issuer-uri`. The Gateway is an OIDC client of the same realm. No service signs tokens or stores passwords. User Service creates accounts only through Keycloak's Admin API, and the password goes straight to Keycloak.

Spring Authorization Server was rejected because the goal is to apply OAuth2 against a real IdP, not to build and own one. It stays the right answer for a team that has to embed a custom AS, which this project does not. The hosted IdPs were rejected for this project because they do not run offline and keep their configuration outside the repo; on a real deployment they are a fair alternative (see revisit trigger).

## One-minute answer

We use Keycloak as the only Authorization Server, and no service issues its own tokens. Identity is solved, standard and risky to build, so we buy the hard parts: password storage, login, signing keys, refresh and logout. The cost is a heavy component to run and harden, with insecure dev defaults we have to fix ourselves, and a single point of failure for login.

## Consequences

### Positive

- Services only validate tokens: signature against JWKS, issuer, expiry, audience. No credential code anywhere in `backend/`
- Standard flows come for free: PKCE for the browser, client credentials for User Service, end-session for logout
- Switching IdP later touches configuration and two Keycloak-specific classes, not the security model

### Negative (residual risk)

- **Single point of failure for login.** Keycloak down means no login, no refresh, no registration (T30, accepted in [0012](0012-accept-single-keycloak-instance.md))
- **Insecure defaults are ours to fix.** The imported realm has `bruteForceProtected: false` (T25, 15 High) and the bootstrap admin is `admin`/`admin` in `docker/docker-compose.yml` (T27, 15 High). Both are owned by step 4 (#02 Phase 7)
- **Dev mode storage.** `start-dev` keeps users, password hashes and signing keys in H2 under `docker/keycloak-data/` (T35)
- **Vendor-specific code.** `KeycloakRealmRoleConverter` (duplicated in Order and Product Service) reads `realm_access.roles`; `KeycloakAdminClient` in User Service calls the Admin API. Both would change with another IdP
- **Resource cost.** Keycloak is the heaviest container in the compose stack

## Verification

- Every service's `application.properties` sets `issuer-uri` to the `secure-shop` realm; the Gateway's provider uses the same issuer
- No `JwtEncoder` or `PasswordEncoder` exists in `backend/` (a grep returns nothing)
- `TokenValidationTest` in each service rejects a token that is not for that service, so a token from any other issuer or client is not accepted

## Migration path

The services depend on standard OIDC: issuer URI, JWKS and the `aud` claim. Moving to another IdP means a new `issuer-uri`, replacing `KeycloakRealmRoleConverter` with a converter for the new role claim, and replacing `KeycloakAdminClient` with the new provider's user API. User data is keyed by `sub` ([0004](0004-owner-from-token-sub-404-for-others.md)), so a migration must keep `sub` values or remap them.

## Revisit trigger

- A real deployment where a hosted IdP's cost is lower than running and hardening Keycloak
- A feature Keycloak cannot provide without custom SPI code
- Keycloak's memory footprint blocks the containerized stack in step 3

Review date: 2027-10-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
