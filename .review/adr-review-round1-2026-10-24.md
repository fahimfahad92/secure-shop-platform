# ADR review — round 1 — step 1 batch (ADR-0001 to ADR-0013)

- Date: 2026-10-24
- Reviewer: architecture-kata-reviewer (ADR mode)
- Branch: `threat-model` @ `f658d41`
- Pass mark 7/10, target 8/10 (from `docs/adr/README.md`)
- Independence: this session did not draft or edit any of these ADRs. Nothing under `.review/` was read.
- Fact-check: every class, test name, property, realm setting, compose setting, risk ID/score and PR/date was checked against the working tree, `docker/keycloak/secure-shop-realm.json`, `docs/threat-model/risk-register.md` and `git log`. All 40+ test method names cited across the batch exist in the named classes. Not verified at runtime: Keycloak/JWKS behaviour during an outage (ADR-0012) and the stateless-BFF cookie size (ADR-0002).
- Pass-mark trend: no Accepted ADRs yet, so the staging rule (7 → 8) cannot trigger.

## Summary

| ADR | Title | Impact | Score | Verdict |
|---|---|---|---|---|
| 0001 | Keycloak as the only Authorization Server | high | 7.0 | ACCEPT PENDING DEFENSE (below target) |
| 0002 | BFF: tokens stay in the Gateway | high | 9.0 | ACCEPT PENDING DEFENSE |
| 0003 | Gateway checks session; services enforce rules | high | 7.5 | ACCEPT PENDING DEFENSE (below target) |
| 0004 | Owner from `sub`, `404` for others | normal | 8.5 | ACCEPT |
| 0005 | Price from the catalog | normal | 9.5 | ACCEPT |
| 0006 | Realm as code, secrets as placeholders | normal | **6.0** (rubric 8.5, capped by G3) | **REVISE** |
| 0007 | Fail at startup on a missing secret | normal | 8.5 | ACCEPT |
| 0008 | Schema per service on one Postgres | normal | 9.0 | ACCEPT |
| 0009 | Order → Product unauthenticated in v1 | high | 8.5 | ACCEPT PENDING DEFENSE |
| 0010 | Dev login behind a flag | normal | 9.0 | ACCEPT |
| 0011 | Realm roles, per-service audience | high | 9.0 | ACCEPT PENDING DEFENSE |
| 0012 | Accepted risk: single Keycloak (T30) | normal | 8.0 | ACCEPT |
| 0013 | Accepted risk: PII unencrypted (T33) | normal | 9.0 | ACCEPT |

Five high-impact ADRs need a defense before acceptance. For those, only the objection is shown below; the strong-answer bullets are withheld until you answer in drill mode.

---

## ADR 0001 — Keycloak as the only Authorization Server — 7.0/10 — ACCEPT PENDING DEFENSE (below target)

