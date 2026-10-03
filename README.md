# secure-shop-platform

Backend platform for a hands-on OAuth2/OIDC and API security exploration project. Internal monorepo: `/backend` holds each Spring Boot service, `/frontend` (Next.js) arrives once the backend is auth-complete.

Architecture: an API Gateway (BFF) is the single entry point for clients, fronting three services — User, Product, Order — with Keycloak as the only Authorization Server. Product browsing is public; everything else requires a session.

## Roadmap

| Phase | Status | What it adds |
|---|---|---|
| 0 | ✅ Done | Order Service: plain CRUD REST API, Postgres, Flyway-owned schema. No auth — the "before" baseline every later phase secures. |
| 1 | ✅ Done | Order Service becomes a Resource Server: Keycloak added to docker-compose, JWT validated against its JWKS, `orders:read`/`orders:write` scopes required. Tokens obtained manually via the Keycloak admin console for now. |
| 2 | ✅ Done | Product Service: public product catalog. `GET` endpoints need no auth (the deliberate public case); writes require an admin realm role (the shared `admin` role since Phase 4). Order Service calls Product Service when placing an order, and prices the order from the catalog instead of trusting the client. |
| 3 | ✅ Done | User Service: profile data keyed by Keycloak's `sub`. `/register` creates both a Keycloak user (via Admin API) and a local profile row, with compensating delete if the local write fails. Order Service scopes every order to the caller's `sub`. |
| 4 | 🔄 In progress — Keycloak identity model in code; old client/role removal + realm export, then Gateway | Keycloak identity model cleanup (realm roles `user`/`admin`, audience per service, dedicated test and Gateway clients), then the API Gateway (BFF): single entry point for all client traffic. Holds the session — sets an `HttpOnly` cookie, translates it to a bearer token on proxied calls, lets public `GET /products/**` through unauthenticated while gating everything else. Browser never sees Keycloak or a raw JWT. |

Phases 0–4 are the backend-complete v1, driven via curl/Postman. A Next.js frontend and deeper security-hardening phases follow once v1 is done.

## Phase 0 — Order Service (no auth yet)

Bootstrap baseline: plain CRUD REST API for orders, backed by Postgres, schema owned by Flyway migrations.

### Run it

```bash
# 1. Start Postgres
docker compose -f docker/docker-compose.yml up -d

# 2. Start the service (from backend/order-service)
cd backend/order-service
./mvnw spring-boot:run
```

Service listens on `http://localhost:8080`. Flyway creates `order_schema` and the `orders` table on first boot (`src/main/resources/db/migration/`).

### Try it

Postman collection: [`postman/secure-shop-platform.postman_collection.json`](postman/secure-shop-platform.postman_collection.json) (import directly — `Auth` folder fetches a token from Keycloak, `Order Service` folder has the CRUD requests), or raw curl: [`postman/order-service-curl.md`](postman/order-service-curl.md).

