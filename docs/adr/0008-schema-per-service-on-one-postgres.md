# 0008. Schema per service on one Postgres instance

| | |
|---|---|
| Status | Accepted |
| Date | 2026-10-24 (recorded). Decided 2026-08-23, #01 Phase 1, PR #1; extended per service in PR #2 and PR #3 |
| Category | architecture |
| Impact | normal |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5, retroactive) |
| Drivers | Microservice data ownership; laptop resources; T31, T32 |
| Supersedes | — |
| Superseded by | — (to be extended by the step 2 ADR on per-service database roles, finding #6) |

## Context and problem statement

Three Spring Boot services keep data: `order-service` (table `orders`), `product-service` (`products`) and `user-service` (`profiles`). Each has its own Flyway migrations. Each should own its data, so that no service depends on another's tables and a schema change in one doesn't break the others.

Everything runs on one laptop. Docker compose runs the infrastructure: one `postgres:16` container (database `secure_shop`, data in `docker/postgres-data`) and Keycloak. The services run on the host and connect to `localhost:5432`. Every extra database container costs memory, startup time and another backup, and this R&D project has no load and no need for a separate Postgres version or tuning per service.

The choice also sets how far a compromised service reaches. With a bug or an SQL injection in one service, the question is whether it can read or change the others' data (T31, T32).

Keycloak keeps its own store (H2 in `docker/keycloak-data`) and is not part of this decision.

How is the data of each service separated?

## Decision drivers

- Each service owns its tables and its migrations
- No service reads another service's tables
- Cheap to run locally: one container, one backup
- Can be tightened later without moving data

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **One instance, one database `secure_shop`, one schema per service, each service's Flyway owns its schema** | One container; clear ownership per schema; compose's `POSTGRES_DB` creates the one database, no init script; one `pg_dump`; can be locked down with per-schema roles later (`REVOKE ALL ON SCHEMA ... FROM PUBLIC`, `GRANT USAGE` to the owning role) | Isolation is by convention until roles exist: a schema-qualified query (`product_schema.products`) works for any role with rights on it, including the superuser; shared failure and resource limits |
| Database per service on one instance | Cross-service queries are impossible even by mistake, even as superuser: Postgres has no cross-database joins without `dblink` or `postgres_fdw`; per-database `CONNECT` privilege, `CONNECTION LIMIT`, settings (`ALTER DATABASE ... SET`) and extensions; `pg_dump` per service natively; moving one service to its own instance later is one dump | Small setup cost: an init script in `/docker-entrypoint-initdb.d` to create three databases, since `POSTGRES_DB` creates only one; still one shared instance; the superuser can still connect to every database, so against a compromised service it gives no more isolation than schemas until roles exist |
| Instance per service | Full isolation; independent versions and tuning | Three Postgres containers on a laptop; three backups |
| Shared schema, shared tables | Simplest; joins across services | Couples services through the schema; the opposite of the goal |

## Decision outcome

**Chosen: schema per service on one instance.**

- `order_schema`, `product_schema`, `user_schema` in database `secure_shop`
- Each service sets `currentSchema`, `spring.flyway.schemas`, `spring.flyway.default-schema` and `hibernate.default_schema` to its own schema, and runs its own Flyway migrations
- `spring.jpa.hibernate.ddl-auto=validate`: Hibernate checks the schema, Flyway changes it
- Rule: no service reads another service's schema. Data from another service comes through its API (Order Service gets prices from Product Service, [0005](0005-price-from-the-catalog-never-from-the-client.md))

Why schema beat database per service, driver by driver:

| Driver | Schema per service | Database per service |
|---|---|---|
| Each service owns its tables and migrations | Met: own schema, own Flyway history table in it | Met equally |
| No service reads another's tables | By convention today, plus a CI check (see Verification); enforced by roles in step 2 | Stronger against mistakes: a cross-database query fails. Against a compromised service the same as schema, because everyone is superuser until step 2 |
| Cheap to run locally | One database from `POSTGRES_DB`, one dump | Needs an init script and three dumps; small but real |
| Can be tightened later without moving data | Yes: step 2 is `GRANT`/`REVOKE` on the existing schemas | Yes: per-database `CONNECT` grants |

The deciding point: database per service's one real advantage, preventing an accidental cross-service query, is covered at lower cost by the CI check, and its security advantage only appears once per-service roles exist, which give schemas the same enforcement. Both keep the move open: schema to database is a dump and restore per schema.

Database per service was a close second; it is reopened in step 2, which decides between schema-plus-roles and database-per-service. Instance per service was rejected as too heavy for one laptop with no isolation need that per-database roles can't meet.

## One-minute answer

Each service has its own schema and its own Flyway migrations on one shared Postgres instance. That gives clear data ownership for the price of one container. The cost is that today the separation is only a convention: all three services connect as the same superuser, so any of them could read the others' tables until step 2 adds per-service roles.

## Consequences

### Positive

- Each service's tables and migrations live with its code
- One container and one data folder to manage

### Negative (residual risk)

- **Isolation is a convention, not enforced.** All services connect as `postgres`/`postgres`, the superuser (T31, 11 Medium; T32, 15 High). A bug or an injection in one service can read or change every schema. Step 2 (finding #6) adds per-service roles with rights on their own schema only
- **Superuser reaches past the database.** An SQL injection running as `postgres` can use `COPY ... TO/FROM PROGRAM` to run shell commands in the Postgres container, and `pg_read_server_files` rights to read its files. One injection is a foothold in the container, not only in the data. Same fix: non-superuser service roles in step 2
- **No foreign keys across services.** `orders.product_id` points at `product_schema.products.id` and `orders.user_sub` at a Keycloak subject, with no database constraint. Deleting a product leaves orders with a dangling `product_id`, and nothing in the database stops it. Integrity is the services' job: Order Service checks the product through the Product API when the order is placed ([0005](0005-price-from-the-catalog-never-from-the-client.md)), and orders copy the price they need (`unit_price`) so they stay readable after the product changes. `DELETE /products/{id}` removes the row (no soft delete), so an order's product link can break
- **Shared instance.** One outage, one set of connection limits, one noisy neighbour
- **Superuser password in plain text** in every `application.properties` and in `docker/docker-compose.yml`, and port 5432 is published on all interfaces (T32: credentials and roles in step 2, port not published in step 4)

## Verification

- Each service's `application.properties` names only its own schema
- No code in a service references another service's schema: CI step "Check no cross-schema references" in `.github/workflows/build-service.yml` greps the service's `src/` for `order_schema`, `product_schema` and `user_schema` and fails on any name but its own (the Gateway may name none). It catches schema-qualified references only; an unqualified table name already fails at runtime, because each service's `currentSchema` holds only its own tables
- After step 2: a test or startup check that the service role cannot read another schema

## Migration path

Step 2: one Postgres role per service with rights only on its own schema (DML for the app role; whether Flyway gets a separate owner role is part of that ADR). Moving to database per service later is a dump and restore per schema, since no cross-schema queries exist.

## Revisit trigger

- Step 2 (finding #6): this ADR gets extended or superseded
- One service needing a different Postgres version, extension or tuning

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-11-08 | architecture-kata-reviewer | 7.5/10 | ACCEPT (below target) |
| 2 | 2026-11-08 | architecture-kata-reviewer | 9.5/10 | ACCEPT |
