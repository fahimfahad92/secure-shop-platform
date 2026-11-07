# ADR review — round 2 — ADR-0007 — 2026-11-07

Reviewer: `architecture-kata-reviewer` (ADR mode). Pass mark 7, target 8 (from `docs/adr/README.md`).

> **Independence:** fresh session (started after `/clear`); this session did not draft or edit the ADR. The score can gate acceptance.

Target: `docs/adr/0007-fail-at-startup-on-a-missing-secret.md`, working tree (uncommitted revision on `threat-model`, base `f658d41`). Previous round: `.review/adr-review-round1-2026-10-24.md` (8.5/10, ACCEPT).

Changes since round 1 (from `git diff`):

1. New option row: empty default `${KEYCLOAK_ADMIN_CLIENT_SECRET:}` plus `@Validated` + `@NotBlank`, rejected because Boot owns the Gateway's `OAuth2ClientProperties`
2. Decision rationale extended: empty default named as runner-up, criteria stated (one rule shape, review, step 2 shared-code move, no reliance on a `:` suffix)
3. Review date added: 2027-04-24
4. Round-1 row added to the review record

---

## ADR 0007 — Fail at startup on a missing secret — 9.5/10 — ACCEPT

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

### Fact-check

| Claim | Where | Result |
|---|---|---|
| Secrets referenced as `${...}` | `gateway/.../application.properties:10`, `application-dev.properties:12`, `user-service/.../application.properties:27` | ✓ (test client secret only in the `dev` profile file) |
| Binding keeps an unresolvable placeholder as literal text | Boot binder resolves placeholders with unresolvables ignored; `@Value` would fail, `@ConfigurationProperties` does not | ✓ |
| `ClientSecretCheck` is an `InitializingBean`, walks every registration, rejects blank or `${`, maps client id → variable | `gateway/.../config/ClientSecretCheck.java:19-57` | ✓ |
| `KeycloakAdminProperties` compact constructor does the same check at binding time | `user-service/.../config/KeycloakAdminProperties.java:21-28`, bound via `@ConfigurationPropertiesScan` (`UserServiceApplication.java:8`) | ✓ |
| Both messages name the variable, `docker/.env`, and the IDE hint | both files | ✓ |
| Boot's `OAuth2ClientProperties` validates only `client-id` | Boot's registration validation throws only "Client id must not be empty" | ✓ |
| Order and Product hold no client secrets | only `spring.datasource.password=postgres` | ✓ (and matches the T32 bullet) |
| Six verification tests | `ClientSecretCheckTest` (3), `KeycloakAdminPropertiesTest` (3) | ✓ all exist under the names given |
| `DevLoginTest.wrongClientSecret_returns502_notAWrongPasswordMessage` | `gateway/src/test/.../DevLoginTest.java:111-125` | ✓ |

Imprecise:

1. **"the same check, the same message, the same tests"** (Decision rationale). The *condition* is identical (`hasText && !contains("${")`) and both messages carry the same hint, but the messages differ in shape (Gateway names the registration and client id; User Service names the property), and the tests differ (User Service also tests `null`; names differ: `realSecret_passes` vs `realSecret_binds`, `blankSecret_failsStartup` vs `missingSecret_failsStartup`). The Negative section itself says "two implementations". The argument holds; the wording overclaims.
2. **Tests named `…_failsStartup` don't start anything.** Both test classes call the check directly (`new ClientSecretCheck(...).afterPropertiesSet()`, `new KeycloakAdminProperties(...)`). No test proves the *application context* refuses to start with a placeholder secret. If `ClientSecretCheck` lost `@Component`, or moved out of the scanned package, every listed test would still pass and the Phase 4 incident would come back silently. The integration bases set the secret (`AbstractGatewayTest.java:46`, `AbstractIntegrationTest.java:27`), which shows the check is *expected* to run, not that it does. The User Service side is safer (the check is in the constructor that binding must call), the Gateway side is not.

### Rubric

