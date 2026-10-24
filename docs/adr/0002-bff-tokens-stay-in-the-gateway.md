# 0002. BFF: tokens stay in the Gateway, the browser holds only a session cookie

| | |
|---|---|
| Status | Proposed |
| Date | 2026-10-24 (recorded). Decided 2026-10-04, #01 Phase 4, PR #5 |
| Category | security |
| Impact | high |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5, retroactive) |
| Drivers | T1, T3, T4, T5, T34; OAuth 2.0 for Browser-Based Applications (IETF draft) |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

A browser app (the Next.js frontend in step 6; Postman until then) calls the APIs on behalf of a logged-in user. The services need a bearer access token. Somewhere, an access token and a refresh token have to be held between requests.

If the browser holds them, any script running on the page can read them: our code, every npm dependency, any third-party widget. One XSS bug and the refresh token can be used from the attacker's machine until it expires, which is up to 10 hours with the current realm settings (T29).

Where do the user's tokens live, and what does the browser hold?

## Decision drivers

- XSS must not be able to take a token off the device
- The refresh token must never reach the browser
- The login client should be confidential (have a secret), which a browser cannot keep
- Works with the Next.js frontend planned for step 6 without changing the services
- Simple enough for one instance on a laptop

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **BFF: Gateway is a confidential client, tokens in its server-side session, browser gets an HttpOnly session cookie** | No token in the browser; refresh token stays server-side; confidential client; services unchanged | Gateway becomes stateful; needs CSRF protection; sits in the path of every call |
| SPA as a public client (PKCE in the browser), tokens in memory or `localStorage` | No state on the server; the common tutorial setup | XSS can read and exfiltrate tokens; refresh token in the browser; public client only |
| Stateless BFF: tokens encrypted into an HttpOnly cookie | No session store; scales out freely | Access, refresh and ID tokens together likely exceed the 4 KB cookie limit; we own the encryption key and its rotation; a stolen cookie works until the tokens expire, with no server-side kill switch |
| Each service keeps its own session | None worth having | Every service handles login and CSRF; tokens are pointless |

## Decision outcome

**Chosen: BFF in the Gateway (Spring Cloud Gateway, WebMVC).**

- The Gateway is the confidential OIDC client `secure-shop-gateway`: Authorization Code with PKCE `S256`, client secret on the token call
- Tokens are stored in the `HttpSession` through `HttpSessionOAuth2AuthorizedClientRepository`, so they die with the session
- The browser gets `JSESSIONID` (`HttpOnly`, `SameSite=Lax`) and a readable `XSRF-TOKEN` cookie, nothing else
- On private routes the Gateway relays the access token as `Authorization: Bearer`, refreshing it first when it has expired; it strips `Cookie` and `X-XSRF-TOKEN` before forwarding
- Writes need the CSRF token as a header (`CookieCsrfTokenRepository`), because the cookie is sent automatically

The SPA option was rejected because it leaves the refresh token readable by any script. The stateless BFF was rejected for v1 because of cookie size and key management; it becomes interesting if the Gateway has to scale out without a shared session store.

## One-minute answer

The browser never sees a token: the Gateway is a confidential OAuth client, keeps the tokens in its server-side session, and gives the browser only an HttpOnly session cookie. That way an XSS bug cannot steal a token and use it from another machine. The cost is a stateful Gateway that has to handle CSRF and session storage, and that every call goes through.

## Consequences

### Positive

- XSS can't exfiltrate a token or the refresh token
- Services stay simple bearer-token resource servers; the browser model can change without touching them
- One place for login, refresh and logout

### Negative (residual risk)

- **XSS can still ride the session.** While the victim's tab is open, injected script can call the API as the user. BFF limits theft, not misuse. CSP and output encoding in step 6 are still needed
- **Stateful, single instance.** Sessions live in memory. A Gateway restart logs everyone out, and a second instance needs Spring Session with a shared store
- **Session flooding.** Every login start creates a session (T5, 13 High, step 6 rate limit and session cap)
- **Cookie on plain HTTP.** No `Secure` flag and no TLS yet (T1, 12 High, step 4)
- **Memory dump exposes every user's tokens** (T34, step 4 keeps Actuator `heapdump` off)
- **No back-channel logout.** Ending the session in Keycloak directly does not end the Gateway session
- **Latency and a choke point.** Every API call passes through the Gateway

## Verification

- `AuthFlowTest.loginStart_redirectsToKeycloakWithPkceS256`
- `AuthFlowTest.me_withSession_returnsUserAndRoles_butNoToken`
- `DevLoginTest.validCredentials_createSession_andReturnUserWithoutTokens`
- `GatewayRoutingTest.write_withSessionButNoCsrfToken_returns403_andNothingIsForwarded`
- `GatewayRoutingTest.write_withSessionAndCsrfToken_isForwardedWithBearerButWithoutCsrfHeader`
- `GatewayRoutingTest.publicProductRead_isForwardedWithoutAnyBrowserCredentials`

## Migration path

- Second Gateway instance: Spring Session (JDBC or Redis); the authorized-client repository already uses the session, so tokens move with it
- Shared environment (step 4): TLS, `Secure` cookie, consider the `__Host-` cookie prefix
- Step 6: Next.js served from the same site as the Gateway, so `SameSite=Lax` keeps working

## Revisit trigger

- A second Gateway instance is needed
- The frontend is served from a different site than the Gateway
- Next.js server components need to call the APIs server-side as the user

Review date: 2027-10-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
