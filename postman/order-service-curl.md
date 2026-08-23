# order-service — curl requests

Paste any of these into Postman via **Import → Raw text** to auto-generate a request, or run directly. Assumes service is up on `localhost:8080` (`docker compose -f docker/docker-compose.yml up -d` for Postgres, then `mvnw spring-boot:run` in `backend/order-service`).

## Create Order

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2,"unitPrice":9.99}'
```

## Get All Orders

```bash
curl http://localhost:8080/orders
```

## Get Order By Id

```bash
curl http://localhost:8080/orders/1
```

## Update Order

```bash
curl -X PUT http://localhost:8080/orders/1 \
  -H "Content-Type: application/json" \
  -d '{"quantity":5,"status":"CONFIRMED"}'
```

## Delete Order

```bash
curl -X DELETE http://localhost:8080/orders/1
```

## Validation Error (400)

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":0,"unitPrice":9.99}'
```

## Not Found (404)

```bash
curl http://localhost:8080/orders/999
```
