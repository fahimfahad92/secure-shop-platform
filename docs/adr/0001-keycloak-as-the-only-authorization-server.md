# 0001. Keycloak as the only Authorization Server

| | |
|---|---|
| Status | Proposed |
| Date | 2026-10-24 (recorded). Decided 2026-08-23, #01 Phase 1, PR #1 |
| Category | security |
| Impact | high |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5, retroactive) |
| Drivers | #01 requirement: learn OAuth2/OIDC on a real IdP; offline/free local stack; Admin API for registration |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

secure-shop needs user accounts, login, and access tokens that three resource servers (Order, Product, User) and a BFF Gateway can trust. Someone has to issue those tokens, store passwords, run the login page, publish signing keys and handle refresh and logout.

The project exists to learn OAuth2 and OIDC as they are used at work. That creates a pull towards building the Authorization Server too, "to see how it works". Writing password storage, login, key rotation and token issuance is a separate project, and the place where a home-grown version is most likely to be wrong.

Which component issues tokens, and may any service issue its own?

## Decision drivers

- Standard OIDC end to end: discovery, JWKS, Authorization Code with PKCE, refresh, end-session, client credentials
- No password storage or token signing in code we write
- An industry-standard IdP (open source, CNCF incubating project) whose password and secret handling already follows current practice: Argon2 password hashing by default (the realm sets no `passwordPolicy`, so the default applies), configurable password policies, brute-force detection (off in this realm today, T25) and per-client secrets
- On top of that, social login and SSO through identity brokering (Google and other OIDC/SAML providers), planned for later; `identityProviders` is empty in the realm today
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

Scope: local development and the learning phase. The production IdP (Keycloak on ECS or a hosted IdP) is decided in a new ADR at first deployment.

Every service trusts exactly one issuer, `http://localhost:8081/realms/secure-shop`, through `spring.security.oauth2.resourceserver.jwt.issuer-uri`. The Gateway is an OIDC client of the same realm. No service signs tokens or stores passwords. User Service creates accounts only through Keycloak's Admin API, and the password goes straight to Keycloak.

Spring Authorization Server was rejected because the goal is to apply OAuth2 against a real IdP, not to build and own one. It stays the right answer for a team that has to embed a custom AS, which this project does not. The hosted IdPs were rejected for this project because they do not run offline and keep their configuration outside the repo; on a real deployment they are a fair alternative (see revisit trigger).

An Authorization Server has two hard parts: building it (password hashing and storage, login flows, token signing, JWKS and key rotation, refresh and logout, an admin API) and running it (patching, hardening, availability, key storage). Keycloak takes the first off our hands, tested and standard; we still own the second. A hosted IdP takes both, which makes it the real runner-up. We still choose Keycloak because, for this project, running offline and free in docker compose, keeping the realm in the repo ([0006](0006-realm-as-code-secrets-as-placeholders.md)) and learning on a server we can inspect weigh more than saved operations work. Keycloak also has no licence fee and no per-user pricing, but that saving is paid back in our own patching and hardening time, so cost alone does not decide it.

## One-minute answer

We use Keycloak as the only Authorization Server, and no service issues its own tokens. Identity is solved, standard and risky to build, so Keycloak builds it for us: password storage, login, signing keys, refresh and logout. We chose it over a hosted IdP because it runs offline and free with the realm in the repo; the cost is that running it is ours: a heavy component to patch and harden, insecure dev defaults to fix, and a single point of failure for login.

## Consequences

### Positive

- Services only validate tokens: signature against JWKS, issuer, expiry, audience. No credential code anywhere in `backend/`
- Standard flows come for free: PKCE for the browser, client credentials for User Service, end-session for logout
- Switching IdP later touches configuration and a small set of Keycloak-specific classes (listed under Negative), not the security model

### Negative (residual risk)

