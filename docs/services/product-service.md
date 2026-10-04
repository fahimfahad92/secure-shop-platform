# Product Service

The product catalog. Anyone can browse it without logging in. Only users with the `admin` role can add, change or delete products.

| | |
|---|---|
| Port | `8082` |
| Code | `backend/product-service` |
| Type | OAuth2 resource server (validates JWTs, stateless) |
| Database | Postgres, schema `product_schema`, table `products` |
| Called by | Gateway, Order Service (to price orders) |
| Calls | Nothing |

## Endpoints

| Method | Path | Who can call | Success |
|---|---|---|---|
| `GET` | `/products` | Anyone | `200` list |
| `GET` | `/products/{id}` | Anyone | `200`, or `404` |
| `POST` | `/products` | Role `admin` | `201` with `Location` |
| `PUT` | `/products/{id}` | Role `admin` | `200`, or `404` |
| `DELETE` | `/products/{id}` | Role `admin` | `204`, or `404` |

<details>
<summary>Request and response bodies</summary>

`POST` and `PUT` take the same body:

```json
{ "name": "Mechanical Keyboard", "description": "87-key", "price": 99.99, "stock": 25 }
```

| Field | Rules |
|---|---|
| `name` | Required, up to 200 characters |
| `description` | Optional, up to 2000 characters |
| `price` | Required, greater than 0 |
| `stock` | Required, 0 or more |

Response:

```json
{ "id": 1, "name": "Mechanical Keyboard", "description": "87-key", "price": 99.99, "stock": 25,
  "createdAt": "2026-10-04T10:00:00Z", "updatedAt": "2026-10-04T10:00:00Z" }
```

A validation error answers `400` with every failing field in `message`.

</details>

## How security works

Reads are public. Every other request needs a valid token whose user holds the `admin` realm role.

| Request | Result |
|---|---|
| `GET` with no token | `200` |
| `GET` with a bad or wrong-audience token | `401`. A token that is sent is always checked, even on a public endpoint |
| Write with no token | `401` |
| Write as `testuser` (role `user` only) | `403` |
| Write as `adminuser` | Allowed |

<details>
<summary>How the token is checked</summary>

1. Spring Security's resource server validates the JWT: signature against Keycloak's JWKS, issuer `http://localhost:8081/realms/secure-shop`, expiry.
2. `spring.security.oauth2.resourceserver.jwt.audiences=product-service` rejects a token whose `aud` doesn't include `product-service` (`401`).
3. `KeycloakRealmRoleConverter` turns `realm_access.roles` into `ROLE_*` authorities. Spring's default converter only reads `scope`, so without this `hasRole("admin")` would never match. The `SCOPE_*` authorities are kept as well.
4. `GET /products/**` is `permitAll`. Everything else needs `hasRole("admin")`.

No session is created (`STATELESS`) and CSRF is off: the only credential is the bearer header, which a browser never sends on its own.

</details>

<details>
<summary>Why a role, not a scope</summary>

Admins and normal users log in through the same client. A scope is granted to the client, so it would land in everyone's token. A role belongs to the user. "Only admins can change the catalog" is a statement about the user, so it's a role.

</details>

## Notes

<details>
<summary>Behavior worth knowing</summary>

- Order Service calls `GET /products/{id}` with no credentials to read the price and stock. That works because `GET` is public. Service-to-service auth is planned (see [Next plans](../architecture.md#next-plans)).
- Stock is only changed through `PUT`. Placing an order doesn't reduce it.
- The schema is created by Flyway (`db/migration/V1__create_products_table.sql`). Hibernate only validates it (`ddl-auto=validate`). The database also enforces `price > 0` and `stock >= 0`.

</details>

## Configuration

<details>
<summary>Properties</summary>

| Property | Value |
|---|---|
| `server.port` | `8082` |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/secure_shop?currentSchema=product_schema` |
| `spring.flyway.schemas` | `product_schema` |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `http://localhost:8081/realms/secure-shop` |
| `spring.security.oauth2.resourceserver.jwt.audiences` | `product-service` |

No secrets needed.

</details>

## Tests

<details>
<summary>How the tests work</summary>

```bash
cd backend/product-service && ./mvnw verify   # needs Docker for Testcontainers
```

- One Testcontainers Postgres per test JVM. `AbstractIntegrationTest` starts it in a static initializer, not with `@Container`. The `@Container` lifecycle stops the container between test classes and restarts it on a new port, while Spring's cached context still points at the old one.
- `jwt()` from spring-security-test skips the `JwtDecoder`, so it can't test the audience check. `TokenValidationTest` sends real RS256 tokens instead. `TestJwts` makes a key pair per test JVM and `AbstractIntegrationTest` points `public-key-location` at the public half, so test tokens go through the same decoder and validators as production. No key material is committed.

</details>

## Try it

- curl: [`postman/product-service-curl.md`](../../postman/product-service-curl.md)
- Postman: **Product Service** folder. Run **Auth → Get Admin Token** first.
- Through the Gateway: `GET http://localhost:8090/products` needs no login.
