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

The seeded users need their own choice:

| Option | Pros | Cons |
|---|---|---|
| **Dev-only fixtures: committed password `test`** | Same value in the realm, the Postman collection and the docs; works after any fresh import with nothing to set | A known admin password in every import (T43); safe only while nothing leaves the developer machine |
| Password placeholders (`${KEYCLOAK_TESTUSER_PASSWORD}`, `${KEYCLOAK_ADMINUSER_PASSWORD}`) from `docker/.env` | Same mechanism as the client secrets; nothing usable in git | The collection and docs also hold the password, so they would need collection variables and doc changes, and every developer picks their own value. Buys nothing while the stack is local |
| `"temporary": true` | Forces a change at first login | Breaks the Postman password grant and the dev login; the first login still uses `test` |
| No seeded users; a setup script creates them via the Admin API | Nothing in the realm file | One more script to run after every wipe; the script still holds or reads a password |

## Decision outcome

**Chosen: committed export at `docker/keycloak/secure-shop-realm.json`, imported on first start. Client secrets come from `docker/.env`; the seeded users are dev-only fixtures with a known password.**

Client secrets:

- The three client secrets in the file are `${KEYCLOAK_GATEWAY_CLIENT_SECRET}`, `${KEYCLOAK_TEST_CLIENT_SECRET}` and `${KEYCLOAK_ADMIN_CLIENT_SECRET}`. Keycloak substitutes them from the container environment at import
- `docker-compose.yml` passes them with `${VAR:?set it in docker/.env}`, so compose refuses to start without them
- `docker/.env` is gitignored; `docker/.env.example` lists the variables
- The import is skipped when `docker/keycloak-data/` already holds the realm; wiping that folder re-imports

Seeded users:

- `testuser` and `adminuser` keep the committed password `test` (`"temporary": false`). They are **dev-only fixtures**, not secrets kept out of git: anyone with the repo knows them, by design
- This is accepted because the stack runs on one developer machine with no real users (same scope as ADR-0014). The ports are **not** limited to localhost: compose publishes Keycloak's `8081` on all interfaces, and the apps set no `server.address`. What limits the known password from the LAN today:
  - The Gateway's only redirect URI and every `issuer-uri` are `http://localhost:…`, so a browser login from another machine doesn't complete
  - The only password-grant client whose tokens carry the services' audiences, `secure-shop-test-client`, needs its secret from `docker/.env` (T26)
  - The realm's built-in `admin-cli` is public with direct grants on, so anyone on the LAN can get an `adminuser` token from it. The services reject it only because `admin-cli` doesn't have the `secure-shop-audience` scope and every service checks audience (T10)
  These limits are incidental, not designed. Step 4 already binds Keycloak's port to localhost (T27), which closes the `admin-cli` path by configuration rather than by a missing scope
- Fixtures beat placeholders here because the password is shared: the Postman collection (`password`, `adminPassword` variables), `README.md` and `docs/development.md` all use `test`. A placeholder would move the problem into those files and give every developer a different value, while removing nothing an attacker on this machine couldn't read anyway
- The fixtures must not reach any non-dev import. Step 4 already plans to move the dev test client out of non-dev imports (T26), and the two users move with it in the same split
- The acceptance ends when the realm is imported anywhere outside a developer machine. From then on, seeded passwords become `${...}` placeholders, or the users are removed, and the collection and docs change in the same PR

The declarative tool was rejected for v1 as extra tooling, but it is the better fit once the realm needs changes applied without a wipe or per-environment variants (see revisit trigger).

## One-minute answer

The Keycloak realm is a committed JSON file imported at startup, and its client secrets are placeholders filled from an uncommitted `.env` file. So the whole identity setup is reproducible and reviewable, and no client secret is in git. Two seeded users, one of them an admin, do have the committed password `test`. That is accepted as a dev-only fixture shared with the Postman collection while everything runs on one laptop; step 4 binds Keycloak's port to localhost and keeps the users out of any non-dev import. The cost is that Keycloak only imports into an empty realm, so changing the file means wiping Keycloak's data, and console edits can drift from the file.

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
- `git grep -n -E 'adminuser|testuser|"(adminP|p)assword"' -- postman README.md docs/development.md` lists every other file that carries the fixture password; they change together with the realm file
- Planned, step 3: secret scanning in CI. Default scanner rules don't flag a short JSON value like `"value": "test"`, so step 3 needs a custom rule over the realm's `credentials` values that allowlists only the two fixtures. Until then, the grep above is the check

## Migration path

Step 4 (realm hardening) is the natural point to move to keycloak-config-cli: the hardening changes have to land on existing realms, and dev and non-dev need different client and user sets: the test client and both seeded users exist only in the dev variant.

## Revisit trigger

- A second environment with different clients or settings
- A realm change that must not wipe user data
- The realm is imported anywhere outside a developer machine: the seeded-user fixtures end (see Decision outcome)

Review date: 2027-04-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 6/10 (G3) | REVISE |
| 2 | 2026-11-07 | architecture-kata-reviewer (same session as revision, provisional) | 8/10 | ACCEPT |
| 3 | 2026-11-07 | architecture-kata-reviewer (confirmed in fresh session) | 9/10 | ACCEPT |