Gates: G1 ✓ G2 ✓ G3 ✓ (two wrong claims, see Accuracy; neither would flip the choice) G4 ✓ G5 ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 1.5/2 | Specific pull "to build the AS too, to see how it works". But `Drivers` lists T25, T27, T30, which are risks *created by* this decision (they reappear under Negative), not forces that drove it. | Move T25/T27/T30 out of `Drivers`; keep the learning requirement, the offline/free constraint and the Admin API need. |
| Options | 1.5/2 | Four real options. Hosted IdP con "config lives in a vendor console, harder to commit and review" is weak: Auth0 Deploy CLI and the Terraform Okta/Auth0 providers exist. The real cons (offline, account, free-tier limits) carry it alone. | Drop or correct the "can't commit" con; the offline argument is enough and is honest. |
| Decision & rationale | 1.5/2 | Rejections of SAS and hosted IdP are reasoned. Criteria are implied by the drivers, never weighed against the runner-up (hosted IdP) explicitly. One-minute answer matches the Decision. | One sentence: "hosted IdP wins on ops and hardening, loses on offline and reviewable config; we weight offline/free highest because…" |
| Consequences & risk | 1.5/2 | Honest SPOF, insecure defaults, H2 storage, resource cost. But the Positive "Switching IdP later touches configuration and two Keycloak-specific classes" understates lock-in. | Restate the switching cost truthfully (see Accuracy). |
| Accuracy & verifiability | 1/2 | Verified: Keycloak `26.5.2` (`docker/docker-compose.yml`, unchanged since PR #1), `issuer-uri` in all three services and the Gateway, no `JwtEncoder`/`PasswordEncoder` in `backend/`. **Wrong:** (1) "`TokenValidationTest` … so a token from any other issuer … is not accepted": every `AbstractIntegrationTest` sets `issuer-uri` to `""` and `TestJwts` mints tokens with no `iss`, so no test exercises the issuer check; the tests prove audience only. (2) "two Keycloak-specific classes": the Gateway also parses `realm_access` in `KeycloakRoles` and `KeycloakOidcUserService`, plus `KeycloakPasswordGrant` (dev) and the duplicated converter. | Fix both claims. Either add an issuer test (token signed by the test key with a wrong `iss`, issuer validator wired in) or say issuer validation is untested. List all Keycloak-specific classes in Consequences and Migration path. |

Interviewer's objection: "You say you 'buy the hard parts'. But with self-hosted Keycloak you still own patching, hardening (two 15-High defaults today), signing keys in an H2 file on a laptop, and availability. Which hard parts did you actually buy, and why wouldn't a hosted IdP's dev tenant buy more of them?"

(High impact: answer this in drill mode; the strong-answer shape is withheld.)

---

## ADR 0002 — BFF: tokens stay in the Gateway — 9.0/10 — ACCEPT PENDING DEFENSE

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Concrete threat ("one XSS bug and the refresh token can be used … up to 10 hours", matches `ssoSessionMaxLifespan: 36000`, `revokeRefreshToken: false`, T29). Drivers traceable to T1/T3/T4/T5/T34 and the IETF draft. | — |
| Options | 1.5/2 | Stateless BFF steelmanned well. The SPA option is presented in its weakest form (tokens in `localStorage`); the version a reviewer will raise is an in-memory SPA with refresh-token rotation and sender-constrained tokens (DPoP), which is what the browser-apps draft discusses. "Each service keeps its own session" is filler. | Replace the SPA row with its strongest form (in-memory, rotation, DPoP) and say why BFF still wins. |
| Decision & rationale | 1.5/2 | Clear decision, scope, rejections. Stateless BFF is rejected on "likely exceed the 4 KB cookie limit": unmeasured, and chunked cookies are a known workaround. | Measure one real access+refresh+ID token set from this realm and state the size, or rest the rejection on key management and no server-side kill switch alone. |
| Consequences & risk | 2/2 | Best section in the batch: XSS can ride the session, restart logs everyone out, T5 session flooding, no `Secure`, heap dump, no back-channel logout, choke point. | — |
| Accuracy & verifiability | 2/2 | Verified in `SecurityConfig`/`RouteConfig`/`application.properties`: `HttpSessionOAuth2AuthorizedClientRepository`, PKCE via `OAuth2AuthorizationRequestCustomizers.withPkce()`, refresh via `authorizedClientManager`, `Cookie` and `X-XSRF-TOKEN` stripped on every route, `http-only=true`, `same-site=lax`, `CookieCsrfTokenRepository.withHttpOnlyFalse()`. All six tests exist. Triggers and review date present. | Optional: state the Gateway session timeout (Tomcat default 30 min) next to Keycloak's 30 min SSO idle. |

Interviewer's objection: "Your own consequences say XSS can still ride the session while the tab is open. Given the frontend is same-site, what concrete attack does the BFF stop that an in-memory SPA with refresh-token rotation doesn't, and is that worth a stateful choke point in front of every call?"

(High impact: answer in drill mode.)

---

## ADR 0003 — The Gateway checks only "is there a session"; services enforce the rules — 7.5/10 — ACCEPT PENDING DEFENSE (below target)

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Specific: TB7, service ports reachable today, step 3 changes that. Drivers concrete. | — |
| Options | 1.5/2 | Header-identity option is fair. "Both … no security gain, since the service check is the one that counts" is a strawman: an edge check is a second layer exactly when a service's `SecurityConfig` is wrong, the same defense-in-depth argument the ADR uses for the services. | Steelman "Both": what it catches (a misconfigured service, load shedding) and what it costs (drift, Gateway knows every rule). Then reject it on cost, not on "no gain". |
| Decision & rationale | 1.5/2 | Rule list is explicit, one-minute answer matches. The rejection of "Both" rests on the weak claim above. | Reword the rejection once the option is steelmanned. |
| Consequences & risk | 1.5/2 | Honest about bypassable edge controls (T40), duplicated config, unauthorized load. Missing the failure mode the decision creates: authorization correctness lives in each service alone, and services are uneven. **User Service checks neither role nor scope** (`anyRequest().authenticated()` plus audience); the ADR's "services check scopes, roles, audience and ownership" is true for Order only. | Add "one layer per rule: a service misconfiguration is not caught anywhere", and state what User Service actually enforces. |
| Accuracy & verifiability | 1/2 | Gateway matchers verified. Inaccurate: "`POST /auth/dev-login` when dev login is enabled": the matcher is `permitAll` unconditionally, the endpoint is just absent (404) without the flag. "Service rules: signature, issuer, expiry and `aud` (all three)" is true in production config but `TokenValidationTest` blanks the issuer, so the cited verification doesn't cover issuer. | Fix the dev-login line; scope the `TokenValidationTest` claim to audience/signature, or add an issuer test. |

Interviewer's objection: "With this split every service is the only layer for its rules. User Service today checks neither role nor scope, only that a token has its audience. If one service's `SecurityConfig` regresses, what catches it, and why isn't a coarse edge check defense in depth by your own argument?"

(High impact: answer in drill mode.)

---

## ADR 0004 — Owner from the token's `sub`, `404` for other users' data — 8.5/10 — ACCEPT

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | BOLA plus the 403/404 existence leak, specific to orders and profiles. | Optional: T24 (mass assignment on profiles) is not a driver for ownership-by-`sub`; drop it or say why it's there. |
| Options | 1.5/2 | Three fair options. The option a reviewer suggests first given the ADR's own top residual risk (Postgres RLS or a Hibernate filter) appears only in Migration path. | Add RLS/filter as a row and reject it on cost for v1. |
| Decision & rationale | 1.5/2 | Clear; beats load-then-check on stated criteria. But driver "Ownership can't be forgotten in a new code path" is not met: the Negative says "A new repository method without `sub` is a BOLA. Only code review and the ownership tests guard this". "Closed by construction" overstates it. | Say the driver is met for existing paths only, and name the guard for new ones. |
| Consequences & risk | 2/2 | Honest: forgettable filter, no admin view, 404 hides probing (T36/T41), data keyed by Keycloak `sub`. | — |
| Accuracy & verifiability | 1.5/2 | Verified: `findByIdAndUserSub`/`findByUserSub`, update/delete via `getById`, controllers pass `jwt.getSubject()`, `keycloak_sub` PK, `/users/me` only, all six tests. Security ADR without a review date. | Add a review date. Optional: an ArchUnit rule that every `OrderRepository` finder takes `userSub` turns the residual risk into a fitness function. |

Interviewer's objection: "Your driver says ownership can't be forgotten, your consequences say a new repository method without `sub` is a BOLA. Which is it, and what stops the next developer?"

What a strong answer would contain:
- Admit the guarantee is per-method, not structural
- A mechanical guard: ArchUnit/test that every finder on owned aggregates takes the owner, or RLS keyed on a session variable
- When the cost of RLS becomes worth it (number of owned aggregates, team size)

---

## ADR 0005 — Price from the catalog, never from the client — 9.5/10 — ACCEPT

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Concrete history: PR #1's `CreateOrderRequest` really had `unitPrice` (verified in `46e1776`). | — |
| Options | 2/2 | Verify-and-reject and local price projection both steelmanned with their real benefit. | — |
| Decision & rationale | 2/2 | Rejections on stated criteria; scope clear (fallback price deferred to its own ADR). | — |
| Consequences & risk | 2/2 | Availability coupling, T38, HTTP call inside class-level `@Transactional` (verified), stock not reserved, quantity change without stock check. | Optional: "fixed in step 5 and step 4" for a forged reply: only TLS (step 4) fixes reply forgery; see cross-ADR finding 4. |
| Accuracy & verifiability | 1.5/2 | Verified: `(productId, quantity)` record, `ProductClient` 404→`ProductNotFoundException`→400, other errors/timeouts→503, `update` uses stored `unitPrice`. All four tests exist. Security ADR without a review date. | Add a review date. |

Interviewer's objection: "Price integrity now depends on an unauthenticated plaintext HTTP reply. Isn't the vulnerability just moved from the public API to the internal network?"

What a strong answer would contain:
- Yes, and it's recorded as T38 with likelihood reasoning (single host today)
- Reply forgery needs server authentication (TLS), caller auth alone doesn't fix it
- Dates: step 4 TLS, step 5 caller auth, and the trigger that pulls them forward (shared network, step 3)

---

## ADR 0006 — Realm as code, secrets as `${...}` placeholders — 6.0/10 (rubric 8.5) — REVISE

Gates: G1 ✓ G2 ✓ **G3 ✗** (a committed admin credential contradicts "no secret in the repository", and the verification gives false assurance) G4 ✓ G5 ✓

The realm export commits two users with a known password, imported into every environment that uses the file:

```
docker/keycloak/secure-shop-realm.json:491  testuser   "credentials": [{ "type": "password", "value": "test" }]
docker/keycloak/secure-shop-realm.json:504  adminuser  "credentials": [{ "type": "password", "value": "test" }], realmRoles: [..., "admin"]
```

`adminuser`/`test` works through the real browser login (`secure-shop-gateway`), not only the dev test client, so it's the same class of problem as T26 (12 High) with a wider reach. It's not in this ADR, not in ADR-0010, and not in the risk register (no match for `adminuser`, `testuser` or seeded users under `docs/threat-model/`). The decision's core driver is "No secret in the repository" and its verification (`grep '"secret"'`) only covers client secrets. That's why G3 fails and not just a deduction: the decision's scope (which credentials are placeholders) is what's wrong.

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Real pain (manual console setup), three client secrets, reviewability. | — |
| Options | 2/2 | keycloak-config-cli/Terraform steelmanned, even named as the better fit later. | — |
| Decision & rationale | 2/2 | Clear, with the import-only-once mechanics stated (`IGNORE_EXISTING` behaviour, compose comment). | — |
| Consequences & risk | 1.5/2 | Honest on import-only, drift, noise (2,862 lines, "about 2,900" ✓), insecure defaults, T26, T35. Misses the seeded known-password admin. | Add it as residual risk with a T-ID. |
| Accuracy & verifiability | 1/2 | Verified: three `${...}` secrets at lines 796/859/989, `${VAR:?set it in docker/.env}` in compose, `.env` gitignored, `.env.example`. Wrong: "No secret in the repository" given committed user credentials; verification grep can't see them. | Extend the verification to `"credentials"` values. |

Required changes (in order of points gained):
1. Decide what seeded users are: either placeholder their passwords (`${KEYCLOAK_TESTUSER_PASSWORD}`, `${KEYCLOAK_ADMINUSER_PASSWORD}` from `docker/.env`), or record them as dev-only fixtures with known passwords that must not reach a non-dev import (same step 4 split as the test client). State the choice in the Decision. (Clears G3.)
2. Add the residual risk to Consequences and a new risk-register entry (and the threat-model changelog), scored with DREAD like T26.
3. Change the verification to cover credentials, e.g. `grep -n '"value"' secure-shop-realm.json` under `credentials` shows only `${...}`, and make step 3's secret scanning include it.
4. Cross-reference from ADR-0010, whose "test client live in every environment" risk combines with a known admin password.

Interviewer's objection: "Your realm-as-code puts every setting in review, including a working admin password. What exactly does 'no secret in the repository' mean in this ADR, and who decided a seeded admin with a known password isn't one?"

What a strong answer would contain:
- A definition: any credential that authenticates in any imported environment is a secret
- Seeded users either placeholdered or confined to a dev-only import, with the mechanism named
- A check that enforces it (grep/secret scanning over `credentials`), not a convention

---

## ADR 0007 — Fail at startup on a missing secret — 8.5/10 — ACCEPT

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Real incident (IDE started before env vars), the misleading 401 explained. The mechanism is correct: `@ConfigurationProperties` binding ignores unresolvable placeholders. | — |
| Options | 1.5/2 | Bean validation is rejected because "the literal `${...}` text is not blank". The obvious variant, `${KEYCLOAK_ADMIN_CLIENT_SECRET:}` (empty default) plus `@Validated @NotBlank`, would catch it declaratively in User Service. The real reason it doesn't work for the Gateway (secrets bind into Boot's own `OAuth2ClientProperties`, which you can't annotate) is not stated. | Add the empty-default variant and reject it on the real grounds: works for User Service, not for Boot-owned properties, and you want one rule shape in both. |
| Decision & rationale | 1.5/2 | Clear, scoped (Order/Product hold no client secrets). Rationale rests on the incomplete rejection above. | Follows from the Options fix. |
| Consequences & risk | 2/2 | Two implementations, DB passwords uncovered (T32), wrong-but-present secret still starts, `${` heuristic. | — |
| Accuracy & verifiability | 1.5/2 | Verified: `ClientSecretCheck` (`InitializingBean`, id→variable map, message names variable and `docker/.env`, IDE hint), `KeycloakAdminProperties` compact constructor, all six tests. Security ADR without a review date. | Add a review date. |

Interviewer's objection: "Why hand-write a check when `${VAR:}` plus `@NotBlank` gives you the same failure declaratively?"

What a strong answer would contain:
- It does work for User Service's own record
- The Gateway's secrets bind into Boot's `OAuth2ClientProperties`, which only validates `client-id`, so a custom check is needed there anyway
- One rule shape in both services beats two mechanisms; revisit with the step 2 shared-code decision

---

## ADR 0008 — Schema per service on one Postgres instance — 9.0/10 — ACCEPT

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 1.5/2 | Clear but generic: these drivers fit any laptop microservice project. | Tie it to this system: the only cross-service data need (price) and why an API call is enough. |
| Options | 2/2 | Database-per-service presented as a close second with a real advantage. | — |
| Decision & rationale | 2/2 | Exact config named; step 2 reopens it explicitly. | — |
| Consequences & risk | 2/2 | "Isolation is a convention": superuser `postgres`/`postgres`, T31/T32. | — |
| Accuracy & verifiability | 1.5/2 | Verified: `currentSchema`, `flyway.schemas`, `flyway.default-schema`, `hibernate.default_schema`, `ddl-auto=validate` in all three services since PR #1 (`46e1776`); grep shows each schema name only in its own service. Verification is a manual grep. | A test (or CI grep) that fails if a service's code references another schema name. |

Interviewer's objection: "A convention that every service connects as superuser is not data ownership. Why not do per-service roles now. It's three `CREATE ROLE` statements?"

What a strong answer would contain:
- The cost isn't the SQL, it's the Flyway owner/app role split, which step 2 decides
- Today's exposure is bounded (single host, test data) and recorded (T31/T32)
- The trigger that pulls it forward

---

## ADR 0009 — Order → Product call unauthenticated in v1 — 8.5/10 — ACCEPT PENDING DEFENSE

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Single call site, learning-scope reason explicit, #03 named. | — |
| Options | 2/2 | Five options; relay-user-token steelmanned and rejected on a specific future fact (`reserve-stock` caller is the service). | — |
| Decision & rationale | 1.5/2 | Driver "the call reads only public data" covers confidentiality, but the property at stake is the *integrity* of the reply (the price, per ADR-0005). The rationale never separates the two. | One sentence: confidentiality is public, integrity is not; integrity needs server authentication (TLS), which is step 4, not #03. |
| Consequences & risk | 1.5/2 | Honest and quantified (T38 8 Medium). But "Forged replies set the price … Fixed by step 5 (auth) and step 4 (TLS)": a Client Credentials token on the request does nothing against a forged reply; only TLS (or mTLS) does. | Split the fixes: TLS fixes forged replies; caller auth fixes caller identity and protects `reserve-stock`. |
| Accuracy & verifiability | 1.5/2 | Verified: no `Authorization` header in `ProductClient`, Javadoc names #03, timeouts 2000/3000 ms in `ProductClientConfig` and properties, errors→503. The "hard rule" (no non-public s2s call without auth) has no enforcement; the ADR admits no test. Review date is an event. | A fitness check: a test that fails if `ProductClient` calls any path other than `GET /products/{id}` without auth, or a step-5 checklist item that blocks the `reserve-stock` PR. |

Interviewer's objection: "You say the call only reads public data. But the reply sets the price on every order. Confidentiality is public, integrity isn't. Why is an unauthenticated, unencrypted channel acceptable for the one value ADR-0005 calls security-critical, and what actually enforces your 'hard rule' when step 5 starts?"

(High impact: answer in drill mode.)

---

## ADR 0010 — Dev login: password grant behind a property and the `dev` profile — 9.0/10 — ACCEPT

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Specific: Postman can't follow the code flow into a Gateway session; BCP cited. | — |
| Options | 1.5/2 | Five options. Missing the no-code one a reviewer suggests first: log in once in a browser and copy `JSESSIONID` + `XSRF-TOKEN` into Postman's cookie jar, which tests the real BFF path. | Add it; reject it on automation (CI/Newman can't do it, sessions expire) if that's the reason. |
| Decision & rationale | 1.5/2 | Decision bullets are precise, but unlike the rest of the batch there is no paragraph saying why the chosen option beats the runner-up; it's left to the table. | Two sentences comparing it with the runner-up (Postman OAuth helper or the cookie copy). |
| Consequences & risk | 2/2 | Test client live in every import (T26), flag is the only guard, login CSRF, deprecated grant, no MFA. | Add the seeded `adminuser`/`test` from ADR-0006 to the "test client live everywhere" point: together they're a known admin password grant. |
| Accuracy & verifiability | 2/2 | Verified: `@ConditionalOnDevLogin` = `@ConditionalOnProperty("secure-shop.dev-login.enabled","true")`, set only in `application-dev.properties`; `keycloak-test` registration; test client `standardFlowEnabled:false`, `directAccessGrantsEnabled:true`; gateway client `directAccessGrantsEnabled:false`; CSRF ignored on the path; `DevLoginLogoutHandler` + 204. All tests exist. Review date present. | — |

