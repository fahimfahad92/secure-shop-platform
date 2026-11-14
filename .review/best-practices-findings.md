# Best-Practices Review — Open Findings

Review working file, tracked in git (since 2026-11-01) so it moves between devices. When a finding is fixed, mark it `Done` in the Status column and add a `**Status:**` line under its heading. The section stays as the record (changed 2026-11-14; fixed findings used to be deleted).

- Source: `/best-practices-review audit` on `main` @ `3afe73f`, 2026-10-10
- Scheduled: **step 2** of the build sequence (after #05 threat model, before #04). Includes per-service Postgres users + schema-only privileges (#6). #14 is handled by #04 Phase 3, not here. #05 may add more items assigned to step 2
- Plan: `Interview-Preparation/project-ideas/README.md` → Build Sequence
- ADRs expected when fixing **#30 (replaces #2's), #3, #6, #7, #13** (list + convention: `Interview-Preparation/project-ideas/README.md` → ADR Convention). Write the ADR in `docs/adr/` as part of that finding's fix
- Workflow: pick one finding → understand it → fix → tests pass → mark it `Done` here (Status column + `**Status:**` line)
- **Threat model links:** #1 → T21, #2 → T18, #3 → T12, #6 → T31 + T32, #15 → T2, #16 → T9. When one of these is fixed, also update `docs/threat-model/` (risk register status, PR, residual DREAD; Control/Gap in the STRIDE file; changelog)
- Severity: BLOCKER > MAJOR > MINOR > SUGGESTION
- Not flagged on purpose (documented in `docs/architecture.md` → "Next plans"): stock race/decrement, client-settable order status, Gateway upstream timeouts, service-to-service auth, registration rate limit

| # | Severity | Rule | Title | Service | Status |
|---|---|---|---|---|---|
| 1 | MAJOR | GEN-DES-05 | Profile delete can't roll back | user-service | Open |
| 2 | MAJOR | SB-JPA-02 | HTTP call inside DB transaction | order-service | Open |
| 3 | MAJOR | SB-JPA-05 | Unpaginated list endpoints | product, order | Open |
| 4 | MINOR | SB-CFG-08 | `open-in-view` not disabled | order, product, user | Done 2026-11-14 (`97b7dca`) |
| 5 | MINOR | GEN-OTHER | `show-sql=true` in base config | order, product, user | Open |
| 6 | MINOR | SB-CFG-05 | Shared hardcoded DB superuser | order, product, user | Open |
| 7 | MINOR | SB-API-02 | Two error response shapes | order, product, user | Open |
| 8 | MINOR | SB-JPA-06 | No optimistic locking | order, product, user | Open |
| 9 | MINOR | SB-IO-01/03 | Dev-login HTTP client: no timeouts | gateway | Open |
| 10 | MINOR | GEN-SMELL-10 | Dead repository method | user-service | Open |
| 11 | SUGGESTION | SB-JPA-10 | Anemic entities, duplicated total calc | order-service | Open |
| 12 | SUGGESTION | SB-CFG-02 | Inconsistent config binding | order, gateway | Open |
| 13 | SUGGESTION | GEN-DIST-08 | Code copy-pasted across services | all | Open |
| 14 | SUGGESTION | GEN-DEP-05 | No dependency/CVE scan in CI | CI | Open |
| 15 | MINOR | GEN-TEST-01 | No test for session id rotation on login (threat T2) | gateway | Open |
| 16 | MINOR | GEN-TEST-01 | No test for `PUT /products/{id}` without `admin` (threat T9) | product-service | Open |
| 17 | MINOR | GEN-TEST-01 | No test for a token from the wrong issuer (ADR-0001 round 1) | order, product, user | Open |
| 18 | MINOR | SB-SEC | User and Order Service `anyRequest()` defaults fail open for new endpoints (ADR-0003) | user, order | Open |
| 19 | MINOR | SB-SEC | Wildcard matchers on permissive rules (ADR-0003) | product, order, gateway | Open |
| 20 | MINOR | GEN-TEST-01 | No route-manifest or cross-user ownership test (ADR-0003) | order, product, user | Open |
| 21 | MINOR | SB-SEC | Unscoped `JpaRepository` methods on owner-scoped repositories (ADR-0004) | order, user | Open |
| 22 | MINOR | SB-SEC | Order Service trusts Product Service's reply unchecked (ADR-0009, T38) | order-service | Open |
| 23 | MINOR | GEN-TEST-01 | Dev-login guard has no ArchUnit rule and no startup-failure test (ADR-0010) | gateway | Open |
| 24 | MINOR | GEN-TEST-01 | Realm facts the ADRs rely on are checked by inspection only (ADR-0011, ADR-0006) | realm | Open |
| 25 | MINOR | GEN-TEST-01 | No context-level startup test for a missing secret (ADR-0007) | gateway, user | Open |
| 26 | SUGGESTION | SB-SEC | `admin-cli` keeps direct access grants in the app realm (ADR-0006) | realm | Open |
| 27 | SUGGESTION | GEN-OTHER | "Test data only" has no visible notice (ADR-0013) | docs | Open |
| 28 | MINOR | GEN-OTHER | ADR-0014 accepted with its round 1 REVISE items open | docs | Open |
| 29 | MINOR | SB-CFG | HikariCP not configured: defaults everywhere, 30 s connection wait | order, product, user | Done 2026-11-14 (`97b7dca`) |
| 30 | MAJOR | SB-JPA | Decision: no `@Transactional`; failures recorded as state, not rolled back | order, product, user | Open |
| 31 | MINOR | SB-API | A DB outage answers 503 "Database busy" and logs at WARN; 500 branch untested | order, product, user | Open |
| 32 | MINOR | GEN-DEP | Testcontainers version override in 3 POMs; remove when the Boot parent is >= 3.5.16 | order, product, user | Open |

Suggested order (changed 2026-11-14: #4 + #29 moved first, so the #30 ADR has its precondition and its check in place): ~~4 + 29 (config)~~ done → 30 (ADR first: it sets the direction for 1, 2, 22) → 1 → 2 + 22 (both in `OrderService.create` and the Product call), 3 → 5, 6 → 7, 8 → 9, 10, 12 → 11, 13 → tests 15, 16, 17, 23, 25 → realm 24 + 26 → 18, 19, 20, 21 → docs 27, 28 (design calls; 14 belongs to project #04).

---

## 1 — MAJOR — GEN-DES-05 — Profile delete can't roll back

**Where:** `backend/user-service/src/main/java/com/fahim/userservice/service/ProfileService.java:92-100` (`ProfileService#delete`)

**What:** `delete()` has no `@Transactional` (class has none either). `profileRepository.delete(profile)` runs in the repository's own transaction and commits immediately. Then `keycloakAdminClient.deleteUser(...)` is called. The Javadoc says the local row goes first "because it is the one we can roll back by simply failing" — but nothing rolls it back.

**Why it matters:** Keycloak down or returns 5xx → profile row is gone, Keycloak user still exists. User can still log in, but `GET /users/me` returns 404 forever and re-registering fails with 409 (username taken in Keycloak). Existing test `delete_removesProfileThenKeycloakUser` only covers the happy path with mocks, so it can't catch this.

**Fix direction (revised 2026-11-08, follows #30: no `@Transactional`, failures recorded as state):** Mark the profile `DELETE_PENDING` (own commit), call Keycloak `deleteUser`, then delete the row. Keycloak fails → set `DELETE_FAILED` with the error and time, return `503`; the row and the account stay, the user can retry (`deleteUser` already treats `404` as done, so a retry is safe). Keycloak succeeds, row delete fails → the row stays `DELETE_PENDING` with no login behind it; log loudly with the `sub`, the same way `register()` logs an orphan. Tests: `deleteUser` throws → row `DELETE_FAILED`, account untouched; row delete fails → `DELETE_PENDING` and the log line; happy path → row gone. Rewrite the Javadoc ("the one we can roll back by simply failing" is false). `GET /users/me` on a `DELETE_PENDING`/`DELETE_FAILED` row: decide whether it reads as `404` or shows the status.

Rejected (original direction): `@Transactional` + `flush()` before the Keycloak call. It rolls the profile back on a Keycloak failure, but holds a DB connection and the row lock for the whole Keycloak call (#2's problem) and leaves no record that a delete failed (#30).

**Understand first:**
- Why `repository.delete()` alone commits: Spring Data repository methods are `@Transactional` themselves when no outer transaction exists.
- Why `flush()` matters: without it Hibernate defers the DELETE until commit, i.e. *after* the Keycloak call.
- Remaining window after the fix: Keycloak delete succeeds, then DB commit fails → orphan profile (harmless: no one can log in as that user). Compare with the opposite order.
- Note: already listed in `project-ideas/01-oauth2-oidc/requirements.md` → "Carried over from Phase 4".

---

## 2 — MAJOR — SB-JPA-02 — HTTP call inside a DB transaction

**Where:** `backend/order-service/src/main/java/com/fahim/orderservice/service/OrderService.java:21` (class-level `@Transactional`), `:32-33` (`create` → `productClient.fetchProduct`)

**What:** `create()` inherits the class-level `@Transactional`. `JpaTransactionManager` takes a DB connection from the pool when the transaction starts, then the method makes an HTTP call to Product Service while holding it.

**Why it matters:** Product Service timeouts are 2s connect + 3s read, so one slow call holds a connection up to ~5s. Hikari's default pool is 10. Ten concurrent orders against a slow Product Service exhaust the pool, and then *every* DB operation in order-service waits, including `GET /orders`, which doesn't touch Product Service at all. One slow dependency takes down unrelated endpoints.

**Fix direction (revised 2026-11-08, follows #30):** Remove the class-level `@Transactional` from `OrderService` (and the `readOnly` ones). In `create()`, save the order first as `PENDING` (own commit, connection returned), then call Product Service with no connection held, then update it: `CONFIRMED` with the price, or a failure status (`REJECTED` for unknown product or not enough stock, `FAILED` for Product Service unavailable) with the reason and time. The API answers stay `400`/`409`/`503`, but every failed attempt now leaves a row. Do it together with #22 (reply validation is one more failure reason). Needs a migration: new status values (the `CHECK` on `status` lists only `PENDING`, `CONFIRMED`, `CANCELLED`) and a `failure_reason` column. Decide how failed orders show in `GET /orders` (hidden, or listed with their status).

Rejected (original direction): splitting into an orchestrating method plus a `@Transactional` persist method on another bean. It fixes the connection hold but keeps transactions, and a failure still leaves nothing behind (#30).

**Understand first:**
- When Spring/Hibernate acquires the connection (transaction begin, not first query).
- Why a second bean is needed if you split methods: proxy-based `@Transactional` and `this.` calls (rule SB-JPA-01).
- How to see it: set `spring.datasource.hikari.maximum-pool-size=2`, slow Product Service down, fire 3 orders + 1 `GET /orders`.

---

## 3 — MAJOR — SB-JPA-05 — Unpaginated list endpoints

**Where:**
- `backend/product-service/src/main/java/com/fahim/productservice/service/ProductService.java:31-34` (`getAll` → `findAll()`), exposed by `ProductController#getAll` — **public, no auth**
- `backend/order-service/src/main/java/com/fahim/orderservice/service/OrderService.java:48-51` (`getAll` → `findByUserSub`) — per user, lower risk

**What:** Both return every matching row as a `List`.

**Why it matters:** Catalog grows → every anonymous `GET /products` loads all rows into memory and serialises them. Cheap to abuse (no token needed), and response time grows with table size. Changing the response shape later is a breaking API change, so it's cheaper to do now than after a frontend depends on it.

**Fix direction:** Accept `Pageable` (`?page=0&size=20&sort=name`), cap size (`spring.data.web.pageable.max-page-size=100`), return a page DTO (content + page metadata) — not `Page<Entity>` directly. Same for orders.

**Understand first:**
- Spring Data `Pageable` binding and `PagedModel` / `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` (Boot 3.3+ warns when serialising `PageImpl` directly).
- Offset vs keyset (cursor) pagination — when offset gets slow.
- Gateway and Postman collection need the new response shape too.

---

## 4 — MINOR — SB-CFG-08 — `open-in-view` not disabled

**Status:** Done 2026-11-14, commit `97b7dca` on `review-findings` (with #29). `spring.jpa.open-in-view=false` in order, product and user service; test `<Service>ApplicationTests.openInViewIsOff`. Task: `Interview-Preparation/tasks/osiv-off-hikari-config/`.

**Where:** `application.properties` of order-service, product-service, user-service (property absent)

**What:** `spring.jpa.open-in-view` defaults to `true`: the Hibernate session stays open for the whole web request, including JSON serialisation.

**Why it matters:**
- **Connections held per request.** The open session keeps its JDBC connection from the first query until the response is written, including serialisation and any remote call made after the first query. A slow request holds a pool connection for its whole length, the same pool-exhaustion failure as #2, on every endpoint. It isn't a transaction, but it holds the connection like one.
- Lazy associations can be loaded silently from the controller/serialiser, hiding N+1 queries until production. No lazy associations today, so no current bug there.
- Boot logs a warning about it at every startup.

**Fix direction:** `spring.jpa.open-in-view=false` in all three; run the tests. With #30 (no `@Transactional`), each repository call opens and closes its own session, so once this is off, no code path holds a connection longer than one query. Verify with #29's `leak-detection-threshold`.

**Understand first:** `OpenEntityManagerInViewInterceptor`, and what `LazyInitializationException` tells you once it's off.

---

## 5 — MINOR — GEN-OTHER — `show-sql=true` in base config

**Where:** `application.properties:9` in order-service, product-service, user-service

**What:** `spring.jpa.show-sql=true` prints every SQL statement with `System.out`, in every environment.

**Why it matters:** Bypasses the logging framework (no levels, no JSON format, no correlation IDs), can't be turned off at runtime, and floods logs under load.

**Fix direction:** Remove it. For local debugging use `logging.level.org.hibernate.SQL=debug` (+ `org.hibernate.orm.jdbc.bind=trace` for parameters) in a dev profile or local override.

---

## 6 — MINOR — SB-CFG-05 — Shared hardcoded DB superuser

**Where:** `application.properties:4-5` in order-service, product-service, user-service (`spring.datasource.username=postgres`, `password=postgres`)

**What:** All three services connect as the same Postgres superuser with credentials committed in the repo.

**Why it matters:** Schema-per-service is only a convention: order-service could read or modify `user_schema`. A bug or compromise in one service reaches all data. Also inconsistent with the project's own rule for Keycloak secrets (env vars, fail fast). Local-only today, so MINOR — but it's a direct input for the #05 threat model.

**Fix direction:** `${DB_USERNAME}` / `${DB_PASSWORD}` from `docker/.env`; in `docker/init/01-schemas.sql` create one role per service with grants only on its own schema; Flyway runs as that role.

**Understand first:** Postgres roles, `GRANT USAGE/CREATE ON SCHEMA`, `ALTER DEFAULT PRIVILEGES`; database-per-service vs schema-per-service.

---

## 7 — MINOR — SB-API-02 — Two error response shapes

**Where:** `GlobalExceptionHandler` in order-service, product-service, user-service (hand-built `Map` with `timestamp/status/error/message`)

**What:** Handled exceptions return the custom map. Anything not handled there — malformed JSON (`HttpMessageNotReadableException`), `/orders/abc` (`MethodArgumentTypeMismatchException`), 405, 415 — falls through to Spring's default error body, which has a different shape.

**Why it matters:** Clients (the coming frontend) need two parsers or break on the unhandled cases. Shape is defined three times by hand.

**Fix direction:** Extend `ResponseEntityExceptionHandler` and return `ProblemDetail` (RFC 9457) for the custom exceptions too; `spring.mvc.problemdetails.enabled=true`. Then one shape everywhere, standard `type/title/status/detail/instance` fields.

**Understand first:** RFC 9457 fields; how `ResponseEntityExceptionHandler` already maps the built-in MVC exceptions.

---

## 8 — MINOR — SB-JPA-06 — No optimistic locking

**Where:** `Order.java`, `Product.java`, `Profile.java` (no `@Version` field)

**What:** `PUT` loads the entity, changes it, saves it. Two concurrent PUTs on the same row both succeed; the later one silently overwrites the earlier.

**Why it matters:** Lost updates: two admins editing the same product's price/stock, or two tabs editing one order. No error, no trace. (The stock *race at order time* is a separate, documented gap — not this.)

**Fix direction:** `@Version private Long version;` + Flyway migration adding the column; map `ObjectOptimisticLockingFailureException` to 409. Optionally expose version as `ETag` and require `If-Match` on PUT.

**Understand first:** how Hibernate adds `WHERE version = ?` to the UPDATE; optimistic vs pessimistic locking; ETag/`If-Match` for HTTP.

---

## 9 — MINOR — SB-IO-01/03 — Dev-login HTTP client has no timeouts

**Where:** `backend/gateway/src/main/java/com/fahim/gateway/devlogin/KeycloakPasswordGrant.java:45-51`

**What:** Builds its own client with static `RestClient.builder()`: no connect/read timeout, and it skips Boot's auto-configured `RestClient.Builder` (no observation/metrics, no shared customisation).

**Why it matters:** Hung Keycloak → `POST /auth/dev-login` request thread hangs indefinitely. Dev-only, hence MINOR, but the pattern gets copied.

**Fix direction:** Inject `RestClient.Builder`, set a request factory with timeouts (same pattern as `ProductClientConfig`).

---

## 10 — MINOR — GEN-SMELL-10 — Dead repository method

**Where:** `backend/user-service/src/main/java/com/fahim/userservice/repository/ProfileRepository.java:8`

**What:** `existsByUsername(String)` is declared and never called (uniqueness is enforced by Keycloak's 409 and the DB `UNIQUE` constraint).

**Fix direction:** Delete it.

---

## 11 — SUGGESTION — SB-JPA-10 / GEN-SMELL-09 — Anemic entities, duplicated total calculation

**Where:** `Order.java` (public setters for every field, including `setId`, `setUserSub`); `OrderService.java:44` and `:64` both compute `unitPrice × quantity`

**What:** The service assembles the entity field by field; the rule "total = unit price × quantity" lives in two service methods instead of the entity.

**Why it matters:** Any new path that changes quantity must remember to recompute the total. `setUserSub` is public although the column is `updatable = false` and ownership must never change.

**Fix direction:** `Order.place(userSub, product, quantity)` factory + `changeQuantity(int)` that recomputes the total; drop setters that shouldn't exist (`setId`, `setUserSub`, `setTotalPrice`). Same idea for `Product`/`Profile` later.

**Understand first:** rich vs anemic domain model (Architecture #04 DDD — aggregates enforce invariants).

---

## 12 — SUGGESTION — SB-CFG-02 — Inconsistent config binding

**Where:** `order-service/.../config/ProductClientConfig.java:15-17`, `gateway/.../config/RouteConfig.java:38-40` (`@Value`) vs `user-service/.../config/KeycloakAdminProperties.java` (validated `@ConfigurationProperties` record)

**What:** Two binding styles for the same kind of config; the `@Value` ones also duplicate defaults (`:2000`) already set in properties.

**Fix direction:** `ProductServiceProperties` record (`baseUrl`, `connectTimeout`, `readTimeout` as `Duration`) with `@Validated` + `@NotNull`; same for Gateway upstream URLs.

---

## 13 — SUGGESTION — GEN-DIST-08 / SB-TEST-05 — Code copy-pasted across services

**Where:**
- `KeycloakRealmRoleConverter` — identical in order-service and product-service
- `GlobalExceptionHandler#body` — 3 copies
- `TestJwts` — 3 copies, 92 lines each
- `SecurityConfig` resource-server skeleton — 3 near-copies

**What:** Same code maintained in several places.

**Why it matters:** A fix in one copy (e.g. role parsing) has to be repeated by hand; copies drift.

**Fix direction:** Decision, not a code fix: either accept duplication (independent deployability, documented in an ADR) or extract a small `platform-security` library. For 3 services, accepting + ADR is reasonable. Revisit if a 4th resource server appears.

---

## 14 — SUGGESTION — GEN-DEP-05 — No dependency/CVE scan in CI

**Where:** `.github/workflows/build-service.yml`

**What:** CI checks formatting and runs tests, but has no Dependabot/Renovate and no dependency or image CVE scan.

**Fix direction:** Belongs to project #04 (DevSecOps pipeline): Dependabot for Maven + GitHub Actions, OWASP Dependency-Check or Trivy step, fail on high CVEs.

---

## 15 — MINOR — GEN-TEST-01 — No test for session id rotation on login (from threat model T2)

**Where:** `backend/gateway/src/test/java/com/fahim/gateway/` (`AuthFlowTest`, `DevLoginTest`)

**What:** Session fixation is prevented by Spring Security changing the session id on OAuth2 login, and by `DevLoginController.startSession` calling `request.changeSessionId()`. No test asserts that the id actually changes.

**Why it matters:** A config change (e.g. a custom `sessionManagement()` block) could switch the protection off silently. Threat T2 stays `Mitigated (v1)` instead of `Verified` until a test exists.

**Fix direction:** In `DevLoginTest`, create a session first, log in, assert the `JSESSIONID` after login differs from the one before. Same for the browser login callback if the test setup allows it.

**When done:** risk register T2 → `Verified`, evidence = the test name; changelog entry.

---

## 16 — MINOR — GEN-TEST-01 — No test for `PUT /products/{id}` without `admin` (from threat model T9)

**Where:** `backend/product-service/src/test/java/com/fahim/productservice/ProductControllerSecurityTest.java`

**What:** Create and delete without the `admin` role are tested (403). Update, the call that changes prices, is not.

**Why it matters:** `PUT` is the write an attacker wants (set a price to 0.01). The rule is the same `anyRequest().hasRole(ADMIN_ROLE)`, so it's covered today, but nothing proves it, and a future per-method matcher could miss it.

**Fix direction:** `updateProduct_withoutAdminRole_returns403` (and `_noToken_returns401`), mirroring the existing create/delete tests.

**When done:** risk register T9 → `Verified`, evidence = the test name; changelog entry.

## 17 — MINOR — GEN-TEST-01 — No test for a token from the wrong issuer (from ADR-0001 round 1 review)

**Where:** `AbstractIntegrationTest` and `TestJwts` in Order, Product and User Service; `TokenValidationTest` in each

**What:** Each service's `AbstractIntegrationTest` sets `spring.security.oauth2.resourceserver.jwt.issuer-uri` to `""` so Boot builds the public-key decoder, and `TestJwts` mints tokens with no `iss`. So no test runs the issuer check. `TokenValidationTest` proves the audience check only.

**Why it matters:** ADR-0001's claim "every service trusts exactly one issuer" rests on config nobody tests. If a later change drops or misspells `issuer-uri`, a token signed by a trusted key with any `iss` would pass, and nothing would fail.

**Fix direction:** First check whether Boot adds `JwtIssuerValidator` to the public-key decoder when `issuer-uri` is set alongside `public-key-location`. If yes: set a fake test issuer in `AbstractIntegrationTest`, make `TestJwts` put it in `iss`, add `request_withWrongIssuer_returns401` to each `TokenValidationTest`. If no: the services need their own `JwtDecoder` bean that combines the issuer and audience validators; test that bean the same way.

**When done:** ADR-0001 Verification → replace the "not covered by tests" line with the test names.

## 18 — MINOR — SB-SEC — User and Order Service fail open for new endpoints (from ADR-0003 round 2 and 3 reviews)

**Where:** `backend/user-service/.../config/SecurityConfig.java`, `backend/order-service/.../config/SecurityConfig.java`

**What:** User Service: after `POST /users/register`, the rule is `anyRequest().authenticated()`, so any token with the `user-service` audience reaches any endpoint. Order Service: `anyRequest()` needs `orders:write` + `user`, but `orders:read`/`orders:write` are realm default client scopes and `default-roles-secure-shop` includes `user`, so every logged-in user's token passes it.

**Why it matters:** Safe today because every existing endpoint has its own rule and acts on the caller's `sub`. A new endpoint without its own rule would be open to every logged-in user. Only Product fails closed (`anyRequest()` needs `admin`). Found in round 3: rounds 2 and the defense wrongly treated Order as fail-closed.

**Fix direction:** User: explicit matchers for `GET`, `PUT`, `DELETE /users/me` (`authenticated()`), then `anyRequest().denyAll()`. Order: explicit matchers for `POST /orders`, `GET /orders`, `GET`, `PUT`, `DELETE /orders/{id}` with the current scope + role rules, then `anyRequest().denyAll()`. Add `deleteMyProfile_noToken_returns401` to `UserControllerSecurityTest`, and in both services a test that an unmapped path with a valid token is refused. Removing the order scopes from the realm defaults is step 4 (ADR-0011, T17), not this finding.

**When done:** ADR-0003 → move items 1 and 5 of "Scheduled hardening" into Decision outcome / Verification; drop the "Until step 2, User and Order Service fail open" residual risk; correct the "Today only Product fails closed" sentence.

## 19 — MINOR — SB-SEC — Wildcard matchers on permissive rules (from ADR-0003 round 2 review)

**Where:** Product `SecurityConfig` (`GET /products/**` `permitAll`), Order `SecurityConfig` (`GET /orders/**` read + `user`), Gateway `SecurityConfig` (`GET /products/**` `permitAll`) and `RouteConfig` (`publicRoute("product-read", GET, "/products/**")`, no token relay)

**What:** A new `GET` under `/products` is public on day one. A new `GET` under `/orders` needs only read scope + `user`, so a `GET /orders/all` that forgets `sub` leaks every user's orders.

**Why it matters:** Fail-closed `anyRequest()` defaults never apply to routes a wildcard already matches.

**Fix direction:** Exact routes: `GET /products`, `GET /products/{id}`; exact Order `GET` routes. Change both deployables: if only Product Service is narrowed, a future admin `GET /products/...` still hits the Gateway's public route, gets no token, and always answers `401`.

**When done:** ADR-0003 → move item 2 of "Scheduled hardening" into Decision outcome and update the Gateway rules list; drop the "Wildcard matchers" residual risk once #20 also lands.

## 20 — MINOR — GEN-TEST-01 — No route-manifest or cross-user ownership test (from ADR-0003 round 2 review)

**Where:** test sources of Order, Product and User Service

**What:** Security tests are per endpoint. A new endpoint is covered only if someone writes its test.

**Why it matters:** ADR-0003 makes each service the only layer for its rules, so coverage must be mechanical, not remembered.

**Fix direction:**
- Route-manifest test: read all routes from `RequestMappingHandlerMapping`, compare with a checked-in manifest (route → rule). A missing route fails the build. Every non-public route answers `401` without a token; the public allowlist holds exact patterns only
- Cross-user test (Order, User): seed data for user A, call every owner-scoped route as user B, assert A's data never appears (`404` for by-id routes, absent from list responses)

**When done:** ADR-0003 → move items 3–4 into Verification with the test names; remove the "Not yet written" line. ADR-0004 → add the cross-user test to Verification (Scheduled hardening item 3). If a regression still gets through, ADR-0004's RLS migration path is next (needs `SET LOCAL` per transaction with pooled connections).

## 21 — MINOR — SB-SEC — Unscoped `JpaRepository` methods on owner-scoped repositories (from ADR-0004 round 2 review)

**Where:** `backend/order-service/.../repository/OrderRepository.java`, `backend/user-service/.../repository/ProfileRepository.java`

**What:** `OrderRepository extends JpaRepository<Order, Long>`, so `findById`, `findAll`, `findAllById`, `getReferenceById`, `existsById`, `deleteById` and `count` are callable without `userSub`. `ProfileRepository extends JpaRepository<Profile, String>`: `findById` takes the owner's `sub`, but `findAll` and `count` don't. Nothing checks that a new derived or `@Query` method takes the owner.

**Why it matters:** ADR-0004 puts ownership in the query. One autocomplete of an inherited method, or a new query without `userSub`, is a BOLA that compiles and passes the existing per-endpoint tests.

**Fix direction:**

1. **Narrow both repositories** to `Repository<T, ID>` and declare only what the service uses (Spring Data "selectively exposing CRUD methods": a declared method with a CRUD signature is backed by `SimpleJpaRepository`; an undeclared one doesn't exist, so `findById(id)` / `findAll()` stop compiling).

   ```java
   public interface OrderRepository extends Repository<Order, Long> {
       Order save(Order order);
       void delete(Order order);
       List<Order> findByUserSub(String userSub);
       Optional<Order> findByIdAndUserSub(Long id, String userSub);
   }

   public interface ProfileRepository extends Repository<Profile, String> {
       Profile save(Profile profile);
       void delete(Profile profile);
       /** The id is the Keycloak {@code sub}, so this lookup is already owner-scoped. */
       Optional<Profile> findById(String keycloakSub);
   }
   ```

   - `OrderService` and `ProfileService` need no change: they already call only these methods. `delete(entity)` after the scoped lookup stays; no `deleteByIdAndUserSub` needed
   - Drops `existsByUsername`, closing #10 too
   - Test cleanup: `OrderOwnershipTest` and `UserControllerSecurityTest` call `deleteAll()` in `@BeforeEach`. Replace with `@Sql(statements = "DELETE FROM orders")` / `"DELETE FROM profiles"` (or `JdbcTemplate`). Don't add `deleteAll` back to the interface. Mockito tests (`OrderServiceTest`, `ProfileServiceTest`) mock only kept methods, unaffected
   - Admin access later gets its own `AdminOrderRepository` (ADR-0004 migration path), never a wider `OrderRepository`

2. **ArchUnit integration** (stops new unscoped methods; narrowing only removes inherited ones)

   - Dependency in order-service and user-service `pom.xml`: `com.tngtech.archunit:archunit-junit5`, `test` scope. Pin the latest version when adding it
   - `OwnershipArchitectureTest` per service (`@AnalyzeClasses(packages = "com.fahim.orderservice")`, `@ArchTest` rules), runs in `mvn verify` with the other tests, so CI fails the build
   - Rule 1, allowlist: every method declared on `OrderRepository` except `save` and `delete` has a `String` parameter named `userSub` (needs `-parameters`, on by default with `spring-boot-starter-parent`; otherwise match by a `@Param("userSub")` or a naming convention). User Service: every method on `ProfileRepository` except `save`/`delete` takes the `sub` as its first `String` parameter
   - Rule 2: owner-scoped repositories (`OrderRepository`, `ProfileRepository`) must not be assignable to `CrudRepository` / `JpaRepository`, so nobody widens them back
   - Rule 3: only classes in `..service..` may depend on `..repository..` (controllers can't bypass the scoped service methods)
   - Limit: checks signatures, not whether the JPQL actually uses `userSub`. The cross-user test (#20) covers that

3. **Not chosen:** custom base repository that injects the owner (hidden request-scoped coupling in the data layer); Hibernate `@Filter` (must be enabled per session, no native SQL); Postgres RLS (needs per-service roles, #6, first; stays ADR-0004's migration path).

**When done:** ADR-0004 → move items 1–2 of "Scheduled hardening" into Decision outcome / Verification with the test name; reword the "Unscoped repository methods are callable today" residual risk and the Scope paragraph; drop the "until step 2" clause from the one-minute answer.

## 22 — MINOR — SB-SEC — Order Service trusts Product Service's reply unchecked (from ADR-0009 revision, T38)

**Where:** `backend/order-service/.../service/OrderService.java` (`create`), a new Flyway migration in `backend/order-service/src/main/resources/db/migration/`

**What:** `create` takes `id`, `price` and `stock` from `ProductClient.fetchProduct` as-is. The stored `product_id` is the reply's `id`, not the requested one. Nothing checks that `price` is present and positive or that `stock` is present and not negative; a missing field is a `500`. `orders` has no `CHECK` on `unit_price` or `total_price`.

**Why it matters:** A forged or broken reply sets what the user pays, gets past the stock check and can store a zero or negative price. Validation doesn't make a forged plausible price detectable (that needs TLS, accepted in ADR-0014), but it bounds the damage and turns a bad upstream reply into a clean `503`.

**Fix direction:** In `create`, reject the reply with `ProductServiceUnavailableException` (→ `503`) when `id` differs from the requested product, `price` is null or not positive, or `stock` is null or negative. Migration: `ALTER TABLE orders ADD CONSTRAINT ... CHECK (unit_price > 0 AND total_price > 0)`. Tests in `OrderProductLookupTest`: mismatched `id`, zero price, negative price, missing field, each `503` with nothing saved.

**When done:** ADR-0009 Verification → add the test names; risk register T38 → drop "Known gap, unscheduled"; `stride/data-flows.md` T38 → move reply validation from Gap to Control today; changelog entry.

## 23 — MINOR — GEN-TEST-01 — Dev-login guard has no ArchUnit rule and no startup-failure test (from ADR-0010 review)

**Where:** `backend/gateway/src/test/java/com/fahim/gateway/devlogin/`, `backend/gateway/pom.xml`

**What:** Dev login is safe because every bean in `devlogin` carries `@ConditionalOnDevLogin`, and because `KeycloakPasswordGrant` refuses to start without the `keycloak-test` registration. Neither is tested: a new unguarded bean would compile and pass, and the startup failure is code only (ADR-0010 Verification says so).

**Why it matters:** A regression in the guard puts a password endpoint into every Gateway start, not only `dev`.

**Fix direction:**
- ArchUnit (`archunit-junit5`, test scope; shared with #21), test class in the `devlogin` package because `ConditionalOnDevLogin` is package-private: every `@Component`/`@RestController` in `..devlogin..` is annotated with `ConditionalOnDevLogin`; no class outside `..devlogin..` depends on `KeycloakPasswordGrant`. `SecurityConfig`'s `ObjectProvider<DevLoginLogoutHandler>` is the one allowed outside dependency
- `ApplicationContextRunner` test: `secure-shop.dev-login.enabled=true` without the `keycloak-test` registration fails with the "Run with the dev profile" message
- Optional: move `application-dev.properties` out of `src/main/resources` (load it with `spring.config.additional-location` locally), so the deployed jar can't activate dev login by profile name

This is regression protection, not the T7 fix: the step 4 startup guard (refuse `dev-login.enabled=true` outside a local environment) stays the fix.

**When done:** ADR-0010 Verification → replace "No test covers the startup failure" with the test names.

## 24 — MINOR — GEN-TEST-01 — Realm facts the ADRs rely on are checked by inspection only (from ADR-0011 rounds 1–2 and ADR-0006 round 3 reviews)

**Where:** `docker/keycloak/secure-shop-realm.json`; a new unit test (any service's test sources, or a small test module)

**What:** ADR-0011 and ADR-0006 rest on realm settings that only a human checks: `secure-shop-audience` is a default scope on `secure-shop-gateway` and `secure-shop-test-client` only, on no other client (`admin-cli` included), and not in `defaultDefaultClientScopes`; `default-roles-secure-shop` includes `user`; only `adminuser` has `admin`; `accessTokenLifespan` is 300; `secure-shop-gateway` has direct access grants off and `secure-shop-test-client` has standard flow off.

**Why it matters:** One realm edit, such as making the audience scope a realm default, silently gives every client, `admin-cli` included, tokens every service accepts. Nothing fails.

**Fix direction:** A JUnit test that parses `secure-shop-realm.json` with Jackson and asserts each fact above, one assertion per ADR claim, with the ADR number in the message. Runs in `mvn verify`, so CI fails on a breaking realm edit.

**When done:** ADR-0011 and ADR-0006 Verification → replace "by inspection, no test" with the test name.

## 25 — MINOR — GEN-TEST-01 — No context-level startup test for a missing secret (from ADR-0007 round 2 review)

**Where:** Gateway and User Service test sources

**What:** ADR-0007's fail-fast checks are tested at the properties-class level (`KeycloakAdminPropertiesTest` and the Gateway equivalent), not by starting a context without the secret.

**Why it matters:** A wiring change (the properties class no longer bound or validated at startup) would leave the unit test green while the app starts without its secret and fails at the first login.

**Fix direction:** `ApplicationContextRunner` (or `@SpringBootTest` expecting a startup failure) without `KEYCLOAK_GATEWAY_CLIENT_SECRET` / the admin-client secret: the context fails with the hint ADR-0007 promises. One per deployable.

**When done:** ADR-0007 Verification → add the test names; reword "the same check, the same message, the same tests" to "same condition, same hint, parallel tests" (round 2 note).

## 26 — SUGGESTION — SB-SEC — `admin-cli` keeps direct access grants in the app realm (from ADR-0006 round 3 review)

**Where:** `docker/keycloak/secure-shop-realm.json`, client `admin-cli`

**What:** `admin-cli` is public with `directAccessGrantsEnabled: true`, so anyone with a user's password can get a token for that user straight from Keycloak, no secret needed. Nothing in the project uses it. Its tokens are rejected by every service today only because it lacks `secure-shop-audience` (#24 makes that checked).

**Why it matters:** It's a second password-grant path next to `secure-shop-test-client` (T26), and the reason it's harmless is incidental.

**Fix direction:** Set `directAccessGrantsEnabled: false` on `admin-cli` in the realm export, with step 4's realm hardening (T25, T26). Confirm nothing in `docs/` or Postman uses it first.

**When done:** risk register T26 note; ADR-0006 Verification line.

## 27 — SUGGESTION — GEN-OTHER — "Test data only" has no visible notice (from ADR-0013 round 1 review, F6)

**Where:** `README.md`, `docs/development.md`

**What:** ADR-0012, 0013 and 0014 all accept risks because the stack holds only test data on one laptop. Their revisit triggers ("any real personal data", "any deployment") are events nothing detects; someone who clones the repo doesn't see the condition.

**Fix direction:** One notice near the top of `README.md` and in `docs/development.md`: local R&D only, test data only, no TLS, unencrypted data folders, links to ADR-0012/0013/0014. Point to step 4, which binds ports to localhost.

**When done:** ADR-0013 → mention the notice under Verification.

## 28 — MINOR — GEN-OTHER — ADR-0014 accepted with its round 1 REVISE items open (approver override, 2026-11-08)

**Where:** `docs/adr/0014-accept-no-tls-local-rnd.md`

**What:** Round 1 (2026-11-06, 8.5/10) said REVISE: the ADR is missing (1) how "local only" is enforced, (2) a CI trigger that would catch the stack leaving one machine, and (3) a note on what step 3's containers change. Accepted with an override. The 2026-11-06 report was not saved in `.review/`, so re-derive the details from the review record or a fresh review when fixing.

**Why it matters:** The acceptance of T1, T37, T38 (reply integrity) and T39 rests on "local only", which nothing enforces or detects today.

**Fix direction:** Likely shape: (1) enforcement = step 4 binding published ports to `127.0.0.1` (T40 is step 3); (2) a CI check that fails when a non-dev profile names a non-localhost issuer or base URL over plain HTTP, or similar; (3) step 3 note: container-to-container traffic on the compose network is still plain HTTP, and who can sniff it. Because ADR-0014 is `Accepted`, items that change the decision go in a new ADR that supersedes it; Verification and trigger lines can be added in place.

**When done:** mark it `Done`; ADR-0014 review record → re-review row.

## 29 — MINOR — SB-CFG — HikariCP not configured: defaults everywhere, 30 s connection wait (added 2026-11-08)

**Status:** Done 2026-11-14, commit `97b7dca` on `review-findings` (with #4). Each service: `pool-name=<service>-pool`, `maximum-pool-size=5`, `minimum-idle=2`, `connection-timeout=3000`, `leak-detection-threshold=2000`; a pool timeout answers `503` + `Retry-After: 1` (`PoolTimeoutTest.exhaustedPoolAnswers503`), tests `<Service>ApplicationTests.poolIsConfigured`. Values and reasons: `docs/development.md` → Database connections. Follow-up: #31 (an outage also reads as "busy").

**Where:** `application.properties` of order-service, product-service, user-service (no `spring.datasource.hikari.*` property anywhere)

**What:** Every service runs Hikari's defaults: `maximum-pool-size` 10, `minimum-idle` = pool size, `connection-timeout` 30 s, `idle-timeout` 10 min, `max-lifetime` 30 min, `leak-detection-threshold` off, pool name `HikariPool-1` in all three.

**Why it matters:**
- A request that can't get a connection waits up to 30 s before failing. With 2 s + 3 s timeouts on the Product call, a 30 s pool wait is the slowest part of any outage, and it ties up a web thread the whole time
- Nothing reports a connection held too long, so #2- and #4-type holds stay invisible
- Logs and metrics can't tell the three pools apart

**Fix direction:** Per service, in `application.properties`:
- `spring.datasource.hikari.pool-name=<service>-pool`
- `maximum-pool-size`: small and explicit (start at 5; 3 services × 5 stays far under Postgres' default `max_connections` of 100, and step 2's per-service roles can add a `CONNECTION LIMIT` to match)
- `minimum-idle` lower than the max (e.g. 2) for a laptop
- `connection-timeout=3000` (fail fast, answer `503`)
- `leak-detection-threshold=2000` in dev (logs a stack trace for any connection held over 2 s)
- `max-lifetime` below any network or Postgres idle cut-off (keep the 30 min default unless one exists)
- Map `SQLTransientConnectionException` (pool timeout) to `503` in each service's error handler, not `500`

**Understand first:** why a smaller pool is often faster (HikariCP "About Pool Sizing"); what the leak detection log shows and why it isn't a leak by itself; how `connection-timeout` interacts with the web server's thread pool.

**When done:** note the values in `docs/development.md`; #4 and #30 use the leak detection log as their check.

## 30 — MAJOR — SB-JPA — Decision: no `@Transactional`; failures recorded as state, not rolled back (added 2026-11-08)

**Where:** `OrderService` (class-level `@Transactional`, two `readOnly`), `ProductService` (class-level, two `readOnly`), `ProfileService` (`getBySub` `readOnly`, `update`); every future service class

**What (the decision, by Fahim):** Services don't use `@Transactional`. Each repository call runs in its own short transaction and returns its connection at once. Instead of rolling back, a failed operation leaves a record: the row carries a status (e.g. `PENDING` → `CONFIRMED` / `FAILED`) with the reason and time, and the failure is logged with enough context (ids, the failing dependency) to follow it. Error handling maps each failure to a clear response.

**Why:**
- `@Transactional` holds a DB connection from the start of the method to the end, including remote calls (#2) and slow code
- A rollback removes every trace of a failed attempt: nothing shows that an order was tried, what failed, or how often. A status row keeps that history and can be queried
- It matches the cross-system reality: Keycloak and Product Service calls were never part of a DB transaction anyway (`ProfileService.register` already says so)

**Trade-offs to settle in the ADR:**
- **Atomicity of multi-row writes is gone.** Today every write is one row, so nothing breaks. A future write that changes two rows in one service (e.g. an order and its lines) would commit them separately; a crash between them leaves half the change. The ADR needs either an explicit exception ("a single local multi-row write may use a transaction with no remote call inside it") or a rule that every partial state has a status that shows it is incomplete
- **Read consistency:** two reads without a transaction can see different snapshots. Harmless today (single-row reads)
- **Lost-update protection** still needs optimistic locking (#8); a transaction never provided it
- **Data model work:** status and reason columns per aggregate (`orders` needs new status values and `failure_reason`; `profiles` needs a status for pending or failed deletes), and a decision on whether failed rows show in list endpoints
- **Retention:** failure rows grow; decide how long to keep them

**Fix direction:**
1. ADR first (replaces the "#2 rule: no remote calls inside a DB transaction" ADR planned for step 2): the rule, the exception for local multi-row writes or why there is none, the status model, the logging fields
2. Remove `@Transactional` from the three services; the readOnly ones go too
3. Apply the status pattern in #1 (profile delete) and #2 + #22 (order create)
4. An ArchUnit rule (shared with #21, #23): no class in `..service..` or `..controller..` is annotated with `@Transactional` and no method is, outside the ADR's exception
5. Verify with #29's `leak-detection-threshold` and #4 off: no connection held over 2 s in any test. Both are in place since `97b7dca` (2026-11-14): every service runs `open-in-view=false` and `leak-detection-threshold=2000`, tests included

**Understand first:** how Spring Data's repository methods run in their own transaction when there is no outer one (`SimpleJpaRepository` is `@Transactional`); why dirty checking needs an explicit `save()` once there's no surrounding transaction; the Saga pattern's "compensate and record" as the general form of this decision.

**Breakdown (2026-11-08):** one task per method, tracked in `Interview-Preparation/tasks/` (`/task-workflow <slug>`): `tx-00-adr-no-transactional` first (the ADR, and class-level `@Transactional` moved onto each method so the parts stay independent), then one task per method: Order `create` (with #2, #22), `getAll`, `getById`, `update`, `delete`; Product `create`, `getAll`, `getById`, `update`, `delete`; Profile `register`, `getBySub`, `update`, `delete` (with #1); `tx-99-archunit-no-transactional` last.

**When done:** ADR in `docs/adr/`; mark it `Done`; #1 and #2 reference the ADR.

---

## 31 — MINOR — SB-API — A DB outage answers 503 "Database busy" and logs at WARN; 500 branch untested (added 2026-11-14)

**Where:** `GlobalExceptionHandler#handleNoTransaction` and `isPoolTimeout` in order-, product- and user-service (added by #29, commit `97b7dca`)

**What:** Hikari throws `SQLTransientConnectionException` for every `getConnection()` that times out. That includes a pool that can't open connections because Postgres is down; then the last connection error is the cause. So an outage after startup gets the same answer as a busy pool: `503` "Database busy, try again", logged at WARN. The handler's Javadoc says an outage "stays a 500", which is wrong. Seen on 2026-11-14 with product-service running and the Postgres container stopped: `GET /products` → `503` after 3.08 s, log `No database connection free within the pool timeout: Connection refused: getsockopt`. Also, no test covers the handler's `500` branch.

**Why it matters:** `503` is a fair answer for an outage. But the message tells the client it's load, and WARN hides an outage among routine saturation warnings. An alert on ERROR would miss it.

**Fix direction:** keep `503`, but split the cases. If the `SQLTransientConnectionException` has a cause (Hikari couldn't connect), log at ERROR and answer "Database unavailable". With no cause, keep "Database busy" at WARN. Fix the Javadoc. Add a test for the `500` branch, e.g. a `CannotCreateTransactionException` whose cause isn't a pool timeout, thrown from a mocked repository. Small; it can ride along with #7 (one error shape) or go before #30.

**Also noted in the same review (not separate findings):**
- **One WARN per rejected request** under saturation. Hikari's pool metrics (`hikaricp.connections.timeout`) are the better signal: step 6 observability.
- **`Retry-After: 1` is shorter than the 3 s pool wait.** Clients that honour it would retry in sync while the pool is still full.
- **Security review, INFO:** the smaller pool (10 → 5) halves the number of concurrent anonymous `GET /products` calls needed to fill it. The 3 s timeout bounds the effect; #3 (pagination) and rate limiting (step 6) are the real fixes.

---

## 32 — MINOR — GEN-DEP — Testcontainers version override in 3 POMs; remove when the Boot parent is >= 3.5.16 (added 2026-11-14)

**Where:** `backend/order-service/pom.xml`, `backend/product-service/pom.xml`, `backend/user-service/pom.xml`: `<testcontainers.version>1.21.4</testcontainers.version>` in `<properties>`, with a comment pointing here (commit `0cbbb6b`)

**What:** Spring Boot 3.5.5 manages Testcontainers 1.21.3. Its Docker client asks for API 1.32, and the `ubuntu-latest` runner's Docker now requires 1.40, so every integration test on PR #8 failed to start Postgres (`client version 1.32 is too old. Minimum supported API version is 1.40`). The three POMs override the version to 1.21.4 ("makes version 1.21.x work with recent Docker Engine changes"; it is managed by Boot 3.5.16). PR #8 went green with it (2026-11-14, `0cbbb6b` and `82e0c92`).

**Why it matters:** the override keeps pinning 1.21.4 after a Boot bump, silently ignoring the newer Testcontainers that later Boot versions bring. It is a test-scope pin that is easy to forget.

**Fix direction:** when the parent moves to Boot ≥ 3.5.16, delete the property and its comment in all three POMs. Check that `./mvnw dependency:tree -Dincludes=org.testcontainers` shows the Boot-managed version (≥ 1.21.4) and that `verify` passes on CI.

**When done:** mark it `Done`. Task: `Interview-Preparation/tasks/ci-testcontainers-docker-api/`.
