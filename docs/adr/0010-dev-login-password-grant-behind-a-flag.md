# 0010. Dev login: password grant behind a property and the `dev` profile

| | |
|---|---|
| Status | Accepted |
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

- `@ConditionalOnDevLogin` (`@ConditionalOnProperty("secure-shop.dev-login.enabled", havingValue = "true")`) guards every dev-login bean (`DevLoginController`, `KeycloakPasswordGrant`, `DevLoginLogoutHandler`); only `application-dev.properties` sets the property
- The guard is the property, not the profile: an env var (`SECURE_SHOP_DEV_LOGIN_ENABLED=true`) or a command-line argument also turns it on. But `KeycloakPasswordGrant` refuses to start without the `keycloak-test` client registration, which also lives only in `application-dev.properties`, so the flag alone fails at startup. In practice dev login needs the whole `dev` profile, or someone copying both settings by hand
- When it is on, the Gateway logs a `WARN` at startup ("DEV LOGIN ENABLED ... never enable this in a shared environment")
- Without the property `POST /auth/dev-login` has no handler and returns `404`, without calling Keycloak. The path is `permitAll` and CSRF-exempt in `SecurityConfig`, like `/users/register`: there is no session yet to protect
- The password grant runs against `secure-shop-test-client` (`directAccessGrantsEnabled: true`, standard flow off); `secure-shop-gateway` keeps the standard flow only
- The session is built like a browser login's: the ID token is checked the same way (signature, issuer, audience, expiry), the session id is rotated on login (session fixation), and only the user and the session cookie reach the caller, never a token
- A wrong password is `401`; Keycloak refusing the test client itself (wrong `KEYCLOAK_TEST_CLIENT_SECRET`) is `502`, so a config error isn't reported as the user's fault; a missing field is `400` without calling Keycloak
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

- **The test client is live in Keycloak in every environment that imports the realm**, whatever the Gateway's profile. Anyone who knows its secret can use the password grant directly at Keycloak (T26, 12 High; step 4 keeps it out of non-dev imports). The realm also seeds `adminuser` with the committed password `test` ([ADR-0006](0006-realm-as-code-secrets-as-placeholders.md), T43), so the secret plus the repo give an admin token without any login page
- **The profile is the only guard.** Starting a shared Gateway with the `dev` profile opens a password endpoint with no rate limit and no CSRF check (login CSRF, T3), on top of no brute-force protection in Keycloak (`bruteForceProtected: false` in the realm) (T7, 11 Medium; T25, 15 High). Nothing checks where the Gateway runs; the startup `WARN` is the only signal. Step 4 adds a startup guard
- **Password grant is deprecated** in the Security BCP and dropped from OAuth 2.1. Fine for a local test tool, not something to copy
- **No MFA path** through this endpoint

## Verification

- `AuthFlowTest.devLogin_doesNotExistByDefault`: `404` and no request reaches Keycloak
- `DevLoginTest` (with the property on): `validCredentials_createSession_andReturnUserWithoutTokens`, `devSession_relaysTheAccessTokenLikeABrowserSession`, `wrongPassword_returns401_andNoSession`, `wrongClientSecret_returns502_notAWrongPasswordMessage`, `missingPassword_returns400_withoutCallingKeycloak`, `logout_endsKeycloakSessionServerSide_andReturns204`
- Realm file: `secure-shop-test-client` has `standardFlowEnabled: false`, `directAccessGrantsEnabled: true`; `secure-shop-gateway` has `standardFlowEnabled: true`, `directAccessGrantsEnabled: false`
- No test covers the startup failure when the flag is on without the `keycloak-test` registration (code only)

## Migration path

Step 4: a startup guard that refuses `dev-login.enabled=true` outside a local profile, and the test client moved out of non-dev realm imports. Step 6: once Playwright tests log in through the real browser flow, delete dev login and the test client.

## Revisit trigger

- The first shared environment (step 3 containers)
- Browser tests that log in for real (step 6)

Review date: 2027-04-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-11-08 | architecture-kata-reviewer | 8.5/10 | ACCEPT |
