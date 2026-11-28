# 11 — Anemic entities, duplicated total calc

| | |
|---|---|
| Status | Open |
| Severity | SUGGESTION |
| Rule | SB-JPA-10 |
| Service | order-service |

**Where:** `Order.java` (public setters for every field, including `setId`, `setUserSub`); `OrderService.java:44` and `:64` both compute `unitPrice × quantity`

**What:** The service assembles the entity field by field; the rule "total = unit price × quantity" lives in two service methods instead of the entity.

**Why it matters:** Any new path that changes quantity must remember to recompute the total. `setUserSub` is public although the column is `updatable = false` and ownership must never change.

**Fix direction:** `Order.place(userSub, product, quantity)` factory + `changeQuantity(int)` that recomputes the total; drop setters that shouldn't exist (`setId`, `setUserSub`, `setTotalPrice`). Same idea for `Product`/`Profile` later.

**Understand first:** rich vs anemic domain model (Architecture #04 DDD — aggregates enforce invariants).
