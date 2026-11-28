# 2 — HTTP call inside DB transaction

| | |
|---|---|
| Status | Open |
| Severity | MAJOR |
| Rule | SB-JPA-02 |
| Service | order-service |

**Where:** `backend/order-service/src/main/java/com/fahim/orderservice/service/OrderService.java:21` (class-level `@Transactional`), `:32-33` (`create` → `productClient.fetchProduct`)

**What:** `create()` inherits the class-level `@Transactional`. `JpaTransactionManager` takes a DB connection from the pool when the transaction starts, then the method makes an HTTP call to Product Service while holding it.

**Why it matters:** Product Service timeouts are 2s connect + 3s read, so one slow call holds a connection up to ~5s. Hikari's default pool is 10. Ten concurrent orders against a slow Product Service exhaust the pool, and then *every* DB operation in order-service waits, including `GET /orders`, which doesn't touch Product Service at all. One slow dependency takes down unrelated endpoints.

**Decided by:** [ADR-0015](../../docs/adr/0015-no-transactional-failures-recorded-as-state.md) (Proposed, 2026-11-22); it replaces the ADR planned for this finding. Failed orders are listed with their status in `GET /orders`. The order starts as `CREATING` (in-flight, system-owned), not as the business `PENDING`; "save the order first as `PENDING`" below now reads `CREATING`. Fixed in task `tx-order-create` (with #22).

**Fix direction (revised 2026-11-08, follows #30):** Remove the class-level `@Transactional` from `OrderService` (and the `readOnly` ones). In `create()`, save the order first as `PENDING` (own commit, connection returned), then call Product Service with no connection held, then update it: `CONFIRMED` with the price, or a failure status (`REJECTED` for unknown product or not enough stock, `FAILED` for Product Service unavailable) with the reason and time. The API answers stay `400`/`409`/`503`, but every failed attempt now leaves a row. Do it together with #22 (reply validation is one more failure reason). Needs a migration: new status values (the `CHECK` on `status` lists only `PENDING`, `CONFIRMED`, `CANCELLED`) and a `failure_reason` column. Decide how failed orders show in `GET /orders` (hidden, or listed with their status).

Rejected (original direction): splitting into an orchestrating method plus a `@Transactional` persist method on another bean. It fixes the connection hold but keeps transactions, and a failure still leaves nothing behind (#30).

**Understand first:**
- When Spring/Hibernate acquires the connection (transaction begin, not first query).
- Why a second bean is needed if you split methods: proxy-based `@Transactional` and `this.` calls (rule SB-JPA-01).
- How to see it: set `spring.datasource.hikari.maximum-pool-size=2`, slow Product Service down, fire 3 orders + 1 `GET /orders`.