Interviewer's objection: "Why ship a password endpoint in the Gateway at all, when copying a real browser session's cookies into Postman tests the same path with zero code?"

What a strong answer would contain:
- Repeatability: Newman/CI, many users (testuser vs adminuser), sessions that expire mid-run
- It exercises refresh and logout on demand, which a pasted cookie can't
- The cost is bounded by the flag and removed in step 6

---

## ADR 0011 — Realm roles for `user`/`admin`, an audience per service — 9.0/10 — ACCEPT PENDING DEFENSE

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | The before-state (admin modelled on the client) is a real, specific bug. | — |
| Options | 2/2 | Two tables, each with a steelmanned runner-up (client roles, shared audience). | — |
| Decision & rationale | 1.5/2 | "Scopes say what the app may do" is applied by Order Service only. Product uses role only for writes; User Service checks neither role nor scope (`anyRequest().authenticated()`). The driver "`admin` and `user` mean the same thing to every service" is moot for User Service. The ADR's reach is wider than the code. | State per service what is enforced (Order: scope+role, Product: `admin` on writes, User: audience+ownership only) and whether User Service should require `ROLE_user`. |
| Consequences & risk | 2/2 | Unusually honest: one token valid at all three services, global admin, T17 via realm default scopes and the service account's `default-roles` composite (all verified in the realm). | — |
| Accuracy & verifiability | 1.5/2 | Verified: `secure-shop-audience` with three audience mappers, default on gateway and test client only; `orders:*` in `defaultDefaultClientScopes`; `user` in `default-roles-secure-shop`; converter keeps `SCOPE_*`; `audiences` property in each service. "`TokenValidationTest` in each service: `tokenForAnotherService_returns401`" — Product has no method of that name (it has the `admin…` variant); the issuer path is untested everywhere. | Name the tests per service precisely. |

