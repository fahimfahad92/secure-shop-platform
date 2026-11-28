# 6 — Shared hardcoded DB superuser

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | SB-CFG-05 |
| Service | order, product, user |

**Where:** `application.properties:4-5` in order-service, product-service, user-service (`spring.datasource.username=postgres`, `password=postgres`)

**What:** All three services connect as the same Postgres superuser with credentials committed in the repo.

**Why it matters:** Schema-per-service is only a convention: order-service could read or modify `user_schema`. A bug or compromise in one service reaches all data. Also inconsistent with the project's own rule for Keycloak secrets (env vars, fail fast). Local-only today, so MINOR — but it's a direct input for the #05 threat model.

**Fix direction:** `${DB_USERNAME}` / `${DB_PASSWORD}` from `docker/.env`; in `docker/init/01-schemas.sql` create one role per service with grants only on its own schema; Flyway runs as that role.

**Understand first:** Postgres roles, `GRANT USAGE/CREATE ON SCHEMA`, `ALTER DEFAULT PRIVILEGES`; database-per-service vs schema-per-service.
