# Threat Model — Changelog

One entry per update, newest first. Format: date, step, what changed, threats added or moved. Rules: [README.md](README.md).

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

