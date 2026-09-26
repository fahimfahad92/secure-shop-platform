# product-service — curl requests

Assumes Postgres + Keycloak are up (`docker compose -f docker/docker-compose.yml up -d`) and the
service is running on `localhost:8082` (`./mvnw spring-boot:run` in `backend/product-service`).

Reads are public. Writes need a token carrying the `product-admin` realm role.

## Get an admin token

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8081/realms/secure-shop/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=order-service-client" \
  -d "client_secret=<client secret>" \
  -d "username=adminuser" \
  -d "password=<adminuser password>" | jq -r .access_token)
```

A `testuser` token works the same way and is the non-admin case used below.

## Get All Products (public — no token)

```bash
curl http://localhost:8082/products
```

## Get Product By Id (public — no token)

```bash
curl http://localhost:8082/products/1
```

## Create Product (admin)

```bash
curl -X POST http://localhost:8082/products \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Mechanical Keyboard","description":"87-key, hot-swappable","price":99.99,"stock":25}'
```

## Update Product (admin)

```bash
curl -X PUT http://localhost:8082/products/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Mechanical Keyboard (v2)","description":"87-key, RGB","price":119.99,"stock":40}'
```

## Delete Product (admin)

```bash
curl -X DELETE http://localhost:8082/products/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

## No Token on a Write (401)

```bash
curl -i -X POST http://localhost:8082/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Keyboard","price":99.99,"stock":25}'
```

## Non-Admin Token on a Write (403)

```bash
curl -i -X POST http://localhost:8082/products \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Keyboard","price":99.99,"stock":25}'
```

`$TOKEN` here is a `testuser` token — authenticated fine, no `product-admin` role, so 403 rather
than 401. The 401-vs-403 split is the whole point of running both of these.

## Validation Error (400)

```bash
curl -X POST http://localhost:8082/products \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"","price":-1,"stock":-5}'
```

## Not Found (404)

```bash
curl http://localhost:8082/products/999999
```
