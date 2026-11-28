# 9 — Dev-login HTTP client: no timeouts

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | SB-IO-01/03 |
| Service | gateway |

**Where:** `backend/gateway/src/main/java/com/fahim/gateway/devlogin/KeycloakPasswordGrant.java:45-51`

**What:** Builds its own client with static `RestClient.builder()`: no connect/read timeout, and it skips Boot's auto-configured `RestClient.Builder` (no observation/metrics, no shared customisation).

**Why it matters:** Hung Keycloak → `POST /auth/dev-login` request thread hangs indefinitely. Dev-only, hence MINOR, but the pattern gets copied.

**Fix direction:** Inject `RestClient.Builder`, set a request factory with timeouts (same pattern as `ProductClientConfig`).
