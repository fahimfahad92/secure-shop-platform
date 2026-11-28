# 3 — Unpaginated list endpoints

| | |
|---|---|
| Status | Open |
| Severity | MAJOR |
| Rule | SB-JPA-05 |
| Service | product, order |

**Where:**
- `backend/product-service/src/main/java/com/fahim/productservice/service/ProductService.java:31-34` (`getAll` → `findAll()`), exposed by `ProductController#getAll` — **public, no auth**
- `backend/order-service/src/main/java/com/fahim/orderservice/service/OrderService.java:48-51` (`getAll` → `findByUserSub`) — per user, lower risk

**What:** Both return every matching row as a `List`.

**Why it matters:** Catalog grows → every anonymous `GET /products` loads all rows into memory and serialises them. Cheap to abuse (no token needed), and response time grows with table size. Changing the response shape later is a breaking API change, so it's cheaper to do now than after a frontend depends on it.

**Fix direction:** Accept `Pageable` (`?page=0&size=20&sort=name`), cap size (`spring.data.web.pageable.max-page-size=100`), return a page DTO (content + page metadata) — not `Page<Entity>` directly. Same for orders.

**Understand first:**
- Spring Data `Pageable` binding and `PagedModel` / `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` (Boot 3.3+ warns when serialising `PageImpl` directly).
- Offset vs keyset (cursor) pagination — when offset gets slow.
- Gateway and Postman collection need the new response shape too.