Interviewer's objection: "You rejected one shared audience because every service would accept every token. But the Gateway puts all three names in every user token, so today a token leaked at Product is valid at Order and User. What does per-service audience buy you over a shared audience right now, and when does that change?"

(High impact: answer in drill mode.)

---

## ADR 0012 — Accepted risk: single Keycloak instance (T30) — 8.0/10 — ACCEPT

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Outage behaviour listed precisely (300 s token lifespan, Gateway discovery at startup, both verified). | — |
| Options | 1.5/2 | Accept vs cluster vs hosted. Missing the cheap partial mitigations: (a) let the Gateway start without Keycloak (explicit provider endpoints instead of `issuer-uri` discovery), which removes the coupling the ADR lists as a negative; (b) single-node production mode on the existing Postgres, which buys durability (no H2) without HA. | Add both and accept or reject them. |
| Decision & rationale | 1.5/2 | Acceptance reasoning is sound. It doesn't say what would make the risk unacceptable before the triggers fire (for example, the first time an outage blocks a demo or test run). | One line of "unacceptable when …". |
| Consequences & risk | 1.5/2 | Covers availability. Two gaps: (1) durability. H2 under `docker/keycloak-data/` holds users and signing keys; corruption loses accounts, and new keys invalidate every session. (2) "Requests with a still-valid access token keep working": only for services that already fetched the JWKS and only while the decoder's JWK cache holds. A service restarted during the outage resolves the issuer lazily and fails every request. | Add both. |
| Accuracy & verifiability | 1.5/2 | Verified: one `keycloak` service, `start-dev --import-realm`, `restart: unless-stopped`; DREAD 2/2/2/3/1 = 10 matches T30. The keep-working claim is unverified (manual check only). | Extend the manual check: restart Order Service during the outage and record the result. |

