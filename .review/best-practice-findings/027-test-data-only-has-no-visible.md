# 27 — "Test data only" has no visible notice (ADR-0013)

| | |
|---|---|
| Status | Open |
| Severity | SUGGESTION |
| Rule | GEN-OTHER |
| Service | docs |

**Where:** `README.md`, `docs/development.md`

**What:** ADR-0012, 0013 and 0014 all accept risks because the stack holds only test data on one laptop. Their revisit triggers ("any real personal data", "any deployment") are events nothing detects; someone who clones the repo doesn't see the condition.

**Fix direction:** One notice near the top of `README.md` and in `docs/development.md`: local R&D only, test data only, no TLS, unencrypted data folders, links to ADR-0012/0013/0014. Point to step 4, which binds ports to localhost.

**When done:** ADR-0013 → mention the notice under Verification.