As of Phase 1, every request needs a bearer token — see [Phase 1](#phase-1--order-service-as-a-resource-server-keycloak) below for how to get one.

### Stack

Java 21, Spring Boot 3.5, Maven, PostgreSQL, Flyway. Formatting: `google-java-format` (AOSP, 4-space) via `spotless-maven-plugin` — run `./mvnw spotless:apply` before committing.

## Phase 1 — Order Service as a Resource Server (Keycloak)

All `/orders/**` endpoints now require a valid JWT: `orders:read` scope for `GET`, `orders:write` for `POST`/`PUT`/`DELETE`. No token → 401. Valid token missing the required scope → 403.

### Run it

```bash
# 1. Start Postgres + Keycloak
docker compose -f docker/docker-compose.yml up -d

# 2. Start the service
cd backend/order-service
./mvnw spring-boot:run
```

Keycloak admin console: `http://localhost:8081` (`admin`/`admin`). Realm/client/scope setup is manual — see the setup notes referenced in this project's planning docs if starting from scratch.

### Get a token and call the API

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/secure-shop/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=secure-shop-test-client" \
  -d "client_secret=<client secret>" \
  -d "username=testuser" \
  -d "password=<password>" | grep -o '"access_token":"[^"]*"' | sed 's/"access_token":"//;s/"$//')

curl -X POST http://localhost:8080/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2}'
```

Or use the Postman collection's **Auth → Get Token** request — it stores the token as a collection variable automatically, so every other request just works.

## Phase 2 — Product Service (public catalog + admin writes)

Two services now. Product Service (`:8082`) owns the catalog:

- `GET /products` and `GET /products/{id}` — **public**, no token at all. The first genuinely public endpoints in the project.
- `POST`/`PUT`/`DELETE /products` — require the `admin` realm role. No token → 401. Valid token without the role → 403.

Authorization here is a **realm role**, not a client scope. Both `testuser` and `adminuser` authenticate through the same client, so a client scope would land on every user of that client and "admin-only" would mean nothing. A scope describes what the client was granted; a role describes who the user is. Keycloak puts realm roles in the token's `realm_access.roles` claim, which Spring Security does not map to authorities on its own — hence `KeycloakRealmRoleConverter`, which adds `ROLE_*` authorities while leaving the `SCOPE_*` ones intact.

### Order Service now calls Product Service

Placing an order triggers an internal `GET /products/{id}`:

- **Price comes from the catalog, never from the request.** `CreateOrderRequest` has no price field at all, so a client cannot order a 99.99 keyboard for 0.01. The client sends only `productId` and `quantity`.
- **Stock is checked before the order is written.** Ordering more than the catalog holds is rejected and nothing is persisted.

Failure mapping, chosen so the status code says whose problem it is:

| Situation | Status | Why |
|---|---|---|
| Product does not exist | `400 Bad Request` | The bad input is the `productId` in the body — the `/orders` resource itself is fine, so 404 would be misleading |
| `quantity` exceeds stock | `409 Conflict` | The request is well-formed; it conflicts with current catalog state |
| Product Service down or erroring | `503 Service Unavailable` | The order is not rejected — it just cannot be priced right now |

The call is plain HTTP with no credentials: v1 treats the internal network as trusted. That is the deliberate retrofit point for the service-to-service security work (`project-ideas/03`), and `ProductClient` is where it lands.

### Run it

```bash
# 1. Start Postgres + Keycloak
docker compose -f docker/docker-compose.yml up -d

# 2. Start Product Service
cd backend/product-service
./mvnw spring-boot:run

# 3. Start Order Service (separate terminal)
cd backend/order-service
./mvnw spring-boot:run
```

Product Service listens on `http://localhost:8082`; Flyway creates `product_schema` and the `products` table on first boot. Order Service reaches it via `product-service.base-url` in `application.properties`.

Keycloak needs an `admin` realm role and a user holding it (`adminuser`). See Phase 4 for the full realm model.

### Try it

```bash
# Public browse — no token
curl http://localhost:8082/products

# Admin write — needs a token for a user holding the admin role
curl -X POST http://localhost:8082/products \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Mechanical Keyboard","description":"87-key","price":99.99,"stock":25}'
```

Full request sets: [`postman/product-service-curl.md`](postman/product-service-curl.md) and [`postman/order-service-curl.md`](postman/order-service-curl.md), or the Postman collection's **Product Service** folder (**Auth → Get Admin Token** first — it asserts the `admin` role and the `product-service` audience are present in the token).

### Tests

```bash
cd backend/product-service && ./mvnw test   # 20 tests
cd backend/order-service && ./mvnw test     # 22 tests
```

Both use a single Testcontainers Postgres per JVM. `AbstractIntegrationTest` starts it in a static initializer rather than via `@Container`, because the `@Container` lifecycle stops the container between test classes and restarts it on a new port while Spring reuses its cached context pointing at the old one.


## Phase 3 — User Service (profiles + registration orchestration)

Three services now. User Service (`:8083`) owns profile data; Keycloak still owns identity and credentials.

- `POST /users/register` — **public**, the only unauthenticated write in the project. A new user has no token yet, so there is nothing it could present.
- `GET`/`PUT`/`DELETE /users/me` — the caller is resolved from the token's `sub`. There is no `/users/{id}`, so there is no id for a client to substitute.
- `UpdateProfileRequest` carries `fullName`, `address` and `phone` only. Sending `username` or `email` changes nothing — identity belongs to Keycloak, and a profile update is not the place to edit it.

### Registration spans two systems

`POST /users/register` creates the Keycloak user via the Admin API, then writes the local profile row keyed by the returned `sub`. There is no transaction across the two.

If the profile write fails, the Keycloak user is deleted again. Without that compensation the username stays taken in Keycloak and the user gets a baffling "already exists" on their next attempt. If the compensating delete *also* fails, the user is orphaned in Keycloak and that is logged as `ORPHANED KEYCLOAK USER <sub>` — nothing else would notice. Closing that remaining window properly is what the Outbox stretch phase is for.

`register` is deliberately **not** `@Transactional`: the Keycloak call is not transactional, and annotating the pair would only suggest it was.

### Admin API access

User Service authenticates to Keycloak as the `user-service-admin-client` service account (Client Credentials grant), holding only `manage-users` and `view-users` on `realm-management` — not `realm-admin`, and not the master-realm bootstrap admin. The token is cached in memory and refreshed 30 seconds before expiry.

The secret comes from the environment with no fallback, so a missing value fails startup instead of failing the first registration:

```properties
keycloak.admin.client-secret=${KEYCLOAK_ADMIN_CLIENT_SECRET}
```

### Orders became per-user

`V2` adds `user_sub NOT NULL` to `orders` plus an index, and deletes pre-Phase-3 rows — they have no owner and there is no correct value to backfill.

- `create` stamps the owner from the token; the column is `updatable = false`
- `GET /orders` returns only the caller's orders
- `GET`/`PUT`/`DELETE /orders/{id}` answer **404** for someone else's order, not 403. Ownership is part of the repository query (`findByIdAndUserSub`), so a miss is indistinguishable from a non-existent order and the id cannot be probed for existence

### Run it

```bash
docker compose -f docker/docker-compose.yml up -d

export KEYCLOAK_ADMIN_CLIENT_SECRET=<user-service-admin-client secret>
cd backend/user-service && ./mvnw spring-boot:run    # :8083
cd backend/product-service && ./mvnw spring-boot:run # :8082
cd backend/order-service && ./mvnw spring-boot:run   # :8080
```

Keycloak needs the `user-service-admin-client` service account described above; realm setup is still manual via the admin console.

### Try it

Register, log in as that user, then use the profile and order endpoints: [`postman/user-service-curl.md`](postman/user-service-curl.md), or the Postman collection's **User Service** folder followed by **Auth → Get Token (registered user)**.

### Tests

```bash
cd backend/user-service && ./mvnw test     # 17 tests
cd backend/order-service && ./mvnw test    # 27 tests
cd backend/product-service && ./mvnw test  # 20 tests
```

User Service supplies the service account secret through `@TestPropertySource` on `AbstractIntegrationTest`, not a test `application.properties` — a test file of that name shadows the main one by classpath precedence and would take the resource server config down with it.

## Phase 4 — Keycloak identity model (part 1 of Phase 4)

Phases 1–3 built the realm one console click at a time. The result was a single password-grant client named after one service, and an admin role that only made sense for one service. Before the Gateway is built on top of it, the realm follows one model:

- **Clients are callers, not APIs.** `secure-shop-gateway` is the Gateway's login client (Authorization Code + PKCE `S256`). `secure-shop-test-client` is the password-grant client for Postman/curl only — dev realm, never production. Adding a service adds scopes and an audience, never a new user-facing client.
- **Scopes say what the app may do** (`orders:read`, `orders:write`). **Roles say who the user is**: every user holds the realm role `user` (via `default-roles-secure-shop`, so `/users/register` needs no change), admins also hold `admin`. **`sub` says which rows are theirs.**
- **Roles are realm roles**, so they come with the user through whichever client they log in with. `adminuser` uses the same clients as everyone else.
- **Every token names its services in `aud`.** The `secure-shop-audience` client scope adds `order-service`, `product-service` and `user-service` to both user-facing clients.

What each service checks now:

| Service | Rule | Fails with |
|---|---|---|
| Order | `GET` needs `SCOPE_orders:read` **and** `ROLE_user`; writes need `SCOPE_orders:write` **and** `ROLE_user` | `403` if either is missing |
| Product | writes need `ROLE_admin`; `GET` stays public | `403` without `admin` |
| All three | `aud` must contain the service's own name (`spring.security.oauth2.resourceserver.jwt.audiences`) | `401` — the token is not valid here at all |

Order Service gained the same `KeycloakRealmRoleConverter` Product Service already had, since its rules now depend on a role too.

A bad token is rejected even on public endpoints: a `GET /products` that carries a token minted for another service gets `401`, not the catalog. Anonymous browsing sends no token.

### Tests

```bash
cd backend/order-service && ./mvnw verify    # 39 tests
cd backend/product-service && ./mvnw verify  # 24 tests
cd backend/user-service && ./mvnw verify     # 20 tests
```

`jwt()` from spring-security-test bypasses the `JwtDecoder`, so it can't test the audience check. Each service's `TokenValidationTest` sends real RS256 tokens instead. `TestJwts` generates a key pair per test JVM and `AbstractIntegrationTest` points `public-key-location` at the public half, so those tokens go through the same Boot-built decoder and validators as production. No key material is committed.

Still to come in Phase 4: deleting the old client and role, committing a realm export that Keycloak imports on startup, then the Gateway itself.

## CI

GitHub Actions builds each service independently:

| Workflow | Triggers |
|---|---|
| `.github/workflows/order-service.yml` | PRs to `main` and pushes to `main` touching `backend/order-service/**`, plus **Run workflow** in the Actions tab |
| `.github/workflows/product-service.yml` | same, for `backend/product-service/**` |
| `.github/workflows/user-service.yml` | same, for `backend/user-service/**` |
| `.github/workflows/build-service.yml` | reusable — not triggered directly; the two above call it with a service name |

Each run checks formatting (`./mvnw spotless:check`), then builds and tests (`./mvnw verify`), and uploads the surefire reports as an artifact. Integration tests use Testcontainers against the runner's own Docker daemon, so no service containers are declared.

Path filters mean a change under one service does not rebuild the other, and each service gets its own status check on the PR. Adding a service later means one more thin caller workflow, not a copy of the build steps.

Scope is deliberately build-and-test only — scanning, SBOMs and image hardening belong to the DevSecOps pipeline project (`project-ideas/04`), which targets this repo later.