Interviewer's objection: "You accept unavailability, but the bigger loss is durability: users and signing keys live in an H2 file. What happens to every account and every session if that file is corrupted, and why isn't production mode on the Postgres you already run the cheap fix?"

What a strong answer would contain:
- Impact: accounts lost, re-import gives seed users only, User Service profiles orphaned (keyed by `sub`, ADR-0004)
- Production mode on existing Postgres is the step-4 path; cost (TLS/hostname requirements) vs benefit stated
- Until then: back up `docker/keycloak-data/`, or accept data loss explicitly in this ADR

---

## ADR 0013 — Accepted risk: profile PII unencrypted at rest (T33) — 9.0/10 — ACCEPT

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Exact columns, location, and the key argument (at-rest encryption vs SQL access). | — |
| Options | 2/2 | Disk, column (`AttributeConverter`, AES-GCM) and `pgcrypto`, each with real cons. | — |
| Decision & rationale | 1.5/2 | Prioritisation argument (T31/T32 first) is strong. **One-minute answer cap applies:** its third sentence is a future plan, not the main cost; the cost (a copied data folder exposes PII) is missing from it. | Replace the third sentence with the cost. |
| Consequences & risk | 1.5/2 | Scope stops at `user_schema.profiles`. Keycloak's H2 (`docker/keycloak-data/`) holds the same usernames, emails and names, equally unencrypted (T35). | Extend scope or name it as out of scope with its risk ID. |
| Accuracy & verifiability | 2/2 | Verified: `V1__create_profiles_table.sql` plain `VARCHAR`, bind mount `./postgres-data`, DREAD 2/1/2/3/1 = 9 matches T33. Triggers and review date present. | — |

