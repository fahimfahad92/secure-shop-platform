# 20 — No route-manifest or cross-user ownership test (ADR-0003)

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | GEN-TEST-01 |
| Service | order, product, user |

**Where:** test sources of Order, Product and User Service

**What:** Security tests are per endpoint. A new endpoint is covered only if someone writes its test.

**Why it matters:** ADR-0003 makes each service the only layer for its rules, so coverage must be mechanical, not remembered.

**Fix direction:**
- Route-manifest test: read all routes from `RequestMappingHandlerMapping`, compare with a checked-in manifest (route → rule). A missing route fails the build. Every non-public route answers `401` without a token; the public allowlist holds exact patterns only
- Cross-user test (Order, User): seed data for user A, call every owner-scoped route as user B, assert A's data never appears (`404` for by-id routes, absent from list responses)

**When done:** ADR-0003 → move items 3–4 into Verification with the test names; remove the "Not yet written" line. ADR-0004 → add the cross-user test to Verification (Scheduled hardening item 3). If a regression still gets through, ADR-0004's RLS migration path is next (needs `SET LOCAL` per transaction with pooled connections).
