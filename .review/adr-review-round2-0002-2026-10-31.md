# ADR review — round 2 — ADR-0002 re-review

- Date: 2026-10-31
- Reviewer: architecture-kata-reviewer (ADR mode)
- Branch: `threat-model` @ `b6417d3` plus the uncommitted revision of `docs/adr/0002-bff-tokens-stay-in-the-gateway.md` (`git diff`: one Scope paragraph in Decision outcome, one reworded Negative bullet, one new Revisit trigger)
- Pass mark 7/10, target 8/10 (from `docs/adr/README.md`)
- Independence: this session did not draft or edit ADR-0002. `.review/adr-review-round1-2026-10-24.md` was read only to compute deltas, as requested. The score comes from the current text alone.
- Fact-check: no commits under `backend/gateway` since round 1 (last: `29ad79f`). Re-verified: `HttpSessionOAuth2AuthorizedClientRepository` (`SecurityConfig.java:115`), `OAuth2AuthorizationRequestCustomizers.withPkce()` (`:149`), `CookieCsrfTokenRepository.withHttpOnlyFalse()` (`:98`), `Cookie` and `X-XSRF-TOKEN` removed on both route groups (`RouteConfig.java:67-68, 77-78`), `http-only=true` and `same-site=lax` (`application.properties:20-21`), realm `revokeRefreshToken: false`, `ssoSessionIdleTimeout: 1800`, `ssoSessionMaxLifespan: 36000`, risk IDs T1/T5/T29/T34 with their scores, all six verification tests exist. New link checked: ADR-0001 does name a "production IdP ADR" (lines 47, 98). Still unverified: the stateless-BFF cookie size.

## ADR 0002 — BFF: tokens stay in the Gateway — 9.0/10 — ACCEPT PENDING DEFENSE

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Score | Δ vs round 1 | Evidence | To gain points |
|---|---|---|---|---|
| Context & drivers | 2/2 | 0 | Concrete threat: "one XSS bug and the refresh token can be used … up to 10 hours" matches `ssoSessionMaxLifespan: 36000` and `revokeRefreshToken: false` (T29). The driver "Simple enough for one instance on a laptop" now agrees with the new Scope line. | — |
| Options | 1.5/2 | 0 | Unchanged text. Stateless BFF is steelmanned. The SPA row is still the weakest form ("tokens in memory or `localStorage`"); the form a reviewer raises is an in-memory SPA with refresh-token rotation and sender-constrained tokens (DPoP). "Each service keeps its own session — None worth having" is still filler. | Replace the SPA row with its strongest form and say why BFF still wins; drop or shrink the filler row. |
| Decision & rationale | 1.5/2 | 0 | The Scope paragraph is good: pattern decided for every environment, storage decided for local/test only. Scope was already credited in round 1, so it adds no points. Two gaps: (1) the stateless-BFF rejection still rests on "likely exceed the 4 KB cookie limit", unmeasured; (2) new, the Scope line says production storage "is decided when the production deployment is planned" but in the same sentence names the answer ("a shared session store with its own database"), while the next paragraph keeps stateless BFF open "if the Gateway has to scale out without a shared session store". Is production storage open or not? Also, the step-4 "Shared environment" in Migration path is neither "local development and testing" nor "production": the Scope line doesn't say which side of the line it falls on, and ADR-0001 fires its trigger earlier ("first deployment outside a developer machine") than this ADR ("production deployment is planned"). | Measure one access+refresh+ID token set from this realm, or rest the stateless rejection on key management and no kill switch alone. Reword Scope to "production storage is open: shared store (likely) or stateless BFF", and place step 4 explicitly (in-memory OK on a single-instance shared env, or trigger fires). Align the trigger wording with ADR-0001's. |
| Consequences & risk | 2/2 | 0 | Still the strongest section: XSS rides the session, restart logs everyone out (now with the honest "costs a re-login" for one developer), T5, T1, T34, no back-channel logout, choke point. One omission, outside current scope but in the Migration path text: "tokens move with it" understates that a shared session store puts every user's refresh token at rest in a database (Spring Session serializes `OAuth2AuthorizedClient` as-is), which turns T34 from a memory-dump risk into a data-store risk. | Optional: add to Migration path "session store holds tokens at rest: encrypt or isolate it; new risk in the production ADR". |
| Accuracy & verifiability | 2/2 | 0 | Every claim re-verified (see Fact-check). New trigger links to ADR-0001's production IdP ADR, which exists as a commitment in 0001. Six tests, four triggers, review date 2027-10-24. | Optional: state the Gateway session timeout (servlet default 30 min, nothing set in `application.properties`) next to Keycloak's 30 min SSO idle. |

**Total 9.0/10 (round 1: 9.0, Δ 0).** The revision clarifies scope and closes a question a reviewer might have asked about production, but it touches neither of the two items round 1 listed as point-gainers (SPA strongest form, unmeasured cookie size), so no dimension moves.

