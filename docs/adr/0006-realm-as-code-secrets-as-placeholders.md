# 0006. Realm as code, secrets as `${...}` placeholders

| | |
|---|---|
| Status | Proposed |
| Date | 2026-10-24 (recorded). Decided 2026-10-03, #01 Phase 4, PR #4 |
| Category | security |
| Impact | normal |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5, retroactive) |
| Drivers | Reproducible setup; T26, T35, T43 |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

Until Phase 4 the Keycloak realm was set up by hand in the admin console, following notes. Clients, scopes, mappers, roles and test users all had to be clicked together again after every reset, and nobody could review a change to them.

The realm holds two kinds of credential:

- **Three client secrets** (Gateway, test client, User Service admin client)
- **Two seeded users with passwords**: `testuser` and `adminuser`, the second with the `admin` realm role. `adminuser` logs in through the real browser flow (`secure-shop-gateway`), not only through the dev test client

In this ADR, a secret is any value that authenticates against an imported realm. That covers both kinds, not only the client secrets. Each one has to be either kept out of git or knowingly committed as a dev-only fixture, and this ADR says which.

How is the realm's configuration stored and applied, and where do its secrets, client and user, come from?

## Decision drivers

- One command recreates the whole realm
- Realm changes show up in a diff and get reviewed like code
- No usable secret in the repository, except dev-only fixtures this ADR names
- No extra tooling in v1

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **Committed realm export, imported by `start-dev --import-realm`; client secrets as `${VAR}` placeholders filled from `docker/.env`** | Built into Keycloak; one file; secrets stay out of git | Import runs only when the realm doesn't exist yet; the export is large and noisy; console edits drift from the file |
| Manual setup, documented steps | Nothing to maintain except the notes | Not reproducible; slow; not reviewable |
| Declarative tool (keycloak-config-cli or the Terraform Keycloak provider) applied on every start | Idempotent: re-applies changes to an existing realm; small, readable config | Another tool and container; more to learn before the security work |
| Export with real secrets committed | Simplest import | Secrets in git history |

## Decision outcome

**Chosen: committed export at `docker/keycloak/secure-shop-realm.json`, imported on first start. Client secrets come from `docker/.env`; the seeded users are dev-only fixtures with a known password.**

Client secrets:

- The three client secrets in the file are `${KEYCLOAK_GATEWAY_CLIENT_SECRET}`, `${KEYCLOAK_TEST_CLIENT_SECRET}` and `${KEYCLOAK_ADMIN_CLIENT_SECRET}`. Keycloak substitutes them from the container environment at import
- `docker-compose.yml` passes them with `${VAR:?set it in docker/.env}`, so compose refuses to start without them
- `docker/.env` is gitignored; `docker/.env.example` lists the variables
- The import is skipped when `docker/keycloak-data/` already holds the realm; wiping that folder re-imports

Seeded users:

- `testuser` and `adminuser` keep the committed password `test` (`"temporary": false`). They are **dev-only fixtures**, not secrets kept out of git: anyone with the repo knows them, by design
- This is accepted because the stack is local only. Keycloak runs on one developer machine, there are no real users, and the realm is reached only through `localhost` (same scope as ADR-0014). Postman and Newman need a known user and a known admin to exercise both roles
- The fixtures must not reach any non-dev import. Step 4 already plans to move the dev test client out of non-dev imports (T26), and the two users move with it in the same split
- The acceptance ends when the realm is imported anywhere outside a developer machine. From then on, seeded passwords become `${...}` placeholders, or the users are removed

The declarative tool was rejected for v1 as extra tooling, but it is the better fit once the realm needs changes applied without a wipe or per-environment variants (see revisit trigger).

## One-minute answer

The Keycloak realm is a committed JSON file imported at startup, and its client secrets are placeholders filled from an uncommitted `.env` file. So the whole identity setup is reproducible and reviewable, and no client secret is in git. Two seeded users, one of them an admin, do have the committed password `test`. That is accepted as a dev-only fixture while everything runs on one laptop, and step 4 keeps them out of any non-dev import. The cost is that Keycloak only imports into an empty realm, so changing the file means wiping Keycloak's data, and console edits can drift from the file.

## Consequences

### Positive

- `docker compose up` gives a working realm on a clean machine
- Realm changes are reviewed in PRs; insecure settings are visible in the file, which is how the threat model found them

### Negative (residual risk)

- **Import only into an empty realm.** Applying a change means deleting `docker/keycloak-data/`, which also deletes registered users
- **Drift.** An edit made in the admin console is not in the file until someone exports again
- **Noisy file.** About 2,900 lines, mostly Keycloak defaults; a real change is easy to miss in review
- **Insecure defaults travel with it.** `bruteForceProtected: false`, `revokeRefreshToken: false` (T25, T29)
- **The dev-only test client is in every import** (T26, 12 High, step 4 moves it out of non-dev imports)
- **A known admin password is in every import.** `adminuser`/`test` has the `admin` realm role and logs in through the real Gateway login, so anyone who has read the repo can change or delete the catalog wherever the realm is imported. `testuser`/`test` gives a normal user session the same way. Together with T26 it is also a ready-made password grant for an admin (T43, 13 High, step 4 moves both users out of non-dev imports)
- **Secrets sit in plain text on the host** in `docker/.env` (T35, step 3 secret scanning, step 4 Docker secrets)

## Verification

- `grep '"secret"' docker/keycloak/secure-shop-realm.json` shows only `${...}` values
- `grep -n '"type": "password"' docker/keycloak/secure-shop-realm.json` lists exactly the two fixture users (lines 492 and 505), each with `"value": "test"`. Any other match is a new committed credential and needs a `${...}` placeholder
- Compose fails with "set it in docker/.env" when a variable is missing
- Step 3 adds secret scanning in CI over the realm file too, which turns "no usable secret in the repo" into a check. Its allowlist names only the two fixture passwords, so any other credential fails the build

## Migration path

Step 4 (realm hardening) is the natural point to move to keycloak-config-cli: the hardening changes have to land on existing realms, and dev and non-dev need different client sets.

## Revisit trigger

- A second environment with different clients or settings
- A realm change that must not wipe user data
- The realm is imported anywhere outside a developer machine: the seeded-user fixtures end (see Decision outcome)

Review date: 2027-04-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
