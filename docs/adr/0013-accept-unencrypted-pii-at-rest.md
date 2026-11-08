# 0013. Accepted risk: profile PII unencrypted at rest (T33)

| | |
|---|---|
| Status | Proposed |
| Date | 2026-10-24 |
| Category | security |
| Impact | normal |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5) |
| Drivers | T33 (PII stored unencrypted, 9 Medium); T31, T32 |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

User Service stores profiles in `user_schema.profiles`: `username`, `email`, `full_name`, `address`, `phone`, in plain `VARCHAR` columns. The Postgres data lives in `docker/postgres-data/` on the host, with no disk or column encryption. Passwords are not here; Keycloak holds them, hashed.

Two copies of part of this data exist. Registration sends `username` and `email` to Keycloak (`KeycloakAdminClient`), which keeps them, with the password hashes, in H2 under `docker/keycloak-data/`, also unencrypted. That folder leaking is T35 (10 Medium, secret scanning and Docker secrets in steps 3–4); this ADR covers the Postgres copy and says the same about the Keycloak copy.

Out of scope here: PII in transit. JDBC and every HTTP flow are plain text, accepted in [0014](0014-accept-no-tls-local-rnd.md) (T37).

The threat model scores this 9 Medium (T33) and plans to accept it for now.

Do we encrypt profile data at rest now, or accept the risk until a real deployment?

## Decision drivers

- Only test data exists; there is no real personal data to protect
- Encryption at rest only helps against someone reading the disk or a backup, not against someone reading through SQL
- The larger exposure today is the shared superuser with a default password (T31, T32), fixed in step 2
- The risk must come back before real data does

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **Accept: plain columns, rely on database access control (tightened in step 2)** | No work; nothing to manage | A copied data folder or backup shows the PII |
| Disk or volume encryption (host, or a managed database's storage encryption) | No code change; covers backups; usually one setting on a managed DB. On the laptop, full-disk encryption also covers `docker/keycloak-data/` and a lost or stolen machine | No protection from anyone who can query the database, or from anyone logged in to the running machine |
| Column encryption in the app (JPA `AttributeConverter`, AES-GCM, key from a KMS or secret) | Protects the fields even from database readers without the key | Key management and rotation; no search or index on encrypted fields; more code |
| `pgcrypto` in the database | No app crypto code | Keys pass through SQL and can end up in logs; same search limits |

## Decision outcome

**Chosen: accept the risk.** Status of T33 becomes `Accepted` when this ADR is accepted.

The same acceptance covers the `username` and `email` that Keycloak keeps in its H2 store.

Host disk encryption is not turned on for this: the laptop holds only test data, so it protects nothing today, and it's a machine setting outside what this repo decides. The revisit trigger turns it on first, before any real data.

Residual risk: unchanged, 9 Medium (D2/R1/E2/A3/D1). The control that matters more comes in step 2: a User Service role that can read only `user_schema`, and no default superuser password.

Controls that exist today: `docker/postgres-data/` and `docker/keycloak-data/` are gitignored, so the PII can't be committed by accident. The app's own log messages carry only the `keycloak_sub`, and `spring.jpa.show-sql=true` prints statements with `?` placeholders, not the bound values. One exception: a failed profile insert on the `UNIQUE` username (for example after wiping only `docker/keycloak-data/` and registering the same name again) logs the Postgres error detail, `Key (username)=(…) already exists`, through `ProfileService.compensate`, Hibernate's `SqlExceptionHelper` and the Postgres server log. Only the username, and only to the console (`docker logs` for Postgres); accepted with the rest.

## One-minute answer

We accept storing profile data unencrypted for now, because there is only test data and encryption at rest protects the disk, not the SQL access, which is the larger gap today. Fixing database access in step 2 reduces more risk than encrypting columns would. The cost: anyone who copies either data folder, a backup, or this laptop can read every profile.

## Consequences

### Positive

- No key management before there is anything to protect
- Effort goes to database access control first, which covers more threats

### Negative (residual risk)

- A copy of `docker/postgres-data/` or a backup exposes names, emails, addresses and phone numbers; a copy of `docker/keycloak-data/` exposes usernames and emails. That copy also holds password hashes, secrets and signing keys: that half is T35, still Open, and not accepted here
- The laptop's disk is not encrypted (Windows 11 Home, Device encryption off; checked 2026-11-08), so a lost or stolen machine exposes both folders. The PII part is accepted with the rest (only test data is on it); the Keycloak secrets part stays with T35
- Not acceptable for real users: GDPR-style rules expect appropriate protection of personal data

## Verification

- `V1__create_profiles_table.sql`: plain `VARCHAR` columns
- `docker-compose.yml`: Postgres data (`./postgres-data`) and Keycloak data (`./keycloak-data`) on plain bind mounts
- `.gitignore`: `docker/postgres-data/`, `docker/keycloak-data/` (`git check-ignore` confirms both)
- Host disk: not encrypted (Settings → Privacy & security → Device encryption, checked 2026-11-08 by the decider)
- `KeycloakAdminClient`: registration sends `username`, `email` and the password to Keycloak
- User Service log statements (`ProfileService`, `KeycloakAdminClient`) put only the `keycloak_sub` in their messages; `ProfileService.compensate` also logs the exception, which can carry the username (see Decision outcome)

## Migration path

Before a real deployment: storage encryption first (cheap, often one setting on a managed database). Column encryption only for fields that need it against database-level readers, with the key from a secret manager. Accepting that encrypted fields can't be searched or indexed is part of that later ADR.

## Revisit trigger

- Any real personal data
- Any deployment outside a local machine
- Any real personal data in either folder on this laptop: turning on disk encryption comes first, before any app-level work

Review date: 2027-04-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 0 | 2026-10-24 | architecture-kata-reviewer (same session as drafting, baseline) | 9/10 | ACCEPT |
| 1 | 2026-10-24 | architecture-kata-reviewer | 9.0/10 | ACCEPT |
| 2 | 2026-11-08 | architecture-kata-reviewer | 8.0/10 | ACCEPT |
| 3 | 2026-11-08 | architecture-kata-reviewer (same session as the edit) | 8.5/10 | ACCEPT |
| 4 | 2026-11-08 | architecture-kata-reviewer (same session as the edit) | 10/10 | ACCEPT |
| 5 | 2026-11-08 | architecture-kata-reviewer (fresh session) | 9.5/10 | ACCEPT. Confirms rounds 3–4 fixes; round 4's 10 corrected to 9.5: data minimization (`address`/`phone` read by nothing else) not weighed as an option. Nits: "backup" vs 0012's no-backup decision; Tomcat logs the rethrown constraint violation too |