Interviewer's objection (carried from round 1, because the ADR still doesn't contain the answer):

"Your own consequences say XSS can still ride the session while the tab is open, and the frontend will be same-site. Compare against the strongest SPA, not the tutorial one: tokens in memory only, refresh-token rotation with reuse detection, DPoP-bound tokens. What concrete attack does your BFF stop that this SPA doesn't, and is that difference worth a stateful choke point in front of every call, plus a session database in production?"

(High impact: answer in drill mode. The strong-answer shape is withheld until you answer.)

## Optional changes (score is above target; none required)

1. Options: steelman the SPA row (in-memory, rotation, DPoP) and state the residual difference. (+0.5, Options)
2. Decision: measure the cookie size or drop the claim; make production storage explicitly open; place the step-4 shared environment relative to the Scope line. (+0.5, Decision & rationale)
3. Migration path: tokens at rest in the session store.

## Defense drill — 2026-10-31 — ACCEPT (defense passed)

### Answer 1 (main objection): holds on the core

> BFF doesn't stop XSS using the session in the open tab. It stops XSS getting tokens usable anywhere: a public-client SPA can be made to run a new login flow and hand over a fresh token set; rotation or DPoP can't prevent that. A confidential client in the Gateway can. The Gateway already sits on every call, so the extra cost is session state and CSRF. Also it cannot get the refresh token, so the token can't be used for long.

Landed: the new-token-acquisition attack (silent `prompt=none` flow riding the Keycloak SSO cookie), why rotation (new family) and DPoP (attacker's own key) don't stop it, and why a confidential client does (code redeemable only by the Gateway with its secret). Weak: "can't get the refresh token" misframes it; XSS gets no token at all, and the bound is online-only, on-device, server-revocable. Cost half ("session state and CSRF") is enough for the scope this ADR decides: local, one instance, sessions in memory.

Strong answer, first half:
- Name the attack: new token acquisition via a fresh code flow (IETF browser-apps draft)
- Rotation and DPoP protect stolen tokens, not freshly minted ones
- Confidential client: code is useless without the Gateway's secret and redirect URI
- Residual misuse is bounded to the victim's open tab and ends server-side

### Press (once): production session store — withdrawn as out of scope

> "In production your Scope line puts every user's refresh token in a shared session database. What stops a store compromise, and why is the trade still better than the SPA?"

Answer 2:

> Only the Gateway reaches the store, and tokens are encrypted at rest.

**Withdrawn (2026-10-31, after the author's pushback).** This follow-up misread the ADR's scope. The Scope line decides session storage for local development and testing only, and leaves production storage to the production ADR. ADR-0001 is scoped the same way, and it passed its defense the same day. Asking for production store controls here graded a decision this ADR explicitly does not make, so this follow-up does not count against the defense. The notes below are kept as input for the production ADR, not as findings against this one.

Notes for the production ADR:
- **Key placement unstated.** If the Gateway holds the key, encryption protects backups, snapshots and a DB-only breach, not a Gateway compromise (which T34 already says exposes everything). Spring Session does not encrypt attributes by default, so this is new work, not a property of the design.
- **No blast-radius bound.** Tokens in a leaked store are valid until expiry: refresh up to 10 h with `revokeRefreshToken: false` (T29). No mention of short session TTL, rotation, or bulk revoke (flush the store, Keycloak session logout).
- **"Why still better than the SPA" unanswered.** The argument exists (store breach needs a server-side compromise of one hardened, monitored component; XSS is reachable from every npm dependency and widget; one place to revoke everyone) but wasn't made.

What the production ADR should cover:
- Network isolation plus dedicated store credentials; encryption key outside the store (KMS or Gateway secret), and what that does and doesn't cover
- Bound the leak: session TTL, refresh rotation (T29 fix), a kill switch (flush store + Keycloak logout-all)
- Comparison: one server-side component to harden and audit vs every script on every page

### Recommended changes (not required; verdict is ACCEPT)

1. **Options / Decision:** put Answer 1 into the ADR. Replace the SPA row with its strongest form (in-memory, rotation, DPoP) and reject it on new token acquisition, which a public client can't prevent. This is the ADR's best argument and it isn't written down; it also earns back the Options half point.
2. **Revisit trigger, one line:** "the production ADR decides how the session store protects tokens at rest". This is a pointer, not a design. The BFF pattern is decided for every environment, so its main production cost should be named where the pattern is decided.
3. **Scope line:** say which side of the line the step-4 shared environment falls on.

## Review record (paste at the bottom of ADR-0002)

```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 9/10 | ACCEPT PENDING DEFENSE |
| 2 | 2026-10-31 | architecture-kata-reviewer | 9/10 | ACCEPT PENDING DEFENSE |
| 2 (defense) | 2026-10-31 | architecture-kata-reviewer (drill) | — | ACCEPT. Defense: passed 2026-10-31 (new token acquisition; production storage out of scope, follow-up withdrawn) |
```