- **Single point of failure for login.** Keycloak down means no login, no refresh, no registration (T30, accepted in [0012](0012-accept-single-keycloak-instance.md)).
- **Insecure defaults are ours to fix.** The imported realm has `bruteForceProtected: false` (T25, 15 High) and the bootstrap admin is `admin`/`admin` in `docker/docker-compose.yml` (T27, 15 High). `admin`/`admin` is for the local setup only; production is out of scope (see the production IdP ADR). Both are owned by step 4 (#02 Phase 7)
- **Dev mode storage, local only.** `start-dev` keeps users, password hashes and signing keys in H2 under `docker/keycloak-data/` (T35). Dev mode is used only on a developer machine; a production deployment is out of scope (see the production IdP ADR)
- **Vendor-specific code, small and accepted.** Keycloak-specific code is limited to reading `realm_access.roles` (`KeycloakRealmRoleConverter`, duplicated in Order and Product Service; `KeycloakRoles` and `KeycloakOidcUserService` in the Gateway), calling the Admin API (`KeycloakAdminClient` and its config in User Service), and the dev-only `KeycloakPasswordGrant` in the Gateway. These classes would change with another IdP; the security model would not
- **Resource cost.** Keycloak is the heaviest container in the compose stack. Image and memory tuning is planned for later work

### Future work

Risks this decision creates, tracked in the [risk register](../threat-model/risk-register.md):

| Risk | Threat | DREAD | Planned fix |
|---|---|---|---|
| T25 | Password guessing: no brute-force protection, password policy or MFA | 15 High | Step 4 (#02 Phase 7): enable brute-force detection and a password policy in the realm. MFA in step 6 (stretch) |
| T27 | Admin console `admin`/`admin` reachable on the network | 15 High | Step 4 (#02 Phase 7): admin credentials from `.env`, admin port bound to localhost |
| T30 | Keycloak down: no login, refresh or registration | 10 Medium | Accepted in [0012](0012-accept-single-keycloak-instance.md) for this project. Review 2027-04-24 |

## Verification

- Every service's `application.properties` sets `issuer-uri` to the `secure-shop` realm; the Gateway's provider uses the same issuer
- No `JwtEncoder` or `PasswordEncoder` exists in `backend/` (a grep returns nothing)
- `TokenValidationTest` in each service rejects a token whose audience is not that service, so a token issued for another client is not accepted
- The issuer check is not covered by tests: each service's `AbstractIntegrationTest` sets `issuer-uri` to `""` and test tokens carry no `iss`. A test that sends a token signed by the test key with a wrong `iss` is still to be added

## Migration path

The services depend on standard OIDC: issuer URI, JWKS and the `aud` claim. Moving to another IdP means a new `issuer-uri` in each service and in the Gateway's client registration; replacing the role-claim readers (`KeycloakRealmRoleConverter` in Order and Product Service, `KeycloakRoles` and `KeycloakOidcUserService` in the Gateway) with readers for the new role claim; replacing `KeycloakAdminClient` and its config with the new provider's user API; and dropping or porting the dev-only `KeycloakPasswordGrant`. User data is keyed by `sub` ([0004](0004-owner-from-token-sub-404-for-others.md)), so a migration must keep `sub` values or remap them.

## Revisit trigger

- First deployment outside a developer machine: write the production IdP ADR
- A feature Keycloak cannot provide without custom SPI code
- Keycloak's memory footprint blocks the containerized stack in step 3

Review date: 2027-10-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 7/10 | ACCEPT PENDING DEFENSE (below target) |
| 1 (defense) | 2026-10-31 | architecture-kata-reviewer (drill) | — | REVISE: defense not passed (no build/run split, cost-only reason vs hosted IdP) |
| 2 | 2026-10-31 | architecture-kata-reviewer | 8/10 | ACCEPT PENDING DEFENSE |
| 2 (defense) | 2026-10-31 | architecture-kata-reviewer (drill) | — | ACCEPT. Defense: passed 2026-10-31 (scope: local/learning; production IdP in a new ADR at first deployment) |
