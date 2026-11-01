# Best-Practices Review — Open Findings

Review working file, tracked in git (since 2026-11-01) so it moves between devices. Delete a finding's section when it is fixed.

- Source: `/best-practices-review audit` on `main` @ `3afe73f`, 2026-10-10
- Scheduled: **step 2** of the build sequence (after #05 threat model, before #04). Includes per-service Postgres users + schema-only privileges (#6). #14 is handled by #04 Phase 3, not here. #05 may add more items assigned to step 2
- Plan: `Interview-Preparation/project-ideas/README.md` → Build Sequence
- ADRs expected when fixing **#2, #3, #6, #7, #13** (list + convention: `Interview-Preparation/project-ideas/README.md` → ADR Convention). Write the ADR in `docs/adr/` as part of that finding's fix
- Workflow: pick one finding → understand it → fix → tests pass → delete its section here
- **Threat model links:** #1 → T21, #2 → T18, #3 → T12, #6 → T31 + T32, #15 → T2, #16 → T9. When one of these is fixed, also update `docs/threat-model/` (risk register status, PR, residual DREAD; Control/Gap in the STRIDE file; changelog)
- Severity: BLOCKER > MAJOR > MINOR > SUGGESTION
- Not flagged on purpose (documented in `docs/architecture.md` → "Next plans"): stock race/decrement, client-settable order status, Gateway upstream timeouts, service-to-service auth, registration rate limit

| # | Severity | Rule | Title | Service |
|---|---|---|---|---|
| 1 | MAJOR | GEN-DES-05 | Profile delete can't roll back | user-service |
| 2 | MAJOR | SB-JPA-02 | HTTP call inside DB transaction | order-service |
| 3 | MAJOR | SB-JPA-05 | Unpaginated list endpoints | product, order |
| 4 | MINOR | SB-CFG-08 | `open-in-view` not disabled | order, product, user |
| 5 | MINOR | GEN-OTHER | `show-sql=true` in base config | order, product, user |
| 6 | MINOR | SB-CFG-05 | Shared hardcoded DB superuser | order, product, user |
| 7 | MINOR | SB-API-02 | Two error response shapes | order, product, user |
| 8 | MINOR | SB-JPA-06 | No optimistic locking | order, product, user |
| 9 | MINOR | SB-IO-01/03 | Dev-login HTTP client: no timeouts | gateway |
| 10 | MINOR | GEN-SMELL-10 | Dead repository method | user-service |
| 11 | SUGGESTION | SB-JPA-10 | Anemic entities, duplicated total calc | order-service |
| 12 | SUGGESTION | SB-CFG-02 | Inconsistent config binding | order, gateway |
| 13 | SUGGESTION | GEN-DIST-08 | Code copy-pasted across services | all |
| 14 | SUGGESTION | GEN-DEP-05 | No dependency/CVE scan in CI | CI |
| 15 | MINOR | GEN-TEST-01 | No test for session id rotation on login (threat T2) | gateway |
| 16 | MINOR | GEN-TEST-01 | No test for `PUT /products/{id}` without `admin` (threat T9) | product-service |
| 17 | MINOR | GEN-TEST-01 | No test for a token from the wrong issuer (ADR-0001 round 1) | order, product, user |
| 18 | MINOR | SB-SEC | User Service `anyRequest().authenticated()` fails open for new endpoints (ADR-0003) | user-service |
| 19 | MINOR | SB-SEC | Wildcard matchers on permissive rules (ADR-0003) | product, order, gateway |
| 20 | MINOR | GEN-TEST-01 | No route-manifest or cross-user ownership test (ADR-0003) | order, product, user |

Suggested order: 1 → 2, 3 → 4, 5, 6 → 7, 8 → 9, 10, 12 → 11, 13, 14 (design calls; 14 belongs to project #04).

---

## 1 — MAJOR — GEN-DES-05 — Profile delete can't roll back

**Where:** `backend/user-service/src/main/java/com/fahim/userservice/service/ProfileService.java:92-100` (`ProfileService#delete`)

**What:** `delete()` has no `@Transactional` (class has none either). `profileRepository.delete(profile)` runs in the repository's own transaction and commits immediately. Then `keycloakAdminClient.deleteUser(...)` is called. The Javadoc says the local row goes first "because it is the one we can roll back by simply failing" — but nothing rolls it back.

**Why it matters:** Keycloak down or returns 5xx → profile row is gone, Keycloak user still exists. User can still log in, but `GET /users/me` returns 404 forever and re-registering fails with 409 (username taken in Keycloak). Existing test `delete_removesProfileThenKeycloakUser` only covers the happy path with mocks, so it can't catch this.

**Fix direction:** `@Transactional` on `delete()` + `profileRepository.flush()` before the Keycloak call (so the DELETE SQL actually runs and can fail first). Keycloak failure → exception → transaction rolls back → profile restored. Add an integration test where `deleteUser` throws and assert the profile still exists.

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

**Fix direction:** Do the remote call outside any transaction, open the transaction only for the write. Options: remove `@Transactional` from `create()` and rely on `save()`'s own transaction (single write, so that's enough), or split into an orchestrating method (no tx) + a `@Transactional` persist method on another bean (self-invocation would bypass the proxy).

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

**Where:** `application.properties` of order-service, product-service, user-service (property absent)

**What:** `spring.jpa.open-in-view` defaults to `true`: the Hibernate session stays open for the whole web request, including JSON serialisation.

**Why it matters:** Lazy associations can be loaded silently from the controller/serialiser, outside any transaction, hiding N+1 queries until production. Boot logs a warning about it at every startup. No lazy associations today, so no current bug — this prevents the first one.

**Fix direction:** `spring.jpa.open-in-view=false` in all three; run the tests.

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

## 18 — MINOR — SB-SEC — User Service fails open for new endpoints (from ADR-0003 round 2 review)

**Where:** `backend/user-service/src/main/java/com/fahim/userservice/config/SecurityConfig.java`

**What:** After `POST /users/register`, the rule is `anyRequest().authenticated()`. Any token with the `user-service` audience reaches any endpoint.

**Why it matters:** Safe today because every protected endpoint is `/users/me` and acts on the caller's `sub`. A new endpoint that is not owner-scoped would be open to every logged-in user. Order and Product already fail closed; User Service is the exception.

**Fix direction:** Explicit matchers for `GET`, `PUT`, `DELETE /users/me` (`authenticated()`), then `anyRequest().denyAll()`. Add `deleteMyProfile_noToken_returns401` to `UserControllerSecurityTest`, and a test that an unmapped path with a valid token is refused.

**When done:** ADR-0003 → move items 1 and 5 of "Scheduled hardening" into Decision outcome / Verification; drop the "Until step 2, User Service fails open" residual risk.

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

**When done:** ADR-0003 → move items 3–4 into Verification with the test names; remove the "Not yet written" line. If a regression still gets through, ADR-0004's RLS migration path is next (needs `SET LOCAL` per transaction with pooled connections).
