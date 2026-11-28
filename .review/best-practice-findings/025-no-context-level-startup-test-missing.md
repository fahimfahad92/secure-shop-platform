# 25 — No context-level startup test for a missing secret (ADR-0007)

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | GEN-TEST-01 |
| Service | gateway, user |

**Where:** Gateway and User Service test sources

**What:** ADR-0007's fail-fast checks are tested at the properties-class level (`KeycloakAdminPropertiesTest` and the Gateway equivalent), not by starting a context without the secret.

**Why it matters:** A wiring change (the properties class no longer bound or validated at startup) would leave the unit test green while the app starts without its secret and fails at the first login.

**Fix direction:** `ApplicationContextRunner` (or `@SpringBootTest` expecting a startup failure) without `KEYCLOAK_GATEWAY_CLIENT_SECRET` / the admin-client secret: the context fails with the hint ADR-0007 promises. One per deployable.

**When done:** ADR-0007 Verification → add the test names; reword "the same check, the same message, the same tests" to "same condition, same hint, parallel tests" (round 2 note).
