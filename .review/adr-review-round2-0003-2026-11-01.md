# ADR review — round 2 — ADR-0003 re-review and defense

- Date: 2026-11-01
- Reviewer: architecture-kata-reviewer (ADR mode)
- Branch: `threat-model` @ `da842a8` plus the uncommitted revision of `docs/adr/0003-gateway-checks-session-services-enforce-rules.md` (`git diff`: +23/−9)
- Pass mark 7/10, target 8/10 (from `docs/adr/README.md`)
- Independence: this session did not draft or edit ADR-0003. The score comes from the current text alone; round 1 (`adr-review-round1-2026-10-24.md`) used only for deltas.
- Fact-check (all match): Gateway matchers incl. `POST /auth/dev-login` `permitAll`, `denyAll` default, `HttpStatusEntryPoint(UNAUTHORIZED)` (`gateway/.../config/SecurityConfig.java`); Order `GET` = `SCOPE_orders:read` + `user`, else `SCOPE_orders:write` + `user`, ownership via `jwt.getSubject()`; Product `GET` public, else `hasRole(admin)`; User `register` public, else `authenticated()`, protected endpoints only `/users/me` GET/PUT/DELETE; `jwt.audiences=<service>` in all three `application.properties`; `KeycloakRealmRoleConverter` in Order and Product only; `AbstractIntegrationTest` blanks `issuer-uri`, no expired-token test; every test named in Verification exists. Gateway `RouteConfig` public `GET /products/**` route does not relay the token.

Tracked in git (since 2026-11-01) so review state moves between devices.

## ADR 0003 — The Gateway checks only "is there a session"; services enforce the rules — 8/10 — ACCEPT PENDING DEFENSE → Defense passed, required changes open

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Score | Δ vs round 1 | Evidence | To gain points |
|---|---|---|---|---|
| Context & drivers | 2/2 | 0 | Tied to TB7, T8, T13, T40 (13 High); "today" vs step 3 stated. | — |
| Options | 1.5/2 | 0 | "Both" now steelmanned (round 1 fix landed: regressed `permitAll` scenario named). New gap: header identity argued only against today's open ports; its real form (Gateway-signed internal token, or headers + mTLS/network policy) survives step 3. Externalized policy (OPA / sidecar PDP) missing. | Steelman header identity in its step-3 form; add externalized-policy row with fair rejection. |
| Decision & rationale | 1.5/2 | 0 | Rejection of "Both" now on cost (round 1 fix landed). But part of it is time-bound ("today the layer is skipped by any direct caller"). Migration path "the decision stays" vs Revisit trigger "after step 3 … coarse role checks at the Gateway become worth their duplication cost" read as contradictory. | One post-step-3 position, same wording in both sections. |
| Consequences & risk | 1.5/2 | 0 | Negatives strong ("One layer per rule", "User Service's default is the weakest", new-endpoint gap — round 1 fix landed). New: Positive bullet "Defense in depth" contradicts "one layer per rule". | "Defense in depth" → "No dependency on the Gateway". |
| Accuracy & verifiability | 1.5/2 | +0.5 | Dev-login line and issuer/expiry scoping fixed (round 1). Gaps: no `deleteMyProfile_noToken_returns401` though DELETE `/users/me` claimed protected; T13 driver but `OrderOwnershipTest` not in Verification; no mechanical check for "new endpoint without a security test". | Route-manifest fitness test; DELETE test; reference `OrderOwnershipTest`. |

Total 8/10 (+0.5 vs round 1's 7.5).

## Defense

**Objection:** "Each service's `SecurityConfig` is the only layer for its rules, and a new endpoint is guarded only if someone remembers a security test. What mechanism — not discipline — guarantees every endpoint is covered, and once step 3 makes the Gateway unbypassable, why isn't a coarse edge role check that mechanism?"

**Answer (summary):** forgetting fails closed on Order/Product (`anyRequest()` is the strictest rule); User Service is the exception → explicit `/users/me` matchers + `anyRequest().denyAll()`. Fitness test over the handler mapping: no token → 401 unless on an explicit public allowlist. Step 3 doesn't make the edge see internal callers (Order → Product, future service accounts); an edge rule per new endpoint is the same discipline in a second place; Gateway `denyAll` on unknown paths is the coarse check worth having.

**Press:** wildcard prefixes — `GET /products/**` `permitAll` makes a new `GET /products/admin/stats` public; `GET /orders/**` read + `user` lets a new `GET /orders/all` that forgets `sub` leak all orders; the no-token test passes both. What catches authenticated-but-wrongly-authorized?

**Answer (summary):** two bugs, two mechanisms. Matcher bug → exact routes (`GET /products`, `GET /products/{id}`), allowlist of exact patterns, checked-in route-to-rule manifest so a new route fails the build until its rule is declared. Ownership bug → data layer: Postgres RLS on `user_sub` (already ADR-0004's migration path, verified), ArchUnit "every `OrderRepository` query takes `userSub`" as a weaker option, plus a generic cross-user test (seed A, call every `/orders` route as B, assert A's order never appears). Neither bug is visible at the edge.

**Precision notes (not failures):**
- Gateway has the same wildcard: `RouteConfig` `publicRoute` `GET /products/**` never relays the token and `SecurityConfig` makes it `permitAll`. After tightening the service, an admin `GET /products/admin/stats` via the Gateway always gets 401. Narrowing must touch both deployables (Gateway owns routing, not rules).
- RLS with a pool needs `SET LOCAL app.user_sub` per transaction.
- ArchUnit checks signatures, not that the parameter reaches the `WHERE` clause.

**Result: Defense passed 2026-11-01.**

## Required changes (answer content not yet in the ADR)

1. **User Service default.** Explicit `/users/me` matchers (GET, PUT, DELETE) + `anyRequest().denyAll()`, recorded as a decision. Replaces the "User Service's default is the weakest" residual risk.
2. **Exact-route matchers** instead of `/**` on permissive rules, in the services and in the Gateway (`SecurityConfig` + `RouteConfig`).
3. **Verification.** Route-manifest fitness test (no token → 401 unless on the exact-pattern allowlist; a route missing from the manifest fails the build), cross-user ownership test, and `deleteMyProfile_noToken_returns401`. Reference `OrderOwnershipTest`.
4. **Rationale for rejecting "Both".** Step 3 doesn't cover internal callers; edge rules duplicate the discipline; neither authorization bug class is visible at the edge. Also resolves the Migration path ↔ Revisit trigger conflict.
5. **Consequences.** "Defense in depth" → "No dependency on the Gateway". Add RLS as the ownership backstop, linked to ADR-0004.

Items 1–3 change code. The ADR records them as done, or as a scheduled step with test names; Verification must not list tests that don't exist yet.

Optional (Options dimension, +0.5): steelman header identity as a Gateway-signed internal token; add an externalized-policy (OPA) row.

## Next

Make changes 1–5, then re-grade in a fresh session (round 3). No new defense needed unless the decision itself changes. Final score goes into the `docs/adr/README.md` index after round 3.

## Review record (as in the ADR)

```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 7.5/10 | ACCEPT PENDING DEFENSE (below target) |
| 2 | 2026-11-01 | architecture-kata-reviewer | 8/10 | ACCEPT PENDING DEFENSE |
| 2 (defense) | 2026-11-01 | architecture-kata-reviewer | 8/10 | Defense: passed 2026-11-01; required changes 1–5 open |
```
