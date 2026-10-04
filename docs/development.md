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