| Dimension | Score | Δ vs R1 | Evidence | To gain points |
|---|---|---|---|---|
| Context & drivers | 2/2 | 0 | Real incident (IDE started before the env vars), the misleading `401` explained, mechanism correct. Driver traceable to Phase 4, PR #5. | — |
| Options | 2/2 | +0.5 | The round-1 gap is closed: the empty-default variant is now the runner-up, with its real advantage ("works for User Service's own record") and its real limit (Boot owns `OAuth2ClientProperties`, which "only checks that `client-id` is set"), plus the silent-regression cost of a forgotten `:`. Choosing it would look reasonable, which is the bar. Not in the table: a startup probe against Keycloak (see objection); acceptable, since the ADR scopes itself to *presence*. | — |
| Decision & rationale | 2/2 | +0.5 | Criteria are explicit and the choice beats the runner-up on them: "one rule written the same way in both services, or a hand-written check in the Gateway plus bean validation in User Service". The `:`-suffix point is a real failure mode, not taste. Scope clear (Order/Product excluded, with reason). One-minute answer matches the Decision and names the cost. | — |
| Consequences & risk | 2/2 | 0 | Two implementations, DB passwords uncovered (T32), wrong-but-present secret still starts (with the test that keeps that error distinct), `${` heuristic. Costs are concrete and bounded. | — |
| Accuracy & verifiability | 1.5/2 | 0 (+0.5 review date, −0.5 new finding) | Every code claim verified; review date and revisit triggers present; index title matches. But the verification proves the *check*, not that startup fails: no context-level test, so a wiring regression in the Gateway is invisible. "Same message, same tests" overclaims. | Add one context-level test per service (`ApplicationContextRunner` or a `@SpringBootTest` with the secret set to `${KEYCLOAK_GATEWAY_CLIENT_SECRET}`, expecting startup failure with the variable in the message), and list it under Verification. Reword to "the same condition and the same hint". |

**Total: 9.5/10** (round 1: 8.5). Pass 7, target 8. Normal impact, no defense needed. **ACCEPT.**

Optional changes, in order of points gained:

1. Context-level startup-failure test in the Gateway (and User Service), named under Verification (Accuracy +0.5).
2. Reword "the same check, the same message, the same tests" to what's true: same condition, same hint, parallel tests (part of the same +0.5).

### Interviewer's objection

"Your check proves the secret is *there*, not that it's *right*. A wrong secret is the same misconfiguration with the same misleading failure at the first login. Why not ask Keycloak for a token at startup and fail on that too?"

Why this is the weak point: the ADR's first driver is "a missing secret is found at deploy time, not by the first user". A wrong secret (stale `.env` after a realm re-import, copy-paste error) breaks that driver the same way, and the ADR only lists it as residual risk without saying why a probe was not chosen. An interviewer will push on the line between "present" and "valid".

What a strong answer would contain:

- A probe makes startup depend on Keycloak being up: compose ordering, starting one service from the IDE without Keycloak, and a crash loop whenever Keycloak restarts or is slow. That trades a misleading `401` for a coupling that fails more often than the misconfiguration it catches.
- Presence is a property of the deployment and can be checked offline; validity is a property of a remote system and belongs in a **readiness** check (start, report not-ready), not in a startup failure, and certainly not in liveness.
- The wrong-secret case is already made distinct at runtime (`LoginFailureHandler` hint naming `KEYCLOAK_GATEWAY_CLIENT_SECRET`, dev login `502` naming `KEYCLOAK_TEST_CLIENT_SECRET`), so the `401` no longer "points at the wrong problem".
- Revisit at step 4: once there is an orchestrator with readiness probes, a Keycloak token probe as a readiness indicator becomes cheap.

The ADR contains the third bullet in part (the `DevLoginTest` reference). One sentence with the first two in Consequences or Options would close it; optional, since it doesn't change the decision.

---

## Review record

Paste at the bottom of the ADR:

```
| 2 | 2026-11-07 | architecture-kata-reviewer | 9.5/10 | ACCEPT |
```

Index: set ADR-0007's Score to `9.5`.
