# secure-shop-platform

Backend platform for a hands-on OAuth2/OIDC and API security exploration project. Internal monorepo: `/backend` holds each Spring Boot service, `/frontend` (Next.js) arrives once the backend is auth-complete.

Architecture: an API Gateway (BFF) is the single entry point for clients, fronting three services — User, Product, Order — with Keycloak as the only Authorization Server. Product browsing is public; everything else requires a session.

## Roadmap

| Phase | Status | What it adds |
|---|---|---|
| 0 | ✅ Done | Order Service: plain CRUD REST API, Postgres, Flyway-owned schema. No auth — the "before" baseline every later phase secures. |
| 1 | ✅ Done | Order Service becomes a Resource Server: Keycloak added to docker-compose, JWT validated against its JWKS, `orders:read`/`orders:write` scopes required. Tokens obtained manually via the Keycloak admin console for now. |
| 2 | ✅ Done | Product Service: public product catalog. `GET` endpoints need no auth (the deliberate public case); writes require the `product-admin` realm role. Order Service calls Product Service when placing an order, and prices the order from the catalog instead of trusting the client. |
| 3 | Not started | User Service: profile data keyed by Keycloak's `sub`. `/register` creates both a Keycloak user (via Admin API) and a local profile row, with compensating delete if the local write fails. Order Service starts checking ownership via `sub`. |
| 4 | Not started | API Gateway (BFF): single entry point for all client traffic. Holds the session — sets an `HttpOnly` cookie, translates it to a bearer token on proxied calls, lets public `GET /products/**` through unauthenticated while gating everything else. Browser never sees Keycloak or a raw JWT. |

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
  -d "client_id=order-service-client" \
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
- `POST`/`PUT`/`DELETE /products` — require the `product-admin` realm role. No token → 401. Valid token without the role → 403.

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

Keycloak needs a `product-admin` realm role and a user holding it (`adminuser`) — realm setup is still manual via the admin console.

### Try it

```bash
# Public browse — no token
curl http://localhost:8082/products

# Admin write — needs a product-admin token
curl -X POST http://localhost:8082/products \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Mechanical Keyboard","description":"87-key","price":99.99,"stock":25}'
```

Full request sets: [`postman/product-service-curl.md`](postman/product-service-curl.md) and [`postman/order-service-curl.md`](postman/order-service-curl.md), or the Postman collection's **Product Service** folder (**Auth → Get Admin Token** first — it asserts the `product-admin` role is present in the token).

### Tests

```bash
cd backend/product-service && ./mvnw test   # 20 tests
cd backend/order-service && ./mvnw test     # 22 tests
```

Both use a single Testcontainers Postgres per JVM. `AbstractIntegrationTest` starts it in a static initializer rather than via `@Container`, because the `@Container` lifecycle stops the container between test classes and restarts it on a new port while Spring reuses its cached context pointing at the old one.


## CI

GitHub Actions builds each service independently:

| Workflow | Triggers |
|---|---|
| `.github/workflows/order-service.yml` | PRs to `main` and pushes to `main` touching `backend/order-service/**`, plus **Run workflow** in the Actions tab |
| `.github/workflows/product-service.yml` | same, for `backend/product-service/**` |
| `.github/workflows/build-service.yml` | reusable — not triggered directly; the two above call it with a service name |

Each run checks formatting (`./mvnw spotless:check`), then builds and tests (`./mvnw verify`), and uploads the surefire reports as an artifact. Integration tests use Testcontainers against the runner's own Docker daemon, so no service containers are declared.

Path filters mean a change under one service does not rebuild the other, and each service gets its own status check on the PR. Adding a service later means one more thin caller workflow, not a copy of the build steps.

Scope is deliberately build-and-test only — scanning, SBOMs and image hardening belong to the DevSecOps pipeline project (`project-ideas/04`), which targets this repo later.
