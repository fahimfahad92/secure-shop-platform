# 35 — `Order.setStatus` lets code bypass the status actions (ADR-0016)

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | GEN-SMELL-14, SB-JPA-10 |
| Service | order-service |

**Where:** `backend/order-service/src/main/java/com/fahim/orderservice/model/Order.java` (`setStatus`); its callers are only in `src/test` (`OrderServiceTest` builds orders with it). Found by `java-scoped-code-review diff 12` on 2026-11-28.

**What:** Since PR #12, ADR-0016 says status changes only through the conditional updates in `OrderRepository` (`confirm`, `cancel`, and `updateQuantity` for quantity). Each one checks the allowed current status in its `WHERE` and sets `statusChangedAt`. The public setter was kept so the unit tests can build orders in any status. Nothing stops production code from calling `order.setStatus(X)` then `save(order)`.

**Why it matters:** That path skips the allowed-state check and leaves `statusChangedAt` stale, so the cancel window and the stuck-row check would read the wrong time. It would also bring back the read-then-save race that ADR-0016 removed. The rule exists only as a convention.

**Fix direction:**
- **Option 1:** remove the setter. Build test orders with a test-only factory or builder, or insert test rows with `JdbcTemplate`.
- **Option 2:** keep the setter and add an ArchUnit rule (with `tx-99`): only `..repository..` and test code may change `Order.status`, and no production class calls `Order.setStatus`.
- Either way, cite the test in ADR-0016's Verification.

**Understand first:** Fowler's *Remove Setting Method*, and why a JPA entity needs a no-arg constructor but not public setters (field access). Also how ArchUnit's `noClasses().should().callMethod(...)` expresses "nobody outside X calls Y".

**Links:** ADR-0016, `tasks/tx-99-archunit-no-transactional` (the ArchUnit work), `tasks/tx-order-create` (adds `REJECTED`, so more transitions will rely on this).
