# 0008. Schema per service on one Postgres instance

| | |
|---|---|
| Status | Proposed |
| Date | 2026-10-24 (recorded). Decided 2026-08-23, #01 Phase 1, PR #1; extended per service in PR #2 and PR #3 |
| Category | architecture |
| Impact | normal |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5, retroactive) |
| Drivers | Microservice data ownership; laptop resources; T31, T32 |
| Supersedes | — |
| Superseded by | — (to be extended by the step 2 ADR on per-service database roles, finding #6) |

## Context and problem statement

Three services keep data: orders, products, profiles. Each should own its data so that no service depends on another's tables, and a schema change in one doesn't break the others. Everything runs on one laptop in docker compose.

How is the data of each service separated?

## Decision drivers

- Each service owns its tables and its migrations
- No service reads another service's tables
- Cheap to run locally: one container, one backup
- Can be tightened later without moving data

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **One instance, one database `secure_shop`, one schema per service, each service's Flyway owns its schema** | One container; clear ownership per schema; can be locked down with per-schema roles later | Isolation is by convention until roles exist; shared failure and resource limits |
| Database per service on one instance | Stronger separation: Postgres can't join across databases without an extension; separate connection limits | A bit more setup; same shared instance failure |
| Instance per service | Full isolation; independent versions and tuning | Three Postgres containers on a laptop; three backups |
| Shared schema, shared tables | Simplest; joins across services | Couples services through the schema; the opposite of the goal |

## Decision outcome

**Chosen: schema per service on one instance.**

- `order_schema`, `product_schema`, `user_schema` in database `secure_shop`
- Each service sets `currentSchema`, `spring.flyway.schemas`, `spring.flyway.default-schema` and `hibernate.default_schema` to its own schema, and runs its own Flyway migrations
- `spring.jpa.hibernate.ddl-auto=validate`: Hibernate checks the schema, Flyway changes it
- Rule: no service reads another service's schema. Data from another service comes through its API (Order Service gets prices from Product Service, [0005](0005-price-from-the-catalog-never-from-the-client.md))

Database per service was a close second; it is reopened in step 2, which decides between schema-plus-roles and database-per-service. Instance per service was rejected as too heavy for one laptop with no isolation need that per-database roles can't meet.

## One-minute answer

Each service has its own schema and its own Flyway migrations on one shared Postgres instance. That gives clear data ownership for the price of one container. The cost is that today the separation is only a convention: all three services connect as the same superuser, so any of them could read the others' tables until step 2 adds per-service roles.

## Consequences

### Positive

- Each service's tables and migrations live with its code
- One container and one data folder to manage

### Negative (residual risk)

- **Isolation is a convention, not enforced.** All services connect as `postgres`/`postgres`, the superuser (T31, 11 Medium; T32, 15 High). A bug or an injection in one service can read or change every schema. Step 2 (finding #6) adds per-service roles with rights on their own schema only
- **Shared instance.** One outage, one set of connection limits, one noisy neighbour
- **Superuser password in plain text** in every `application.properties` (T32, step 2)

## Verification

- Each service's `application.properties` names only its own schema
- No query in `backend/` references another service's schema (grep for the schema names finds only each service's own config)
- After step 2: a test or startup check that the service role cannot read another schema

## Migration path

Step 2: one Postgres role per service with rights only on its own schema (DML for the app role; whether Flyway gets a separate owner role is part of that ADR). Moving to database per service later is a dump and restore per schema, since no cross-schema queries exist.

## Revisit trigger

- Step 2 (finding #6): this ADR gets extended or superseded
- One service needing a different Postgres version, extension or tuning

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
