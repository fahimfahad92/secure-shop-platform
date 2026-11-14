# Development

Running the stack locally, trying it out, testing, and CI.

## Ports

| What | Port | Started by |
|---|---|---|
| Order Service | `8080` | `./mvnw spring-boot:run` |
| Keycloak | `8081` | Docker Compose |
| Product Service | `8082` | `./mvnw spring-boot:run` |
| User Service | `8083` | `./mvnw spring-boot:run` |
| Gateway | `8090` | `./mvnw spring-boot:run` |
| Postgres | `5432` | Docker Compose |

## Run the full stack

You need Docker and JDK 21. Maven comes with each service (`./mvnw`).

1. Create the secrets file (first time only):
   ```bash
   cp docker/.env.example docker/.env
   ```
   Fill in the three values with random strings (`openssl rand -hex 32`). Details: [keycloak-setup.md](keycloak-setup.md).

2. Start Postgres and Keycloak:
   ```bash
   docker compose -f docker/docker-compose.yml up -d
   ```
   On the first start Keycloak imports the `secure-shop` realm. Wait until `http://localhost:8081` answers.

3. Load the secrets into your shell:
   ```bash
   set -a; . docker/.env; set +a
   ```

4. Start the services, one terminal each:
   ```bash
   cd backend/product-service && ./mvnw spring-boot:run
   cd backend/order-service   && ./mvnw spring-boot:run
   cd backend/user-service    && ./mvnw spring-boot:run
   cd backend/gateway         && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```
   Drop `-Dspring-boot.run.profiles=dev` to run the Gateway without dev login.

Each service creates its own schema with Flyway on first start. The product catalog starts empty.

<details>
<summary>Start order and what needs what</summary>

| Service | Needs running first | Needs secret |
|---|---|---|
| Product Service | Postgres | None |
| Order Service | Postgres. Product Service only when an order is placed | None |
| User Service | Postgres. Keycloak only when someone registers | `KEYCLOAK_ADMIN_CLIENT_SECRET` |
| Gateway | Keycloak (reads its discovery document at startup) | `KEYCLOAK_GATEWAY_CLIENT_SECRET`, plus `KEYCLOAK_TEST_CLIENT_SECRET` with the `dev` profile |

All three resource servers fetch Keycloak's signing keys on the first request that carries a token, so they start without Keycloak.

</details>

<details>
<summary>Running from IntelliJ</summary>

- Set the three secrets as environment variables in each run configuration, or as user environment variables in the OS.
- If you set them in the OS, restart IntelliJ completely. A program only sees the environment variables that existed when it started, and IntelliJ passes its own environment to the apps it runs.
- A missing secret stops the Gateway and User Service at startup with a message naming the variable. Without that check, Spring Boot would start with the literal text `${...}` as the secret and fail later with `401` from Keycloak.

</details>

## Try it

### Postman

Import [`postman/secure-shop-platform.postman_collection.json`](../postman/secure-shop-platform.postman_collection.json) and set the `clientSecret` collection variable to your `KEYCLOAK_TEST_CLIENT_SECRET`.

| Folder | Talks to | Start with |
|---|---|---|
| Auth | Keycloak directly | **Get Token** or **Get Admin Token**. Stores the token for the service folders |
| Gateway (BFF) | Gateway `:8090` | **Dev Login (testuser)**. Needs the Gateway's `dev` profile |
| Product Service | Product `:8082` directly | **Auth → Get Admin Token** for writes |
| Order Service | Order `:8080` directly | **Auth → Get Token** |
| User Service | User `:8083` directly | **Register**, then **Auth → Get Token (registered user)** |

The catalog starts empty. Create a product first (Product Service → **Create Product (admin)**), or orders fail with `400` for an unknown product.

The folders that call services directly skip the Gateway and send the bearer token themselves. They test each service's own security. The Gateway folder tests the flow a real browser uses.

### curl