Interviewer's objection: "You accept the risk for 'test data only', but `POST /users/register` is open to anyone who can reach the Gateway. What stops real personal data from arriving before your trigger notices?"

What a strong answer would contain:
- The trigger is deployment-shaped (local machine only, ports on localhost after step 3/4)
- An observable check: no deployment outside localhost without revisiting this ADR (checklist or CI gate)
- Optional: a banner or seed-only policy in the README for anyone running the stack

---

## Cross-ADR findings

1. **Seeded users with known passwords (0006, 0010, threat model).** `testuser`/`test` and `adminuser`/`test` (admin role) are committed in the realm export and imported everywhere. Not in any ADR or the risk register. Drives the 0006 REVISE; 0010 should reference it.
2. **Issuer validation is untested everywhere (0001, 0003, 0011).** All three `AbstractIntegrationTest`s set `issuer-uri` to `""` and `TestJwts` sets no `iss`. ADRs that cite `TokenValidationTest` as issuer verification overclaim. Fix once: a test with a wrong `iss` in each service (wire `JwtValidators.createDefaultWithIssuer` in tests), or reword the three ADRs.
3. **"Services enforce scopes and roles" vs User Service (0003, 0011).** User Service enforces audience and ownership only, no role or scope. Either intended (state it in 0011) or a gap (risk entry, step 2).
4. **Reply forgery vs caller authentication (0005, 0009).** Both say T38 is fixed by "step 5 (auth) and step 4 (TLS)". Only server authentication (TLS/mTLS) stops a forged reply; Client Credentials authenticates the caller. Worth one consistent sentence in both.
5. **Missing review dates on security ADRs (0004, 0005, 0007)**; 0009 uses an event ("when step 5 starts"), acceptable for a superseding-by-design ADR but say so.
6. **Consequences listed as drivers (0001: T25/T27/T30; 0005: T38; 0004: T24).** Drivers should be the forces behind the decision; risks the decision creates belong under Negative.
7. **No contradictions or missing `Supersedes` links.** Forward links in 0008 ("to be extended") and 0009 ("to be superseded") are correctly placed in `Superseded by`. Index titles, categories and impact match all 13 headers; the Score column is empty and should be filled from the review records.
8. **Repeated reasoning, not duplication.** "Converter duplicated / finding #13" appears in 0001, 0003, 0007 and 0011. Fine as pointers; the step 2 shared-code ADR should become the single home and these should link to it.

