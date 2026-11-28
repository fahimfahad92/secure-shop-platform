# 8 — No optimistic locking

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | SB-JPA-06 |
| Service | order, product, user |

**Where:** `Order.java`, `Product.java`, `Profile.java` (no `@Version` field)

**What:** `PUT` loads the entity, changes it, saves it. Two concurrent PUTs on the same row both succeed; the later one silently overwrites the earlier.

**Why it matters:** Lost updates: two admins editing the same product's price/stock, or two tabs editing one order. No error, no trace. (The stock *race at order time* is a separate, documented gap — not this.)

**Fix direction:** `@Version private Long version;` + Flyway migration adding the column; map `ObjectOptimisticLockingFailureException` to 409. Optionally expose version as `ETag` and require `If-Match` on PUT.

**Understand first:** how Hibernate adds `WHERE version = ?` to the UPDATE; optimistic vs pessimistic locking; ETag/`If-Match` for HTTP.

**Progress (2026-11-28, `tx-order-update`), orders only:** status and quantity change through conditional updates (`WHERE status = <allowed>`, [ADR-0016](../../docs/adr/0016-order-status-changes-only-through-actions.md)), so a `PUT` can no longer overwrite a concurrent confirm or cancel (`OrderStatusTransitionsTest.put_whileCancelledDuringProductCall_staysCancelled`). Not covered: two `PUT`s on the same `PENDING` order are still last-write-wins on quantity, and products and profiles are unchanged. Stays Open.
