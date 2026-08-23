# secure-shop-platform

Backend platform for a hands-on OAuth2/OIDC and API security exploration project. Internal monorepo: `/backend` holds each Spring Boot service, `/frontend` (Next.js) arrives once the backend is auth-complete.

Architecture: an API Gateway (BFF) is the single entry point for clients, fronting three services — User, Product, Order — with Keycloak as the only Authorization Server. Product browsing is public; everything else requires a session.

## Roadmap

| Phase | Status | What it adds |
|---|---|---|
| 0 | ✅ Done | Order Service: plain CRUD REST API, Postgres, Flyway-owned schema. No auth — the "before" baseline every later phase secures. |
| 1 | ✅ Done | Order Service becomes a Resource Server: Keycloak added to docker-compose, JWT validated against its JWKS, `orders:read`/`orders:write` scopes required. Tokens obtained manually via the Keycloak admin console for now. |
| 2 | Not started | Product Service: public product catalog. `GET` endpoints need no auth (the deliberate public case); writes require an admin role. Order Service calls Product Service directly when placing an order. |
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
  -d '{"productId":1,"quantity":2,"unitPrice":9.99}'
```

Or use the Postman collection's **Auth → Get Token** request — it stores the token as a collection variable automatically, so every other request just works.