## Defenses needed (high impact)

Answer each objection above in drill mode, one ADR at a time: 0001, 0002, 0003, 0009, 0011. On a passed defense, record `Defense: passed <date>` in that ADR's review record. 0001 and 0003 are below target; the optional changes in their tables would lift them to 8.

## Review records (paste at the bottom of each ADR)

ADR 0001
```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 7/10 | ACCEPT PENDING DEFENSE (below target) |
```

ADR 0002
```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 9/10 | ACCEPT PENDING DEFENSE |
```

ADR 0003
```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 7.5/10 | ACCEPT PENDING DEFENSE (below target) |
```

ADR 0004
```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 8.5/10 | ACCEPT |
```

ADR 0005
```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 9.5/10 | ACCEPT |
```

ADR 0006
```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 6/10 (G3) | REVISE |
```

ADR 0007
```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 8.5/10 | ACCEPT |
```

ADR 0008
```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 9/10 | ACCEPT |
```

ADR 0009
```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 8.5/10 | ACCEPT PENDING DEFENSE |
```

ADR 0010
```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 9/10 | ACCEPT |
```

ADR 0011
```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 9/10 | ACCEPT PENDING DEFENSE |
```

ADR 0012
```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 8/10 | ACCEPT |
```

ADR 0013
```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 9/10 | ACCEPT |
```
