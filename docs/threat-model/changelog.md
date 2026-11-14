# Threat Model — Changelog

One entry per update, newest first. Format: date, step, what changed, threats added or moved. Rules: [README.md](README.md).

## 2026-11-14 — Step 2: findings #4 + #29 (OSIV off, Hikari config)

- **T18, Control today extended, still `Open`:** `open-in-view=false` and an explicit pool (5 connections, 3 s `connection-timeout` → `503`, 2 s leak detection) in order-, product- and user-service, commit `97b7dca` on `review-findings`. A drained pool now fails waiting requests in 3 s with a `503` instead of stalling them for 30 s. The cause stays: the Product call still runs inside the transaction (#2, fixed with #30 in `tx-order-create`). The threat text now says 5 concurrent orders, matching the smaller pool. Not re-scored.
- No threats added. Thirteen High.

## 2026-11-08 — Step 1 (#05): ADRs accepted (Phase 5 done)

- ADR-0001 to 0014 move to `Accepted` with the step 1 PR (#7). ADR-0014 is accepted with an approver override: 8.5/10, above the pass mark, with its round 1 REVISE items (enforcement, CI trigger, step 3 note) deferred.
- **Moved to `Accepted`:** T30 (ADR-0012), T33 (ADR-0013), T1, T37, T39 (ADR-0014). T38 stays `Open`: its reply-integrity half is accepted by ADR-0014, its spoofing half is fixed in step 5.
- ADR pass mark raised from 7 to 8 (the last 5 Accepted ADRs average 8.7).
- No threats added or re-scored. Thirteen High.

## 2026-11-08 — Step 1 (#05): ADR-0009 revision (Phase 5)

- **T38 gap widened, not re-scored:** Order Service takes `id`, `price` and `stock` from Product Service's reply unchecked, and `orders` has no `CHECK` on the prices. A forged reply therefore also gets past the stock check, sets the stored `product_id` and can store a zero or negative price. Same attacker and path (man-in-the-middle on the internal network), so still 8 Medium. Recorded as a known gap, fix unscheduled (reply validation and a price `CHECK` migration). Found while revising ADR-0009, not by the original modelling.
- No threats added. Thirteen High.

## 2026-11-07 — Step 1 (#05): ADR-0006 revision (Phase 5)

- **T43 added** (keycloak, S, 2/3/3/3/2 = **13 High**, fixed in step 4): the realm export seeds `adminuser`/`test` with the `admin` realm role, and `testuser`/`test`, so every import carries a known admin password. Found by the ADR review (round 1, ADR-0006), not by the original modelling.
- ADR-0006 records both users as dev-only fixtures while the stack is local; step 4 moves them out of non-dev imports together with the test client (T26). ADR-0010 links to it.
- Thirteen High now. No other threats re-scored.

## 2026-10-24 — Step 1 (#05): ADRs drafted (Phase 5)

- `docs/adr/` started: index plus ADR-0001 to 0013, all `Proposed`. 0001 to 0011 record v1's decisions; 0012 and 0013 accept T30 and T33.
- T30 and T33 "Fixed in" now link their ADRs. Status stays `Open` until the ADRs are accepted.
- No threats added or re-scored.

## 2026-10-10 — Step 1 (#05): DREAD scoring (Phase 3)

- All 42 threats triaged. 9 `Verified (v1)` and 2 `Mitigated (v1)` (controls that predate the model; T2 and T9 lack a test). 31 scored: **12 High**, 16 Medium, 3 Low.
- Every High has a "Fixed in" step. Highs that no step covered were added to the plan: #02 Phase 7 (Keycloak realm and infrastructure defaults: T25, T26, T27, T32, T42, plus T7, T17, T23, T28) and Phase 8 (TLS: T1, T37, T38, T39); #04 Phase 0 publishes only the Gateway (T40); #01 Phase 7 rate limits gained the login-start endpoint and a session cap (T5) and the enumeration decision (T19).
- Step 2 gained two test-only items: session id rotation (T2), `PUT /products/{id}` without `admin` (T9).
- T30, T33 to be accepted by ADRs in Phase 5. T23 has an open question that needs a running Keycloak.

## 2026-10-10 — Step 1 (#05): initial model

- Code at `main` @ `3afe73f`.
- `system.md`: elements E1–E11, data flow diagram, flows F1–F12, trust boundaries TB1–TB8, assets.
- STRIDE: threats T1–T42 across eight files. All `Open`; DREAD scores and "Fixed in" steps follow in Phase 3.
- Split from a single `threat-model.md` into this folder the same day, so the model can grow per element.

Found by modelling, not already in `docs/architecture.md` → "Next plans" or the review findings:

- **T5** Gateway session flooding through the login-start endpoint
- **T17** the admin service account's token has order scopes and the `user` role; the audience check is the only thing stopping it
- **T19** username enumeration on registration
- **T23** open question: can `manage-users` grant `admin`?
- **T25, T28** Keycloak brute-force protection, password policy and events all off
- **T26** the password-grant client is live at Keycloak regardless of the Gateway's dev profile
- **T27, T32, T42** default admin credentials on ports reachable from the network
- **T38** order prices depend on an unauthenticated, unencrypted response
- **T39** signing keys fetched over plain HTTP
- **T40** a future Gateway rate limit is bypassable while service ports are open
- Test gaps: no test for `PUT /products/{id}` without `admin` (T9), none for session id rotation (T2)

