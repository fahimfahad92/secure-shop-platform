# 22 — Order Service trusts Product Service's reply unchecked (ADR-0009, T38)

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | SB-SEC |
| Service | order-service |

**Where:** `backend/order-service/.../service/OrderService.java` (`create`), a new Flyway migration in `backend/order-service/src/main/resources/db/migration/`

**What:** `create` takes `id`, `price` and `stock` from `ProductClient.fetchProduct` as-is. The stored `product_id` is the reply's `id`, not the requested one. Nothing checks that `price` is present and positive or that `stock` is present and not negative; a missing field is a `500`. `orders` has no `CHECK` on `unit_price` or `total_price`.

**Why it matters:** A forged or broken reply sets what the user pays, gets past the stock check and can store a zero or negative price. Validation doesn't make a forged plausible price detectable (that needs TLS, accepted in ADR-0014), but it bounds the damage and turns a bad upstream reply into a clean `503`.

**Also `update` (2026-11-28, `spring-api-security-review diff 12`):** since PR #12, `PUT /orders/{id}` checks quantity against the reply's `stock` too, with the same lack of checks (no `id` match, `stock` may be null → `500`, a forged value bypasses the check). The fix below covers both callers: validate once, where `ProductClient`'s reply is read.

**Fix direction:** In `create` and `update`, reject the reply with `ProductServiceUnavailableException` (→ `503`) when `id` differs from the requested product, `price` is null or not positive, or `stock` is null or negative. Migration: `ALTER TABLE orders ADD CONSTRAINT ... CHECK (unit_price > 0 AND total_price > 0)`. Tests in `OrderProductLookupTest`: mismatched `id`, zero price, negative price, missing field, each `503` with nothing saved.

**When done:** ADR-0009 Verification → add the test names; risk register T38 → drop "Known gap, unscheduled"; `stride/data-flows.md` T38 → move reply validation from Gap to Control today; changelog entry.
