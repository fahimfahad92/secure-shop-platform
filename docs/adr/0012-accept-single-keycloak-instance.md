# 0012. Accepted risk: single Keycloak instance (T30)

| | |
|---|---|
| Status | Accepted |
| Date | 2026-10-24 |
| Category | security |
| Impact | normal |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5) |
| Drivers | T30 (Keycloak down, 10 Medium) |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

Keycloak is the only Authorization Server ([0001](0001-keycloak-as-the-only-authorization-server.md)) and runs as one container in `start-dev` mode with H2 storage. When it is down:

- Nobody can log in or register
- The Gateway can't refresh tokens, so sessions turn into `401` once their access token expires (`accessTokenLifespan` is 300 s): the failed refresh is a `ClientAuthorizationException`, which `RouteConfig` maps to `401`
- The Gateway doesn't start, because it reads Keycloak's discovery document at startup (`spring.security.oauth2.client.provider.keycloak.issuer-uri`)
- Requests with a still-valid access token keep working at services that are already running: they validate with the signing keys they already fetched
- A service restarted during the outage starts (Spring Boot resolves `resourceserver.jwt.issuer-uri` lazily), but can't validate any token until Keycloak is back, because it has no keys yet

The threat model scores this 10 Medium (T30) and plans to accept it rather than fix it.

Do we make Keycloak highly available, or accept the outage risk?

## Decision drivers

- The project runs on one laptop with test data and no real users
- HA needs production-mode Keycloak with an external database, which is a project of its own
- The risk must stay visible and come back for review

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **Accept: one instance, `restart: unless-stopped`, review later** | No work; matches a single-machine project | Login outage whenever Keycloak is down |
| Accept, plus cheap mitigations: a backup of `docker/keycloak-data/`, explicit Gateway endpoints (`authorization-uri`, `token-uri`, `jwk-set-uri`, `user-info-uri`) instead of `issuer-uri` discovery, or single-node production mode on the Postgres that already runs | Each is small: a copied folder, four properties, a database and a hostname. Durability without HA; a Gateway that starts while Keycloak is down | Protects data and uptime nobody needs here: one user, test data, never going live. The explicit endpoints duplicate the realm's discovery document and drift from it, and they hide the Gateway–Keycloak dependency that this project wants to see and debug |
| Keycloak cluster: two or more nodes in production mode, shared Postgres, distributed cache | No single point of failure for login | Production mode needs a fixed hostname and HTTPS (HTTP only with an explicit `--http-enabled=true`); an external DB; two JVMs on a laptop; large effort for no real users |
| Hosted IdP with an SLA | Availability is the vendor's problem | For the local phase, reverses [0001](0001-keycloak-as-the-only-authorization-server.md). Not ruled out for production: 0001 leaves the production IdP (Keycloak on ECS or a hosted IdP) to a new ADR at first deployment |

## Decision outcome

**Chosen: accept the risk.** Status of T30 becomes `Accepted` when this ADR is accepted.

The cheap mitigations are rejected too, on purpose: no backup of `keycloak-data`, and the Gateway keeps `issuer-uri` discovery. With one user, test data and no plan to go live, uptime and durability are out of scope, and a Gateway that fails to start without Keycloak is a dependency worth learning to debug, not one to hide.

Residual risk: unchanged, 10 Medium (D2/R2/E2/A3/D1). T30 covers loss of Keycloak's data as well as downtime: both leave users unable to log in, and this ADR accepts both. Current partial controls: compose restarts the container (`restart: unless-stopped`, which only covers a process that exits: there is no `healthcheck`, so a hung but running Keycloak is not restarted), and already-issued access tokens keep working for up to 5 minutes.

## One-minute answer

We accept running one Keycloak instance, so a Keycloak outage stops login, registration and token refresh. With one machine and no real users, clustering Keycloak would cost more than it protects. The cost is a known single point of failure, reviewed again before any real deployment.

## Consequences

### Positive

- No HA work before it matters
- The outage behaviour is written down, so nobody is surprised by it

### Negative (residual risk)

- Login, registration and refresh are down while Keycloak is down; logged-in users get `401` within 5 minutes. After an outage longer than 30 minutes (`ssoSessionIdleTimeout: 1800`) the SSO session is gone and everyone logs in again. Whether a session recovers after a shorter outage, once the refresh succeeds again, is not tested
- The Gateway can't start without Keycloak, so the two can't be restarted independently
- `start-dev` with H2 is not supported for production, so HA is not a config change: moving to production mode and Postgres comes first
- **No backup of Keycloak's data.** Users, password hashes and signing keys live only in H2 under `docker/keycloak-data/`. Accepted under T30, not a separate risk (T35 is the same folder leaking, not being lost). Losing that folder re-imports the realm with only the seeded users: everyone who registered through `/users/register` is gone, and their profiles and orders in Postgres point to subjects that no longer exist ([development.md](../development.md)). Recovery is "register again", which is fine for test data only

## Verification

- `docker/docker-compose.yml`: one `keycloak` service, `start-dev`, `restart: unless-stopped`
- Manual check: stop Keycloak, observe `401` after the access token expires and a Gateway that refuses to start; restart a service during the outage and observe that it starts but rejects tokens
- No automated test covers the outage behaviour

## Migration path

No step plans this: a production deployment is out of scope ([0001](0001-keycloak-as-the-only-authorization-server.md)). Before anything runs beyond one machine:

1. Production mode (`start`) with Postgres as Keycloak's database, a fixed hostname and HTTPS (the TLS work [0014](0014-accept-no-tls-local-rnd.md) lists for that point)
2. Two nodes behind a load balancer with the distributed cache
3. Health checks and alerting (step 6 observability)

A backup of `docker/keycloak-data/` (or a realm export with users) is rejected for now (Decision outcome) and comes back with the "registered users becoming data someone would miss" trigger.

## Revisit trigger

- Any deployment with real users
- Keycloak moving to production mode for any other reason
- Registered users becoming data someone would miss (losing `keycloak-data` stops being "register again")

Review date: 2027-04-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 0 | 2026-10-24 | architecture-kata-reviewer (same session as drafting, baseline) | 8.5/10 | ACCEPT |
| 1 (pre) | 2026-10-24 | architecture-kata-reviewer | 8/10 | ACCEPT |
| 1 | 2026-11-08 | architecture-kata-reviewer | 8.5/10 | ACCEPT. Durability and service-restart gaps from 10-24 closed. Open: backup called "cheap" but not decided (F1); Gateway explicit endpoints and single-node production mode not weighed (F2); data-loss risk pinned to T35, which is disclosure (F3). Optional: 30-min SSO idle bound (F5), no healthcheck behind `restart: unless-stopped` (F6), hosted IdP "for the local phase" (F7) |
| 1 (drill) | 2026-11-08 | architecture-kata-reviewer (drill) | 8.5/10 | ACCEPT. Objection answered (not required, Impact normal): one user, never going live, little user data, so uptime and working through an outage are out of scope; a Gateway that fails without Keycloak is a dependency to learn to debug, and hard-coded endpoints would hide it and drift from the realm. Recommended: record the rejection of the backup and the Gateway change in the ADR (options row + one Decision sentence), stating that startup failure is wanted for learning |
