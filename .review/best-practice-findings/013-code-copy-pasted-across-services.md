# 13 — Code copy-pasted across services

| | |
|---|---|
| Status | Open |
| Severity | SUGGESTION |
| Rule | GEN-DIST-08 |
| Service | all |

**Where:**
- `KeycloakRealmRoleConverter` — identical in order-service and product-service
- `GlobalExceptionHandler#body` — 3 copies
- `TestJwts` — 3 copies, 92 lines each
- `SecurityConfig` resource-server skeleton — 3 near-copies

**What:** Same code maintained in several places.

**Why it matters:** A fix in one copy (e.g. role parsing) has to be repeated by hand; copies drift.

**Fix direction:** Decision, not a code fix: either accept duplication (independent deployability, documented in an ADR) or extract a small `platform-security` library. For 3 services, accepting + ADR is reasonable. Revisit if a 4th resource server appears.
