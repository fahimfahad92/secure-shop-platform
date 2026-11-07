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
| Disk or volume encryption (host, or a managed database's storage encryption) | No code change; covers backups; usually one setting on a managed DB | No protection from anyone who can query the database |
| Column encryption in the app (JPA `AttributeConverter`, AES-GCM, key from a KMS or secret) | Protects the fields even from database readers without the key | Key management and rotation; no search or index on encrypted fields; more code |
| `pgcrypto` in the database | No app crypto code | Keys pass through SQL and can end up in logs; same search limits |

## Decision outcome

**Chosen: accept the risk.** Status of T33 becomes `Accepted` when this ADR is accepted.

Residual risk: unchanged, 9 Medium (D2/R1/E2/A3/D1). The control that matters more comes in step 2: a User Service role that can read only `user_schema`, and no default superuser password.

## One-minute answer

We accept storing profile data unencrypted for now, because there is only test data and encryption at rest protects the disk, not the SQL access, which is the larger gap today. Fixing database access in step 2 reduces more risk than encrypting columns would. Before any real data, storage encryption is the default, plus column encryption for the fields that need it.

## Consequences

### Positive

- No key management before there is anything to protect
- Effort goes to database access control first, which covers more threats

### Negative (residual risk)

- A copy of `docker/postgres-data/` or a backup exposes names, emails, addresses and phone numbers
- Not acceptable for real users: GDPR-style rules expect appropriate protection of personal data

## Verification

- `V1__create_profiles_table.sql`: plain `VARCHAR` columns
- `docker-compose.yml`: Postgres data on a plain bind mount

## Migration path

Before a real deployment: storage encryption first (cheap, often one setting on a managed database). Column encryption only for fields that need it against database-level readers, with the key from a secret manager. Accepting that encrypted fields can't be searched or indexed is part of that later ADR.

## Revisit trigger

- Any real personal data
- Any deployment outside a local machine

Review date: 2027-04-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
