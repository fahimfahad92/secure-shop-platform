# STRIDE — E2 Gateway

Legend, status values and how to update: [../README.md](../README.md). Diagram and IDs (`E…`, `F…`, `TB…`): [../system.md](../system.md). Scores and status per threat: [../risk-register.md](../risk-register.md).

| ID | STRIDE | Threat | Control today | Gap |
|---|---|---|---|---|
| T1 | S | Session hijack: `JSESSIONID` read off the network gives the attacker the user's session, and with it every call the user can make | Cookie is HttpOnly and `SameSite=Lax`, so scripts and most cross-site requests can't use it | No TLS, so no `Secure` flag; anyone on the path sees the cookie (documented gap) |
| T2 | S | Session fixation: attacker plants a session id before login, then shares the logged-in session | Spring Security changes the session id on OAuth2 login; `DevLoginController.startSession` calls `changeSessionId()` | No test asserts the id changes |
| T3 | T | CSRF: a malicious site makes the victim's browser send a write with their cookie | `CookieCsrfTokenRepository` + `SpaCsrfTokenRequestHandler`; only `/users/register` and `/auth/dev-login` exempt. Tests: `GatewayRoutingTest.write_withSessionButNoCsrfToken_returns403_andNothingIsForwarded`, `AuthFlowTest.logout_withoutCsrfToken_returns403` | Login CSRF on `/auth/dev-login` (victim logged into the attacker's account); dev profile only |
| T4 | I | Tokens leak to the browser or downstream | `MeResponse` has no token fields (`AuthFlowTest.me_withSession_returnsUserAndRoles_butNoToken`, `DevLoginTest.validCredentials_createSession_andReturnUserWithoutTokens`); `Cookie` and CSRF header stripped before forwarding (`GatewayRoutingTest.publicProductRead_isForwardedWithoutAnyBrowserCredentials`) | A client-sent `Authorization` header is not stripped on public routes (carried-over item); low impact, services validate it |
| T5 | D | Session flooding: every unauthenticated `GET /oauth2/authorization/keycloak` creates an `HttpSession` (state + PKCE verifier) in memory; a loop fills the heap | Default 30-minute session timeout only | No rate limit, no session cap; sessions are in-process memory |
| T6 | D | A slow or hung service ties up Gateway threads | — | No upstream timeouts or `503` mapping (documented gap) |
| T7 | E | Dev login (password grant, no PKCE) switched on outside local testing | `@ConditionalOnDevLogin`, property set only in `application-dev.properties`; `AuthFlowTest.devLogin_doesNotExistByDefault`; loud startup warning | One property away; nothing blocks it in a shared environment |
| T8 | E | A request on a public route reaches private behaviour | Route and access rules agree; unmatched paths denied (`GatewayRoutingTest.unknownPath_isDenied_withOrWithoutSession`, `productWrite_needsASession_unlikeProductRead`) | — |

