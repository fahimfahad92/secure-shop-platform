# 26 — `admin-cli` keeps direct access grants in the app realm (ADR-0006)

| | |
|---|---|
| Status | Open |
| Severity | SUGGESTION |
| Rule | SB-SEC |
| Service | realm |

**Where:** `docker/keycloak/secure-shop-realm.json`, client `admin-cli`

**What:** `admin-cli` is public with `directAccessGrantsEnabled: true`, so anyone with a user's password can get a token for that user straight from Keycloak, no secret needed. Nothing in the project uses it. Its tokens are rejected by every service today only because it lacks `secure-shop-audience` (#24 makes that checked).

**Why it matters:** It's a second password-grant path next to `secure-shop-test-client` (T26), and the reason it's harmless is incidental.

**Fix direction:** Set `directAccessGrantsEnabled: false` on `admin-cli` in the realm export, with step 4's realm hardening (T25, T26). Confirm nothing in `docs/` or Postman uses it first.

**When done:** risk register T26 note; ADR-0006 Verification line.
