# Best-Practice Findings

One file per finding, from `/best-practices-review` (moved from the single `best-practices-findings.md` on 2026-11-28). Open findings sit in this folder; a finding moves to [`done/`](done/) when it is fixed, keeping its number and file name, and its row below stays with the link updated. Findings are never deleted.

**Rules**
- **File:** `NNN-<slug>.md`, numbered in order, never reused. `#30` in other docs means `030-*.md`.
- **Shape:** a header table (Status, Severity, Rule, Service), then Where, What, Why it matters, Fix direction, Understand first. Aim for ≤ ~40 lines; longer means it belongs in an ADR or a task.
- **Progress** is recorded in the task (`Interview-Preparation/tasks/<slug>/`), not here: a finding gets a one-line pointer.
- **Fixed:** set Status to `Done <date> (<commit>)` in the file and in this index, add a `**Status:** Done ...` line naming the commit, tests and task, then `git mv` the file to `done/` and update the link.
- **New finding:** next number, new file, new row here.

- Source: `/best-practices-review audit` on `main` @ `3afe73f`, 2026-10-10
- Scheduled: **step 2** of the build sequence (after #05 threat model, before #04). Includes per-service Postgres users + schema-only privileges (#6). #14 is handled by #04 Phase 3, not here. #05 may add more items assigned to step 2
- Plan: `Interview-Preparation/project-ideas/README.md` → Build Sequence
- ADRs expected when fixing **#30 (replaces #2's), #3, #6, #7, #13** (list + convention: `Interview-Preparation/project-ideas/README.md` → ADR Convention). Write the ADR in `docs/adr/` as part of that finding's fix
- Workflow: pick one finding → understand it → fix → tests pass → mark it `Done` (Rules above) and move it to `done/`
- **Threat model links:** #1 → T21, #2 → T18, #3 → T12, #6 → T31 + T32, #15 → T2, #16 → T9. When one of these is fixed, also update `docs/threat-model/` (risk register status, PR, residual DREAD; Control/Gap in the STRIDE file; changelog)
- Severity: BLOCKER > MAJOR > MINOR > SUGGESTION
- Not flagged on purpose (documented in `docs/architecture.md` → "Next plans"): stock race/decrement, client-settable order status, Gateway upstream timeouts, service-to-service auth, registration rate limit

| # | Severity | Rule | Title | Service | Status |
|---|---|---|---|---|---|
| 1 | MAJOR | GEN-DES-05 | [Profile delete can't roll back](001-profile-delete-cant-roll-back.md) | user-service | Open |
| 2 | MAJOR | SB-JPA-02 | [HTTP call inside DB transaction](002-http-call-inside-db-transaction.md) | order-service | Open |
| 3 | MAJOR | SB-JPA-05 | [Unpaginated list endpoints](003-unpaginated-list-endpoints.md) | product, order | Open |
| 4 | MINOR | SB-CFG-08 | [`open-in-view` not disabled](done/004-open-in-view-not-disabled.md) | order, product, user | Done 2026-11-14 (`97b7dca`) |
| 5 | MINOR | GEN-OTHER | [`show-sql=true` in base config](005-show-sql-true-in-base-config.md) | order, product, user | Open |
| 6 | MINOR | SB-CFG-05 | [Shared hardcoded DB superuser](006-shared-hardcoded-db-superuser.md) | order, product, user | Open |
| 7 | MINOR | SB-API-02 | [Two error response shapes](007-two-error-response-shapes.md) | order, product, user | Open |
| 8 | MINOR | SB-JPA-06 | [No optimistic locking](008-no-optimistic-locking.md) | order, product, user | Open |
| 9 | MINOR | SB-IO-01/03 | [Dev-login HTTP client: no timeouts](009-dev-login-http-client-no-timeouts.md) | gateway | Open |
| 10 | MINOR | GEN-SMELL-10 | [Dead repository method](010-dead-repository-method.md) | user-service | Open |
| 11 | SUGGESTION | SB-JPA-10 | [Anemic entities, duplicated total calc](011-anemic-entities-duplicated-total-calc.md) | order-service | Open |
| 12 | SUGGESTION | SB-CFG-02 | [Inconsistent config binding](012-inconsistent-config-binding.md) | order, gateway | Open |
| 13 | SUGGESTION | GEN-DIST-08 | [Code copy-pasted across services](013-code-copy-pasted-across-services.md) | all | Open |
| 14 | SUGGESTION | GEN-DEP-05 | [No dependency/CVE scan in CI](014-no-dependency-cve-scan-in-ci.md) | CI | Open |
| 15 | MINOR | GEN-TEST-01 | [No test for session id rotation on login (threat T2)](015-no-test-session-id-rotation-on.md) | gateway | Open |
| 16 | MINOR | GEN-TEST-01 | [No test for `PUT /products/{id}` without `admin` (threat T9)](016-no-test-put-products-id-without.md) | product-service | Open |
| 17 | MINOR | GEN-TEST-01 | [No test for a token from the wrong issuer (ADR-0001 round 1)](017-no-test-token-from-wrong-issuer.md) | order, product, user | Open |
| 18 | MINOR | SB-SEC | [User and Order Service `anyRequest()` defaults fail open for new endpoints (ADR-0003)](018-user-order-service-anyrequest-defaults-fail.md) | user, order | Open |
| 19 | MINOR | SB-SEC | [Wildcard matchers on permissive rules (ADR-0003)](019-wildcard-matchers-on-permissive-rules-adr.md) | product, order, gateway | Open |
| 20 | MINOR | GEN-TEST-01 | [No route-manifest or cross-user ownership test (ADR-0003)](020-no-route-manifest-or-cross-user.md) | order, product, user | Open |
| 21 | MINOR | SB-SEC | [Unscoped `JpaRepository` methods on owner-scoped repositories (ADR-0004)](021-unscoped-jparepository-methods-on-owner-scoped.md) | order, user | Open |
| 22 | MINOR | SB-SEC | [Order Service trusts Product Service's reply unchecked (ADR-0009, T38)](022-order-service-trusts-product-services-reply.md) | order-service | Open |
| 23 | MINOR | GEN-TEST-01 | [Dev-login guard has no ArchUnit rule and no startup-failure test (ADR-0010)](023-dev-login-guard-has-no-archunit.md) | gateway | Open |
| 24 | MINOR | GEN-TEST-01 | [Realm facts the ADRs rely on are checked by inspection only (ADR-0011, ADR-0006)](024-realm-facts-adrs-rely-on-are.md) | realm | Open |
| 25 | MINOR | GEN-TEST-01 | [No context-level startup test for a missing secret (ADR-0007)](025-no-context-level-startup-test-missing.md) | gateway, user | Open |
| 26 | SUGGESTION | SB-SEC | [`admin-cli` keeps direct access grants in the app realm (ADR-0006)](026-admin-cli-keeps-direct-access-grants.md) | realm | Open |
| 27 | SUGGESTION | GEN-OTHER | ["Test data only" has no visible notice (ADR-0013)](027-test-data-only-has-no-visible.md) | docs | Open |
| 28 | MINOR | GEN-OTHER | [ADR-0014 accepted with its round 1 REVISE items open](028-adr-0014-accepted-its-round-1.md) | docs | Open |
| 29 | MINOR | SB-CFG | [HikariCP not configured: defaults everywhere, 30 s connection wait](done/029-hikaricp-not-configured-defaults-everywhere-30.md) | order, product, user | Done 2026-11-14 (`97b7dca`) |
| 30 | MAJOR | SB-JPA | [Decision: no `@Transactional`; failures recorded as state, not rolled back](030-decision-no-transactional-failures-recorded-as.md) | order, product, user | Open |
| 31 | MINOR | SB-API | [A DB outage answers 503 "Database busy" and logs at WARN; 500 branch untested](done/031-db-outage-answers-503-database-busy.md) | order, product, user | Done 2026-11-14 (`82e0c92`) |
| 32 | MINOR | GEN-DEP | [Testcontainers version override in 3 POMs; remove when the Boot parent is >= 3.5.16](032-testcontainers-version-override-in-3-poms.md) | order, product, user | Open |
| 33 | MINOR | GEN-OBS | [No correlation ID in error responses: a generic client error can't be matched to its log line](033-no-correlation-id-in-error-responses.md) | order, product, user, gateway | Open |
| 34 | MINOR | SB-API | [`POST /orders` has no idempotency key: a client retry after a lost response places a second order](034-post-orders-has-no-idempotency-key.md) | order, gateway | Open |

Suggested order (changed 2026-11-14: #4 + #29 moved first, so the #30 ADR has its precondition and its check in place): ~~4 + 29 (config)~~ done → 30 (ADR first: it sets the direction for 1, 2, 22) → 1 → 2 + 22 (both in `OrderService.create` and the Product call), 3 → 5, 6 → 7, 8 → 9, 10, 12 → 11, 13 → tests 15, 16, 17, 23, 25 → realm 24 + 26 → 18, 19, 20, 21 → docs 27, 28 (design calls; 14 belongs to project #04).
