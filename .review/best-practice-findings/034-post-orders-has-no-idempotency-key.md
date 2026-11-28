# 34 — `POST /orders` has no idempotency key: a client retry after a lost response places a second order

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | SB-API |
| Service | order, gateway |

**Where:** `OrderController` `POST /orders` → `OrderService#create`. No `Idempotency-Key` header and no unique constraint that would catch a repeated request.

**What:** if the response is lost (a pod is killed after the commit, a timeout at the Gateway, a dropped connection), the customer doesn't know whether the order was placed, and clicking again creates a second, real order. This happens **with or without** `@Transactional`: a rollback covers a crash *before* the commit, not after it. ADR-0015 changes the shape slightly (a crash mid-flight leaves a stuck `CREATING` trace row, not a second placed order), but the double-submit itself stays.

**Why it matters:** duplicate orders, and later duplicate stock reservations once #03 (step 5) adds `reserve-stock`. Retries at the Gateway or in the frontend (step 6 resilience) would make it more likely, not less. Found in the `tx-00` interview drill (`Interview-Preparation/tasks/completed/tx-00-adr-no-transactional/drill.md`, Q1).

**Fix direction:**
- The client sends an `Idempotency-Key` (a UUID per "Place order" click). Order Service stores it with the order (`UNIQUE (user_sub, idempotency_key)`) and, on a repeat, returns the existing order instead of creating one.
- A key seen while its order is still `CREATING` answers `409` or `202` ("still processing"), not a second attempt.
- Keys expire after a window (e.g. 24 h).

Likely together with #01 v2 Phase 7 (API hardening) and the retry-policy ADR listed in `project-ideas/01-oauth2-oidc/requirements.md` (ADR list item 6), since retries are only safe on idempotent calls.

**Understand first:** the IETF `Idempotency-Key` header draft and Stripe's implementation. Why the key must be scoped per user (one user's key must not reveal another's order). Storing the key in the same row as the order: under ADR-0015 there is no transaction, so the `UNIQUE` constraint is what makes it safe.