| Service | File |
|---|---|
| Gateway | Examples in [gateway.md → Dev login](services/gateway.md#dev-login) |
| Product Service | [`postman/product-service-curl.md`](../postman/product-service-curl.md) |
| Order Service | [`postman/order-service-curl.md`](../postman/order-service-curl.md) |
| User Service | [`postman/user-service-curl.md`](../postman/user-service-curl.md) |

### Browser

Open `http://localhost:8090/oauth2/authorization/keycloak`, log in as `testuser` / `test`, and you land on `/auth/me`. Log out with `POST /logout` (needs the CSRF header, so use Postman or curl, or the frontend later).

## Database connections

Same settings in order, product and user service (`application.properties`), from findings #4 and #29:

| Setting | Value | Why |
|---|---|---|
| `spring.jpa.open-in-view` | `false` | A request holds a connection only while a repository call (or a transaction) runs, not until the response is written. No entity has lazy associations, so nothing relies on it |
| `spring.datasource.hikari.pool-name` | `<service>-pool` | Tells the three pools apart in logs and metrics (all were `HikariPool-1`) |
| `spring.datasource.hikari.maximum-pool-size` | `5` | Three services × 5 = 15 connections, far under Postgres' default `max_connections` of 100. Tune it from a load test, not by guess |
| `spring.datasource.hikari.minimum-idle` | `2` | Keeps the laptop footprint small; the pool grows to 5 on demand |
| `spring.datasource.hikari.connection-timeout` | `3000` ms | Fail fast: a request that can't get a connection in 3 s gets a `503` with `Retry-After: 1`, instead of tying up a web thread for Hikari's default 30 s |
| `spring.datasource.hikari.leak-detection-threshold` | `2000` ms | Logs a WARN `Connection leak detection triggered` with the stack trace of where any connection held over 2 s was borrowed. On everywhere, tests included |

- **A leak-detection WARN** doesn't always mean a leak. It means a connection was held over 2 s: a slow query, or slow work inside a transaction. The stack trace shows where the connection was taken. Treat it as a bug to look at, not as noise to silence by raising the threshold.
- **A pool timeout** answers `503` `"Service temporarily unavailable, try again later"` with `Retry-After: 1`. The client never sees a database detail or a stack trace; the log level tells the cases apart (finding #31):
  - **Pool busy, database healthy:** WARN `DB pool exhausted: <pool> - Connection is not available, request timed out after 3000ms (total=, active=, idle=, waiting=)`. Load: look at slow queries or long holds (leak WARNs).
  - **Database unreachable:** ERROR `DB unreachable: ...` with the cause's stack trace (e.g. `PSQLException: Connection to localhost:5432 refused`). Hikari attaches its last connection failure as the cause. Right after a short blip that cause can linger until Hikari reconnects, so a busy timeout may log at ERROR for a moment.
- **Other failures to open a transaction** answer `500` `"Internal error"` and log at ERROR with the stack trace.

## Tests

```bash
cd backend/<service> && ./mvnw verify
```

| Service | Needs Docker | How |
|---|---|---|
| Gateway | No | One stub HTTP server plays Keycloak and the three services |
| Product, Order, User | Yes | Testcontainers Postgres, real RS256 test tokens, Keycloak and service calls mocked |

How each service's tests work is in its own doc under [services/](services/).

## Formatting

All four services use `google-java-format` (AOSP style, 4-space indent) through `spotless-maven-plugin`. CI fails on unformatted code.

```bash
./mvnw spotless:apply    # fix
./mvnw spotless:check    # what CI runs
```

## CI

GitHub Actions builds each service on its own.

| Workflow | Runs when |
|---|---|
| `order-service.yml` | A PR to `main` or a push to `main` touches `backend/order-service/**`, or **Run workflow** in the Actions tab |
| `product-service.yml` | Same, for `backend/product-service/**` |
| `user-service.yml` | Same, for `backend/user-service/**` |
| `gateway.yml` | Same, for `backend/gateway/**` |
| `build-service.yml` | Never directly. The four above call it with a service name |

Each run does `./mvnw spotless:check`, then `./mvnw verify`, and uploads the surefire reports as an artifact (kept 7 days). Testcontainers uses the runner's own Docker, so no service containers are declared.

<details>
<summary>Notes</summary>

- Path filters mean a change in one service doesn't rebuild the others, and each service gets its own check on the PR.
- A new service needs one more small caller workflow, not a copy of the build steps.
- `mvnw` must be executable in git (`100755`). A file added from Windows gets `100644`, and the Linux runner fails with "Permission denied". Fix: `git update-index --chmod=+x backend/<service>/mvnw`.
- Scope is build and test only. Scanning, SBOMs and image hardening belong to the DevSecOps pipeline project.

</details>

## Resetting local data

<details>
<summary>Start over with empty databases or a fresh realm</summary>

```bash
docker compose -f docker/docker-compose.yml down
rm -rf docker/postgres-data      # all orders, products, profiles
rm -rf docker/keycloak-data      # realm re-imported, registered users gone
docker compose -f docker/docker-compose.yml up -d
```

Both folders are gitignored. Delete only one of them if you only want to reset one side, but remember the link between them: profiles and orders point to Keycloak user ids. `testuser` and `adminuser` keep their ids across a re-import; users from `/users/register` don't come back.

</details>
