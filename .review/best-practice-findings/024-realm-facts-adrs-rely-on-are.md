# 24 — Realm facts the ADRs rely on are checked by inspection only (ADR-0011, ADR-0006)

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | GEN-TEST-01 |
| Service | realm |

**Where:** `docker/keycloak/secure-shop-realm.json`; a new unit test (any service's test sources, or a small test module)

**What:** ADR-0011 and ADR-0006 rest on realm settings that only a human checks: `secure-shop-audience` is a default scope on `secure-shop-gateway` and `secure-shop-test-client` only, on no other client (`admin-cli` included), and not in `defaultDefaultClientScopes`; `default-roles-secure-shop` includes `user`; only `adminuser` has `admin`; `accessTokenLifespan` is 300; `secure-shop-gateway` has direct access grants off and `secure-shop-test-client` has standard flow off.

**Why it matters:** One realm edit, such as making the audience scope a realm default, silently gives every client, `admin-cli` included, tokens every service accepts. Nothing fails.

**Fix direction:** A JUnit test that parses `secure-shop-realm.json` with Jackson and asserts each fact above, one assertion per ADR claim, with the ADR number in the message. Runs in `mvn verify`, so CI fails on a breaking realm edit.

**When done:** ADR-0011 and ADR-0006 Verification → replace "by inspection, no test" with the test name.
