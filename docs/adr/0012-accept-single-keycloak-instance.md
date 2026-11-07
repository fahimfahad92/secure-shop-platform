# 0012. Accepted risk: single Keycloak instance (T30)

| | |
|---|---|
| Status | Proposed |
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
- The Gateway can't refresh tokens, so sessions turn into `401` once their access token expires (`accessTokenLifespan` is 300 s)
- The Gateway doesn't start, because it reads Keycloak's discovery document at startup
- Requests with a still-valid access token keep working: services validate with the signing keys they already fetched

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
| Keycloak cluster: two or more nodes in production mode, shared Postgres, distributed cache | No single point of failure for login | Production mode needs TLS and a hostname; an external DB; two JVMs on a laptop; large effort for no real users |
| Hosted IdP with an SLA | Availability is the vendor's problem | Reverses [0001](0001-keycloak-as-the-only-authorization-server.md) |

## Decision outcome

**Chosen: accept the risk.** Status of T30 becomes `Accepted` when this ADR is accepted.

Residual risk: unchanged, 10 Medium (D2/R2/E2/A3/D1). Current partial controls: compose restarts the container (`restart: unless-stopped`), and already-issued access tokens keep working for up to 5 minutes.

## One-minute answer

We accept running one Keycloak instance, so a Keycloak outage stops login, registration and token refresh. With one machine and no real users, clustering Keycloak would cost more than it protects. The cost is a known single point of failure, reviewed again before any real deployment.

## Consequences

### Positive

- No HA work before it matters
- The outage behaviour is written down, so nobody is surprised by it

### Negative (residual risk)

- Login, registration and refresh are down while Keycloak is down; logged-in users drop out within 5 minutes
- The Gateway can't start without Keycloak, so the two can't be restarted independently
- `start-dev` with H2 is not supported for production, so HA is not a config change: moving to production mode and Postgres comes first

## Verification

- `docker/docker-compose.yml`: one `keycloak` service, `start-dev`, `restart: unless-stopped`
- Manual check: stop Keycloak, observe `401` after the access token expires and a Gateway that refuses to start

## Migration path

1. Production mode (`start`) with Postgres as Keycloak's database and TLS (fits step 4)
2. Two nodes behind a load balancer with the distributed cache
3. Health checks and alerting (step 6 observability)

## Revisit trigger

- Any deployment with real users
- Keycloak moving to production mode for another reason (step 4)

Review date: 2027-04-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
