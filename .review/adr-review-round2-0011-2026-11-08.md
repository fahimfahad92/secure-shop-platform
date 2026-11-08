# ADR review — round 2 — ADR-0011 — 2026-11-08

Reviewer: `architecture-kata-reviewer` (ADR mode). Pass mark 7, target 8 (from `docs/adr/README.md`).

> **Independence:** fresh session (started after `/clear`). This session did not draft or edit the ADR, so the score can gate acceptance.

Target: `docs/adr/0011-realm-roles-and-per-service-audience.md`, working tree (uncommitted revision on `threat-model`; the ADR was last committed in `f658d41`).

Previous rounds:

- 2026-10-24 round 0: 6/10 capped, REVISE (G3) (`.review/adr-review-round0-2026-10-24.md`)
- 2026-10-24 round 1: 9.0/10, ACCEPT PENDING DEFENSE (`.review/adr-review-round1-2026-10-24.md`)
- 2026-11-08 round 1 + defense: 8.5/10, then REVISE after a failed defense (recorded in the ADR's review record; no report file in `.review/`)

## Round-1 required changes: status

| # | Required change (from the review record) | Status | Where |
|---|---|---|---|
| 1 | Accept the replay risk with a reason and a trigger that makes it unacceptable | ✓ Done | Negative: "One token, three services: replay by a compromised service is accepted". **Why accepted**: Postgres superuser (0008, T31, T32), the admin-client secret (T23), local R&D. **When it stops being acceptable**: step 2's per-service DB roles, or a service running off the machine. Repeated in the One-minute answer and the Revisit trigger. |
| 2 | Token exchange as a weighed audience option, with its costs | ✓ Done, with a factual problem | New row in the Audience table (setup, round trip per service per 300 s, cached, T30 dependency, refresh) and a rejection sentence in the Decision. **The runtime cost is overstated** (finding F1 below). |
| 3 | Say that per-service and shared audiences behave the same for today's tokens | ✓ Done | New paragraph after the Audience table. The conclusion is correct. "The only other client" is not (finding F2). |
| opt | Realm-JSON assertion test | ✗ Not done | Verification now lists the realm checks as "by inspection, no test". That is honest, but it isn't a test. |
| opt | Fix the test-name grouping in Verification | ✓ Done | Grouped per service; all names verified. |

Also new since the committed text: T23 added to Drivers; per-service enforcement bullet (closes the 10-24 Decision gap); Gateway `KeycloakRoles` copy disclosed; "Who can grant `admin`" negative; two new revisit triggers.

---

## ADR 0011 — Realm roles for `user`/`admin`, an audience per service — 8.5/10 — ACCEPT PENDING DEFENSE

Gates: G1 ✓ G2 ✓ G3 ✓ (F1 is wrong, but the decision still stands on its other reason; see below) G4 ✓ G5 ✓

### Fact-check

| Claim | Where | Result |
|---|---|---|
| Realm roles `user`, `admin`; `user` via `default-roles-secure-shop` | `docker/keycloak/secure-shop-realm.json`: composite has `offline_access`, `uma_authorization`, `user` | ✓ |
| Only `adminuser` has `admin` | realm users: `adminuser` = `default-roles-secure-shop, admin`; `testuser` = default only | ✓ |
| `secure-shop-audience` adds the three names; default only on gateway + test client | three `oidc-audience-mapper` (`included.custom.audience` = `order-service`, `product-service`, `user-service`); default scopes of `secure-shop-gateway`, `secure-shop-test-client`; absent from `user-service-admin-client` | ✓ |
| `orders:read`/`orders:write` in `defaultDefaultClientScopes`; admin-client gets them | realm, and `user-service-admin-client` default scopes | ✓ |
| Service account has `manage-users`, `view-users` on `realm-management` and the default composite | `service-account-user-service-admin-client` | ✓ |
| `accessTokenLifespan` 300 s | realm | ✓ |
| Converter maps `realm_access.roles` → `ROLE_*`, keeps `SCOPE_*` | `order-service/.../KeycloakRealmRoleConverter.java:29-34`; Product copy differs only by package | ✓ |
| Order: scope **and** role (`allOf(hasAuthority, hasRole)`) | `order-service/.../SecurityConfig.java:33-41` | ✓ |
| Product: `ROLE_admin` for writes, public GET, no scope | `product-service/.../SecurityConfig.java:26-29` | ✓ |
| User Service: neither role nor scope, only a valid token | `user-service/.../SecurityConfig.java:25-29` (`authenticated()`, default `jwt`) | ✓ |
| `jwt.audiences` set to the service's own name | each `application.properties` (`:17`, `:19`, `:19`) | ✓ |
| Gateway relays the same token to every service | `RouteConfig.java:79` `tokenRelay()` on every relay route | ✓ |
| Gateway `KeycloakRoles` copy, no signature check, display only | `gateway/.../auth/KeycloakRoles.java` | ✓ |
| All services as Postgres superuser | `spring.datasource.username=postgres` in all three | ✓ (T31, T32 match the register) |
| Replay at User Service gives no more than DB access | profile reads/updates are local DB; Keycloak admin calls happen only on register/compensate (`ProfileService`, `KeycloakAdminClient`) | ✓ (the subsumption argument holds) |
| Verification test names | all 3 `TokenValidationTest`s, both `KeycloakRealmRoleConverterTest`s, `OrderControllerSecurityTest:72,78` | ✓ all exist under the given names |
| T10, T17 (6 Low, step 4), T23 (10 Medium, open question), T30 | `docs/threat-model/risk-register.md:40,47,53,60` | ✓ |

Wrong or imprecise:

- **F1. The token-exchange outage cost is wrong.** The Audience row says token exchange "moves Keycloak onto the hot path of every API call, so a Keycloak outage (T30) stops calls that a valid session would otherwise serve". The Decision repeats it: "it puts Keycloak on every call's path". Two problems:
  1. The same cell says the exchanged tokens are "cached per session and audience" for 300 s. So it is not every call. It is one exchange per service per 300 s.
  2. The Gateway already depends on Keycloak at that rate. `SecurityConfig.authorizedClientManager` (`gateway/.../SecurityConfig.java:123-135`) refreshes the session's 300 s access token through `tokenRelay()`, and `RouteConfig` turns a failed refresh into `401` (`RouteConfig.java:80-82`). Today, a Keycloak outage already stops every session within 300 s. Exchanged tokens cached for the same lifetime fail on the same schedule. The new outage exposure is close to zero; what token exchange adds is more Keycloak load, not a new availability dependency.

  The cost that *is* missing: in Keycloak 26.5 (`docker-compose.yml:16`), standard token exchange takes the `audience` parameter as a **client ID**. `order-service`, `product-service` and `user-service` are custom audience strings today, not clients. Token exchange would need three new (bearer-only) clients in the realm, plus a custom Gateway filter in place of `tokenRelay()`. So the option is presented with an inflated cost and without its real one. This isn't a G3 failure, because the Decision also rejects token exchange for a second reason that holds: the replay it would remove is less than what the Postgres superuser already gives. But one of the two stated reasons is wrong.
- **F2. "The only other client, `user-service-admin-client`".** The realm also has `account`, `account-console`, `admin-cli`, `broker`, `realm-management` and `security-admin-console`. `admin-cli` is public with direct access grants enabled, so anyone who knows a user's password can get a token for that user from it. Its token has no `secure-shop-audience`, so both designs reject it. The paragraph's conclusion holds, but the premise is wrong. It should say "every other client in the realm, including `admin-cli` (public, direct grants)".
- **F3. "For up to 300 s" understates the replay window.** One token is replayable for 300 s, but the Gateway sends a fresh token on every request. A compromised service keeps getting valid tokens for as long as the user is active, so the real window is the user's session. This doesn't change the acceptance argument, which rests on subsumption, not on a short window. But a security ADR should state the window correctly.

### Rubric

Deltas are against the 2026-10-24 round-1 breakdown, the latest one saved (2 / 2 / 1.5 / 2 / 1.5 = 9.0). The 2026-11-08 round 1 has only a total (8.5) and no per-dimension record.

| Dimension | Score | Δ | Evidence | To gain points |
|---|---|---|---|---|
| Context & drivers | 2/2 | 0 | The before-state is a real bug ("admin" modelled on the client, no audience check), and the three questions a token must answer are stated. Drivers now include T23 and are traceable. | — |
| Options | 1.5/2 | −0.5 | Both tables are strong. Client roles and shared audience are steelmanned, and the "behave the same today" paragraph is exactly the honesty round 1 asked for. Token exchange is now in the table, but its runtime cost is inflated (F1: cached, and Keycloak is already on the refresh path every 300 s), and its real setup cost is missing: three audience clients, a filter in place of `tokenRelay()`. A reader who knows the Gateway would conclude that the runner-up was set up to lose. | Rewrite the token-exchange cons: setup = three bearer-only clients + per-client standard token exchange + a custom relay filter with a per-session, per-audience cache; runtime = one extra Keycloak call per service per 300 s, *on top of* the refresh dependency the Gateway already has (no new T30 exposure). Fix F2 in the paragraph. |
| Decision & rationale | 1.5/2 | 0 (10-24 gap closed, new gap) | Closed: the per-service enforcement bullet ("So the `user` role is enforced only at Order Service") fixes the 10-24 finding. The shared-audience rejection is now honest ("although it behaves the same today"). The One-minute answer matches the Decision and names the cost and when it stops being acceptable, so no cap. New: token exchange is rejected partly because "it puts Keycloak on every call's path", which is F1. The rejection should rest only on the true reasons: setup and code cost, against a replay risk that today is subsumed by the superuser path. | Rewrite the rejection sentence: "Token exchange was rejected for v1: it is the only option that contains replay, but it needs three new Keycloak clients and a custom relay filter, and the replay it removes gives less than the Postgres superuser already does (accepted below)." |
| Consequences & risk | 2/2 | 0 | The replay is now stated concretely (Order → Product admin writes, any service → User profile), accepted with a verified reason, and given an exit condition. "Who can grant `admin`" moves the risk to role assignment and ties it to T23's open question. The `KeycloakRoles` copy is disclosed with why it is safe. The costs are the most convincing part of the ADR. | (F3 wording is counted under Accuracy.) |
| Accuracy & verifiability | 1.5/2 | 0 (10-24 gap closed, new gaps) | Closed: test names are per service and all exist. Open: F1 and F2 are wrong claims (non-decisive). F3 understates the window. The realm-side facts that the decision depends on (audience scope only on two clients, `orders:*` realm defaults, `user` in the default composite, the 300 s lifespan) are "by inspection, no test", so a realm-file edit that breaks them goes unnoticed. Revisit triggers and review date are present; index title matches. | One realm-JSON assertion test (parse `secure-shop-realm.json` in a unit test and assert those four facts), and fix F1–F3. |

**Total: 8.5/10.** Pass mark 7, target 8: the score is at or above both. `Impact: high`, so the verdict is **ACCEPT PENDING DEFENSE**. The last defense failed, so a new one is required before acceptance.

Optional changes, in order of points gained (none is needed to pass; they would raise the score toward 10):

1. Correct the token-exchange costs in the table and the Decision sentence (F1); add the missing setup cost (Options +0.5, Decision +0.5).
2. Realm-JSON assertion test, and fix F2/F3 wording (Accuracy +0.5).

### Interviewer's objection (high impact, answer in drill mode)

"You rejected token exchange because it puts Keycloak on every call's path. But your Gateway already goes back to Keycloak every 300 seconds per session to refresh the access token, and if Keycloak is down, your sessions die within five minutes today. Exchanged tokens cached for the same 300 seconds fail on exactly the same schedule. So the outage cost you cite doesn't exist. What is the real cost of token exchange, and with that cost named, why is accepting cross-service replay still the right call rather than fixing it now?"

Answer it, and I'll evaluate it as an interviewer (one follow-up press at most). The shape of a strong answer is held back until you answer, because this ADR is high impact.

---

## Cross-ADR notes

- **0012 (T30) and F1.** If 0011's token-exchange row is corrected, it should say token exchange adds no T30 exposure beyond the existing refresh dependency. That keeps it consistent with 0012's description of the outage (sessions survive at most one token lifetime).
- **Review-record history.** The ADR's record starts at "Round 1, 2026-11-08", but `.review/` holds two earlier rounds for this ADR (2026-10-24: round 0, 6/10 capped, REVISE (G3); round 1, 9.0/10, ACCEPT PENDING DEFENSE). Add them so the history isn't lost. Renumbering is optional; the rows below keep the existing numbering and add this round as round 2.

## Review record (paste at the bottom of the ADR)

```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 0 | 2026-10-24 | architecture-kata-reviewer | 6/10 (capped) | REVISE (G3) |
| 1 (pre) | 2026-10-24 | architecture-kata-reviewer | 9/10 | ACCEPT PENDING DEFENSE |
| 1 | 2026-11-08 | architecture-kata-reviewer | 8.5/10 | ACCEPT PENDING DEFENSE |
| 1 (defense) | 2026-11-08 | architecture-kata-reviewer | 8.5/10 | REVISE. <existing text> |
| 2 | 2026-11-08 | architecture-kata-reviewer | 8.5/10 | ACCEPT PENDING DEFENSE. All three round-1 required changes made. New: token-exchange outage cost overstated (Gateway already refreshes via Keycloak every 300 s; exchanged tokens cached same lifetime); its real setup cost (three audience clients, custom relay filter) missing; "only other client" ignores `admin-cli`; replay window is the session, not 300 s. Optional: fix those; realm-JSON assertion test |
```

On a passed defense, add: `| 2 (defense) | <date> | architecture-kata-reviewer | 8.5/10 | ACCEPT. Defense: passed <date> |` and put 8.5 in the index Score column.

---

## Defense — 2026-11-08 — PASSED

### What a strong answer would contain (now shown)

- Concede the outage point: refresh already ties sessions to Keycloak at 300 s; cached exchange adds load, not new T30 exposure
- Real cost is build work: audience targets must become Keycloak clients, exchange enabled and restricted on the Gateway client, a relay filter in place of `tokenRelay()` with a per-session, per-audience cache, test-stub changes
- Value today is about zero: the Postgres superuser path (T31, and T32 open on the network) gives more than replay
- Sequencing: step 2 first, then exchange; name who owns the fix when the trigger fires

### Evaluation

What landed:

- Clean concession, with the right distinction: availability unchanged, load ×4 but negligible. No defending a wrong claim, which is what failed last time.
- Cost list is concrete and correct for this codebase: custom-audience mappers → clients (matches the realm: `included.custom.audience` strings, no such clients); `tokenRelay()` sends one token (`RouteConfig.java:79`); `TokenExchangeOAuth2AuthorizedClientProvider` exists in Spring Security 6.3+ (Boot 3.5 ships 6.5); the dev-login path is a real second entry point; tests per route. The Keycloak 26 audience-as-client-ID point is flagged as unverified against the instance, which is honest.
- The subsumption argument is the right core, and the "locking the door while the wall is missing" framing works in an interview.
- Sequencing matches the ADR's trigger and 0009's precedent (#03 owns service-to-service design).

Hand-waved or overstated (not enough to fail):

- **"#03 ... turns Product Service into a real resource client that checks `aud=product-service`."** Product already checks `aud=product-service` today (`product-service/.../application.properties:19`). Per 0009 §Migration path, what #03 adds is a Client Credentials client for **Order Service** (a caller) and a service scope on `reserve-stock`.
- **"The per-service clients overlap ... pays it twice."** The overlap is partial. Exchange needs *audience-target* clients for all three services. #03 adds one *caller* client (Order Service). So "shares some setup", not "pays twice".
- **The fix has no owner.** The answer says "step 2 or step 5". When the trigger fires, the ADR reopens the risk but names no step that does the work, whereas 0009 names #03 as the owner. A senior candidate would commit to one, for example: "token exchange is decided in the step 5 (#03) service-to-service ADR, and is blocked from starting before step 2 lands".
- Missed an extra point in your favour: T32 (`postgres`/`postgres` reachable on the network) means even a network attacker without a compromised service already has superuser. The subsumption argument is stronger than stated.

Verdict: **the answer holds. ACCEPT, 8.5/10.** No press needed: the gaps above are precision, not a hole in the decision. The defense also exposed no gap the review hadn't already scored. F1 was found and deducted in round 2.

Strongly recommended before merge, though not required by the verdict: the ADR text still contains the claim you just conceded ("puts Keycloak on every call's path"). Fold the defense into the ADR:

1. Rewrite the token-exchange cons and the rejection sentence along the lines of your cost list 1–3 (fixes F1)
2. Fix F2 (`admin-cli`) and F3 (the replay window is the session)
3. Name the owner of the reopened risk (step 5 / #03 ADR, after step 2)

A re-grade with those changes would likely score 9.5 (Options, Decision and Accuracy each +0.5, minus nothing).

### Review-record row

```
| 2 (defense) | 2026-11-08 | architecture-kata-reviewer | 8.5/10 | ACCEPT. Defense: passed 2026-11-08. Conceded the token-exchange outage cost (refresh already ties sessions to Keycloak at 300 s); real cost named as build work (audience clients, exchange on the Gateway client, custom relay filter and cache, tests); replay accepted as subsumed by the Postgres superuser path until step 2. Recommended: fold this into the ADR (F1–F3) and name the step that owns token exchange |
```

Index: Score column for 0011 → 8.5.
