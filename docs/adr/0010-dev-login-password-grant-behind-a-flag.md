# 0010. Dev login: password grant behind a property and the `dev` profile

| | |
|---|---|
| Status | Proposed |
| Date | 2026-10-24 (recorded). Decided 2026-10-04, #01 Phase 4, PR #5 |
| Category | security |
| Impact | normal |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5, retroactive) |
| Drivers | Postman testing through the BFF; T7 (11 Medium), T26 (12 High), T3 |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

With the BFF ([0002](0002-bff-tokens-stay-in-the-gateway.md)) the only way to get a session is a browser login: redirect to Keycloak, login form, redirect back with a code. Postman and curl can't follow that flow into a Gateway session. Until the frontend exists (step 6), Postman is how the whole system gets tested, so it needs a session without a browser.

The OAuth 2.0 Security Best Current Practice says the password grant must not be used. It is still the simplest way for a script to turn a username and password into tokens.

How do non-browser clients get a Gateway session for testing, without weakening the real login?

## Decision drivers

- Postman can test every route through the Gateway, including token relay and refresh
- The browser login client stays Authorization Code only
- The shortcut does not exist unless someone turns it on
- It is removable once real browser tests exist

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **`POST /auth/dev-login` in the Gateway: password grant against a separate `secure-shop-test-client`, beans only when `secure-shop.dev-login.enabled=true`, set only in `application-dev.properties`** | Tests the real Gateway path; browser client untouched; off by default | Password grant exists in the realm; a misconfigured profile turns it on |
| Enable the password grant on `secure-shop-gateway` | No second client | Weakens the real login client for everyone |
| Postman's OAuth 2.0 helper | No code | Postman ends up with tokens, not a Gateway session, so the BFF path is never tested |
| Call services directly with tokens from Keycloak, skip the Gateway | No Gateway code | The Gateway (session, CSRF, relay, refresh) goes untested |
| Browser automation (Playwright) for every API test | Real login only | Heavy before a frontend exists |

## Decision outcome

**Chosen: dev login in the Gateway, off unless the `dev` profile turns it on.**

- `@ConditionalOnDevLogin` (`@ConditionalOnProperty("secure-shop.dev-login.enabled", havingValue = "true")`) guards every dev-login bean; only `application-dev.properties` sets the property
- Without the property `POST /auth/dev-login` has no handler and returns `404`
- The password grant runs against `secure-shop-test-client` (`directAccessGrantsEnabled: true`, standard flow off); `secure-shop-gateway` keeps the standard flow only
- Refreshes for dev sessions use the same registration, because a refresh token only works for the client that issued it
- Logout of a dev session ends the Keycloak session server-side and returns `204`
- Never enabled in a shared environment

## One-minute answer

For Postman we added a dev-only login endpoint to the Gateway that uses the password grant against a separate test client, and it only exists when the `dev` profile turns it on. That lets us test the real BFF path without a browser, while the real login client stays Authorization Code only. The cost is that the password-grant client lives in Keycloak whatever the Gateway's profile is, so the realm has to drop it outside dev.

## Consequences

### Positive

- Every route can be tested through the Gateway from Postman
- The real login client has no password grant

### Negative (residual risk)

- **The test client is live in Keycloak in every environment that imports the realm**, whatever the Gateway's profile. Anyone who knows its secret can use the password grant directly at Keycloak (T26, 12 High; step 4 keeps it out of non-dev imports)
- **The flag is the only guard.** Starting a shared Gateway with the `dev` profile opens a password endpoint with no rate limit and no CSRF check (login CSRF), on top of no brute-force protection in Keycloak (T7, 11 Medium; T25). Step 4 adds a startup guard
- **Password grant is deprecated** in the Security BCP and dropped from OAuth 2.1. Fine for a local test tool, not something to copy
- **No MFA path** through this endpoint

## Verification

- `AuthFlowTest.devLogin_doesNotExistByDefault`
- `DevLoginTest` (with the property on): `validCredentials_createSession_andReturnUserWithoutTokens`, `wrongPassword_returns401_andNoSession`, `logout_endsKeycloakSessionServerSide_andReturns204`
- `secure-shop-test-client` in the realm file: `standardFlowEnabled: false`, `directAccessGrantsEnabled: true`

## Migration path

Step 4: a startup guard that refuses `dev-login.enabled=true` outside a local profile, and the test client moved out of non-dev realm imports. Step 6: once Playwright tests log in through the real browser flow, delete dev login and the test client.

## Revisit trigger

- The first shared environment (step 3 containers)
- Browser tests that log in for real (step 6)

Review date: 2027-04-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
