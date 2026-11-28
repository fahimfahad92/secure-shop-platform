# 16 — No test for `PUT /products/{id}` without `admin` (threat T9)

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | GEN-TEST-01 |
| Service | product-service |

**Where:** `backend/product-service/src/test/java/com/fahim/productservice/ProductControllerSecurityTest.java`

**What:** Create and delete without the `admin` role are tested (403). Update, the call that changes prices, is not.

**Why it matters:** `PUT` is the write an attacker wants (set a price to 0.01). The rule is the same `anyRequest().hasRole(ADMIN_ROLE)`, so it's covered today, but nothing proves it, and a future per-method matcher could miss it.

**Fix direction:** `updateProduct_withoutAdminRole_returns403` (and `_noToken_returns401`), mirroring the existing create/delete tests.

**When done:** risk register T9 → `Verified`, evidence = the test name; changelog entry.
