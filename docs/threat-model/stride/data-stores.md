# STRIDE — Data stores

Legend, status values and how to update: [../README.md](../README.md). Diagram and IDs (`E…`, `F…`, `TB…`): [../system.md](../system.md). Scores and status per threat: [../risk-register.md](../risk-register.md).

| ID | Store | STRIDE | Threat | Control today | Gap |
|---|---|---|---|---|---|
| T31 | E9 Postgres | E/T | One superuser for all services: a bug or SQL injection in any service can read or change every schema and run DDL | JPA with bound parameters, no native SQL found; each service only *uses* its own schema | Shared `postgres` role (review finding #6) |
| T32 | E9 Postgres | E | `postgres`/`postgres` on a port published to all interfaces (TB7): full database for anyone on the network | — | Default credentials, `5432:5432` binding |
| T33 | E9 Postgres | I | Profile PII (name, email, address, phone) stored in plain columns | Local only | No encryption at rest; acceptable for now, revisit with any real deployment |
| T34 | E3 Session store | I | Memory dump of the Gateway exposes every user's tokens | No Actuator on the classpath, so no `/heapdump` endpoint | Needs host access; step 4 decides Actuator exposure when it's added |
| T35 | E8 Keycloak store, E10 files | I | Host files leak: H2 store (password hashes, secrets, private keys), `docker/.env` (client secrets) | `.env` and `keycloak-data/` gitignored; realm export holds `${...}` only | No secret scanning yet (step 3) |
| T36 | E9 Postgres | R | No history of who changed what: `PUT` overwrites rows, only `updated_at` moves | `user_sub`, `created_at`, `updated_at` columns | No audit log anywhere (also T28) |

