# Order Service

Orders for logged-in users. Every order belongs to the user who placed it, and nobody else can see or change it. Prices come from Product Service, never from the client.

| | |
|---|---|
| Port | `8080` |
| Code | `backend/order-service` |
| Type | OAuth2 resource server (validates JWTs, stateless) |
| Database | Postgres, schema `order_schema`, table `orders` |
| Called by | Gateway |
| Calls | Product Service, `GET /products/{id}` |

## Endpoints

All endpoints work only on the caller's own orders.

| Method | Path | Needs | Success |
|---|---|---|---|
| `POST` | `/orders` | `orders:write` + role `user` | `201` with `Location` |
| `GET` | `/orders` | `orders:read` + role `user` | `200`, the caller's orders only |
| `GET` | `/orders/{id}` | `orders:read` + role `user` | `200`, or `404` |
| `PUT` | `/orders/{id}` | `orders:write` + role `user` | `200`, or `404` |
| `DELETE` | `/orders/{id}` | `orders:write` + role `user` | `204`, or `404` |

<details>
<summary>Request and response bodies</summary>

Create. There is no price field, on purpose:

```json
{ "productId": 1, "quantity": 2 }
```

Update:

```json
{ "quantity": 3, "status": "CONFIRMED" }
```

| Field | Rules |
|---|---|
| `productId` | Required |
| `quantity` | Required, at least 1 |
| `status` | Required on update. `PENDING`, `CONFIRMED` or `CANCELLED` |

Response:

```json
{ "id": 7, "productId": 1, "quantity": 2, "unitPrice": 99.99, "totalPrice": 199.98,
  "status": "PENDING", "createdAt": "2026-10-04T10:00:00Z", "updatedAt": "2026-10-04T10:00:00Z" }
```

The owner (`sub`) is stored but not returned.

</details>

## How security works

A request needs three things: a token meant for this service, the right scope and role, and ownership of the order it touches.

| Request | Result |
|---|---|
| No token | `401` |
| Token without `order-service` in `aud` | `401` |
| Token missing the scope, or missing role `user` | `403` |
| `GET /orders/5` where order 5 belongs to someone else | `404` |
| Valid token, own order | Allowed |

<details>
<summary>How the token is checked</summary>

1. Spring Security's resource server validates the JWT: signature against Keycloak's JWKS, issuer, expiry.
2. `jwt.audiences=order-service` rejects a token whose `aud` doesn't include `order-service` (`401`).
3. `KeycloakRealmRoleConverter` adds `ROLE_*` authorities from `realm_access.roles` next to the `SCOPE_*` ones from `scope`.
4. `GET /orders/**` needs `SCOPE_orders:read` **and** `ROLE_user`. Every other request needs `SCOPE_orders:write` **and** `ROLE_user`.

Stateless, CSRF off: the only credential is the bearer header.

</details>

<details>
<summary>Why both a scope and a role</summary>

They answer different questions. The scope says the app (the client) was allowed to touch orders for this user. The role says the person is a registered shop user. A token from a client that wasn't granted `orders:write` fails, and so does a user without the `user` role, even through a client that has the scope.

</details>

<details>
<summary>Ownership: why 404 and not 403</summary>

The owner always comes from the token's `sub`. It's never read from the body or the path, so a client has nothing to swap.

- `create` stores the caller's `sub` in `user_sub`. The column is `updatable = false`.
- `GET /orders` runs `findByUserSub`.
- `GET`/`PUT`/`DELETE /orders/{id}` run `findByIdAndUserSub`.

Because ownership is part of the query, someone else's order and a non-existent order give the same answer: `404`. A `403` would confirm that the id exists, so ids could be probed.

</details>

## Placing an order

Sequence diagram: [architecture.md → Placing an order](../architecture.md#placing-an-order).

1. Read `productId` and `quantity` from the request.
2. Call Product Service `GET /products/{id}` for the current price and stock.
3. Reject if `quantity` is more than the stock.
4. Save the order with `unitPrice` from the catalog, `totalPrice = unitPrice × quantity`, and the owner from the token.

| Product Service says | Order Service answers |
|---|---|
| `404` | `400`. The bad input is `productId` in the body. `/orders` itself is fine |
| `200`, but not enough stock | `409 Conflict` |
| Any other error, or no answer | `503`. The order isn't wrong, it just can't be priced now |

<details>
<summary>Notes on the Product Service call</summary>

- Plain HTTP with no credentials. v1 treats the internal network as trusted. `ProductClient` is where service-to-service auth will go (see [Next plans](../architecture.md#next-plans)).
- Connect timeout 2 s, read timeout 3 s (`product-service.connect-timeout-ms`, `read-timeout-ms`). A timeout becomes `503`.
- Stock is checked, not reserved or reduced.
- `PUT` keeps the price stored at creation (`unitPrice`) and recalculates `totalPrice`. It doesn't check stock again.
- The owner can set any `status` through `PUT`. Status changes are not restricted yet.

</details>

## Configuration

<details>
<summary>Properties and schema</summary>

| Property | Value |
|---|---|
| `server.port` | `8080` (Boot default, not set) |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/secure_shop?currentSchema=order_schema` |
| `spring.flyway.schemas` | `order_schema` |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `http://localhost:8081/realms/secure-shop` |
| `spring.security.oauth2.resourceserver.jwt.audiences` | `order-service` |
| `product-service.base-url` | `http://localhost:8082` |
| `product-service.connect-timeout-ms` / `read-timeout-ms` | `2000` / `3000` |

Flyway migrations:

| Version | Does |
|---|---|
| `V1` | Creates `orders` |
| `V2` | Deletes all existing rows, adds `user_sub NOT NULL` and an index on it. Rows from before ownership existed had no owner and nothing correct to backfill |

No secrets needed.

</details>

## Tests

<details>
<summary>How the tests work</summary>

```bash
cd backend/order-service && ./mvnw verify   # needs Docker for Testcontainers
```

- Same setup as Product Service: one Testcontainers Postgres per test JVM, started in a static initializer.
- `TokenValidationTest` sends real RS256 tokens signed by a per-JVM test key, so the audience check is tested through the real decoder.
- `OrderOwnershipTest` checks that another user's order answers `404` for read, update and delete.
- `OrderControllerSecurityTest` covers the scope and role combinations (`403` when either is missing).
- `OrderProductLookupTest` covers the `400` / `409` / `503` mapping. `ProductClient` is mocked with `@MockitoBean`.

</details>

## Try it

- curl: [`postman/order-service-curl.md`](../../postman/order-service-curl.md)
- Postman: **Order Service** folder, after **Auth → Get Token**
- Through the Gateway: log in, then `POST http://localhost:8090/orders` with the CSRF header (see [gateway.md → Dev login](gateway.md#dev-login))
