# order-service — curl requests

Paste any of these into Postman via **Import → Raw text** to auto-generate a request, or run
directly. Assumes Postgres + Keycloak are up (`docker compose -f docker/docker-compose.yml up -d`),
Product Service is running on `localhost:8082`, and Order Service is running on `localhost:8080`
(`./mvnw spring-boot:run` in each service folder).

Every `/orders` endpoint needs a bearer token with the `user` realm role plus a scope: `orders:read`
for `GET`, `orders:write` for the rest. The token must also list `order-service` in `aud`. Missing
scope or role answers `403`; wrong or missing audience answers `401`.

As of Phase 3 orders also belong to the `sub` in the token. `GET /orders` returns only the caller's
own orders, and another user's order answers `404` rather than `403` — a 403 would confirm that the
id exists.

## Get a token

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/secure-shop/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=secure-shop-test-client" \
  -d "client_secret=<client secret>" \
  -d "username=testuser" \
  -d "password=<testuser password>" | jq -r .access_token)
```

## Create Order

Price is **not** sent by the client — Order Service looks the product up in Product Service and uses
the catalog price. The request carries only what the client legitimately decides: which product, how
many.

```bash
curl -X POST http://localhost:8080/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2}'
```

Requires an existing product with id `1` — create one via
[`product-service-curl.md`](product-service-curl.md) first.

## Get All Orders

```bash
curl http://localhost:8080/orders -H "Authorization: Bearer $TOKEN"
```

## Get Order By Id

```bash
curl http://localhost:8080/orders/1 -H "Authorization: Bearer $TOKEN"
```

## Update Order

```bash
curl -X PUT http://localhost:8080/orders/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"quantity":5,"status":"CONFIRMED"}'
```

## Delete Order

```bash
curl -X DELETE http://localhost:8080/orders/1 -H "Authorization: Bearer $TOKEN"
```

## Validation Error (400)

```bash
curl -X POST http://localhost:8080/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":0}'
```

## Unknown Product (400)

The product lookup fails, so the order is rejected as a bad request — the missing thing is the
product in the request body, not the `/orders/{id}` resource.

```bash
curl -i -X POST http://localhost:8080/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"productId":999999,"quantity":1}'
```

## Insufficient Stock (409)

```bash
curl -i -X POST http://localhost:8080/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":100000}'
```

## Product Service Down (503)

Stop Product Service, then place an order. Order Service cannot price the order, so it reports the
dependency failure rather than guessing.

```bash
curl -i -X POST http://localhost:8080/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":1}'
```

## Order Not Found (404)

```bash
curl http://localhost:8080/orders/999 -H "Authorization: Bearer $TOKEN"
```

## Another User's Order (404)

Place an order with one user's token, then fetch it by id with a different user's token (see
[`user-service-curl.md`](user-service-curl.md) for registering a second user):

```bash
curl -i http://localhost:8080/orders/1 -H "Authorization: Bearer $OTHER_USERS_TOKEN"
```

Indistinguishable from an order that never existed, which is the point. Same for `PUT` and `DELETE`.

## No Token (401)

```bash
curl -i http://localhost:8080/orders
```
