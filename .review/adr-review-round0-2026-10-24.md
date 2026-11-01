# ADR review — step 1 batch, round 0 (baseline)

| | |
|---|---|
| Date | 2026-10-24 |
| Reviewer | `architecture-kata-reviewer`, ADR mode |
| Target | `docs/adr/` ADR-0001 to 0013 (all `Proposed`) |
| Code at | branch `threat-model`, on top of `main` @ `770efaf` |
| Pass mark / target | 7/10 / 8/10 |
| Kind | **Baseline, not a gate run.** Same session that drafted the ADRs, so scores likely run 0.5–1 point high. Compare with the fresh-session gate run. |

Tracked in git (since 2026-11-01) so review state moves between devices.

## Summary

| ADR | Title | Score | Verdict |
|---|---|---|---|
| 0001 | Keycloak as the only Authorization Server | 8/10 | ACCEPT PENDING DEFENSE |
| 0002 | BFF: tokens stay in the Gateway | 9/10 | ACCEPT PENDING DEFENSE |
| 0003 | Gateway checks session; services enforce the rules | 9/10 | ACCEPT PENDING DEFENSE |
| 0004 | Owner from `sub`, `404` for others | 9/10 | ACCEPT |
| 0005 | Price from the catalog | 9/10 | ACCEPT |
| 0006 | Realm as code, secrets as placeholders | 9/10 | ACCEPT |
| 0007 | Fail at startup on a missing secret | 9/10 | ACCEPT |
| 0008 | Schema per service on one Postgres | 8.5/10 | ACCEPT |
| 0009 | Order → Product unauthenticated in v1 | 8.5/10 | ACCEPT PENDING DEFENSE |
| 0010 | Dev login behind a flag | 9/10 | ACCEPT |
| 0011 | Realm roles, an audience per service | 6/10 (capped) | REVISE (G3) |
| 0012 | Accepted risk: single Keycloak (T30) | 8.5/10 | ACCEPT |
| 0013 | Accepted risk: PII unencrypted (T33) | 9/10 | ACCEPT |

Average 8.6. Fact-check: every class, property, test name, realm setting and PR date was checked against code and git history. Two wrong claims (0011 history → G3 fail; 0001 verification line) and one misleading technical claim (T38 fix attribution in 0009, 0005).

---

## ADR 0001 — Keycloak as the only AS — 8/10 — ACCEPT PENDING DEFENSE

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 1.5/2 | "pull towards building the Authorization Server too" is specific, but the main driver is the learning goal, weak as a system force | Add the system driver: which flows v1 needs (PKCE, client credentials, Admin API, end-session) and that writing them is the risk |
| Options | 1.5/2 | SAS cons ("We would write the user store, login page, password hashing") overstated: Spring Security ships `PasswordEncoder` and default form login. Missing the reviewer's first question: another self-hosted IdP (Authentik, ZITADEL, Ory) | Fair SAS cons (registration, admin UI, user lifecycle, key rotation); add an "other OSS IdP" row |
| Decision & rationale | 1.5/2 | Hosted rejected "for this project" (offline, config in repo), not tied to the negatives the ADR lists (brute force off, `admin`/`admin`, SPOF), which hosted IdPs solve | One sentence on why self-hosting still wins even though hosted closes T25/T27/T30 out of the box |
| Consequences & risk | 2/2 | Every cost has a T-ID and an owning step | — |
| Accuracy & verifiability | 1.5/2 | "`TokenValidationTest`… so a token from any other issuer… is not accepted" is wrong: those tests check audience, not issuer. Revisit trigger "memory footprint blocks" not measurable | Name the audience tests, say issuer is checked by Boot's `issuer-uri` validator; put a MB number on the trigger |

Interviewer's objection (high impact, answer before seeing the strong answer): "Every negative you list (no brute-force protection, default admin credentials, single point of failure, H2 storage) is something Auth0 or Cognito gives you fixed on day one. Why take on running Keycloak yourself?"

---

## ADR 0002 — BFF — 9/10 — ACCEPT PENDING DEFENSE

Gates: all ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Quantified ("up to 10 hours", T29), IETF draft cited | — |
| Options | 1.5/2 | The cited draft has three patterns: BFF, token-mediating backend, browser-based client. The token-mediating backend (access token in browser memory, refresh token server-side) is the obvious runner-up and is missing. "Each service keeps its own session" row is filler | Add the token-mediating backend row; drop the filler row |
| Decision & rationale | 2/2 | Clear scope; SPA rejected on driver 2 (refresh token) | — |
| Consequences & risk | 2/2 | "BFF limits theft, not misuse" is the strongest line in the batch | — |
| Accuracy & verifiability | 1.5/2 | Six real tests. But stateless BFF rejected on "likely exceed the 4 KB cookie limit", never measured | Measure the three token sizes from a real login, state the number |

Interviewer's objection (high impact): "XSS can still call every API as the user while the tab is open. What did you actually buy with a stateful Gateway, CSRF handling and a choke point on every call?"

---

## ADR 0003 — Gateway checks session, services enforce — 9/10 — ACCEPT PENDING DEFENSE

Gates: all ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | TB7, "not true until step 3": why it had to be decided now | — |
| Options | 1.5/2 | "Both" row says "no security gain". Overstated: edge rejection shrinks the attack surface reaching services (an unknown bug in a Product write path, internal DoS) | Fair "Both" cons, then reject it on drift cost |
| Decision & rationale | 1.5/2 | "The edge copy adds no protection" repeats the overstatement. The Gateway's own "session required" is already a coarse edge rule; the ADR doesn't say why authentication goes at the edge but authorization doesn't | One sentence: authn at the edge because only the Gateway holds the session; authz with the data |
| Consequences & risk | 2/2 | T40 and non-admin forwarding named honestly | — |
| Accuracy & verifiability | 2/2 | Matches `SecurityConfig`; tests named | — |

Interviewer's objection (high impact): "A non-admin's `POST /products` crosses your internal network and reaches Product Service before anyone says no. Why let known-unauthorized traffic through on purpose?"

---

## ADR 0004 — Owner from `sub`, 404 — 9/10 — ACCEPT

Gates: all ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | BOLA, OWASP API1, existence leak | — |
| Options | 1.5/2 | Postgres row-level security only under Migration path; a reviewer would raise it as an option | Add RLS as an option, rejected for v1 |
| Decision & rationale | 2/2 | Rejections tied to drivers | — |
| Consequences & risk | 2/2 | "A new repository method without `sub` is a BOLA" | — |
| Accuracy & verifiability | 1.5/2 | Tests verified. No review date (convention requires one on security ADRs) | Add `Review date:` |

Objection: "Support needs to look up a customer's order; your design makes that impossible. Won't 404-by-construction get bypassed the first time the business asks?"
Strong answer: separate `/admin/**` path gated on a role, audit log on it, owner queries untouched.

---

## ADR 0005 — Price from the catalog — 9/10 — ACCEPT

Gates: all ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 1.5/2 | Driver cites "API6 (unrestricted access to sensitive business flows)". API6 is automated abuse (scalping); price tampering is closer to API3 (object property level authorization) or a business-logic flaw | Fix the OWASP mapping |
| Options | 2/2 | Verify-and-reject runner-up is fair | — |
| Decision & rationale | 2/2 | Scope explicit (fallback price left to Phase 6) | — |
| Consequences & risk | 2/2 | T18, T16, T15, T38 linked | — |
| Accuracy & verifiability | 1.5/2 | `OrderProductLookupTest` really sends `unitPrice: 0.01` (verified). No review date | Add review date |

Objection: "Product Service is down ten minutes on Black Friday; you 503 every order. Why not fall back to the last known price?"
Strong answer: stale-price risk vs lost revenue, it's the Phase 6 ADR, bounded staleness if ever cached.

---

## ADR 0006 — Realm as code — 9/10 — ACCEPT

Gates: all ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Manual-setup pain concrete | — |
| Options | 2/2 | keycloak-config-cli presented fairly | — |
| Decision & rationale | 1.5/2 | Rejects config-cli as "extra tooling for v1" but then lists the user-data wipe as a consequence without weighing it against the tool | Say why a wipe is acceptable now (test data only), so the trade is explicit |
| Consequences & risk | 2/2 | Drift, noise, T26, T35 | — |
| Accuracy & verifiability | 1.5/2 | grep check real; drift is named but nothing detects it | Add a drift check (export + diff), even as a manual step in `keycloak-setup.md` |

Objection: "Someone fixes brute-force protection in the console during an incident. Your file still says `false`; the next fresh import silently reverts it. How would you notice?"

---

## ADR 0007 — Fail at startup — 9/10 — ACCEPT

Gates: all ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Real incident, matches the IntelliJ note in `development.md` | — |
| Options | 1.5/2 | Missing the option a Spring reviewer suggests first: `@Value("${KEYCLOAK_...}")` without a default, which fails on an unresolvable placeholder by itself | Add it; rejected because Gateway secrets bind through Boot's OAuth2 client properties, not `@Value` |
| Decision & rationale | 2/2 | `@NotBlank` correctly shown not to work | — |
| Consequences & risk | 2/2 | "A wrong secret still starts" | — |
| Accuracy & verifiability | 1.5/2 | Six tests verified. No review date | Add review date |

Objection: "Is this even ADR-worthy? It's a 30-line validation class."
Strong answer: it sets a rule (no default secret, fail fast) that every later secret source in step 4 must keep.

---

## ADR 0008 — Schema per service — 8.5/10 — ACCEPT

Gates: all ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 1.5/2 | "Laptop resources" not quantified | What three Postgres containers would cost (RAM) |
| Options | 2/2 | DB-per-service presented as "close second" | — |
| Decision & rationale | 1.5/2 | Never says why schema-per-service beat DB-per-service in Phase 1, only that step 2 reopens it | The original reason in one line, or an honest "not weighed at the time" |
| Consequences & risk | 2/2 | "Isolation is a convention" with T31/T32 | — |
| Accuracy & verifiability | 1.5/2 | grep claim true; no automated check until step 2 | Name the step 2 fitness test now (app role `SELECT` on another schema fails) |

Objection: "All three services connect as the superuser. In what sense do they own their data?"
Strong answer: ownership by convention vs by permission, step 2 is the fix, why it wasn't day one.

---

## ADR 0009 — Order → Product unauthenticated — 8.5/10 — ACCEPT PENDING DEFENSE

Gates: all ✓ (T38 issue is close to G3 but TLS step 4 is also listed, so not failed)

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Only call site, public endpoint, separate project | — |
| Options | 2/2 | Five options; user-token relay rejection well argued | — |
| Decision & rationale | 1.5/2 | Deferral assumes step 5 closes T38. Client Credentials authenticates Order → Product, not Product's reply → Order. A forged reply needs TLS server authentication | Say response integrity is closed by TLS (step 4); step 5 closes caller identity |
| Consequences & risk | 1.5/2 | "Forged replies set the price… Fixed by step 5 (auth) and step 4 (TLS)" credits the wrong step | Same fix; also correct T38's "Fixed in" in the risk register |
| Accuracy & verifiability | 1.5/2 | "No test for the absence" honest. "Review date: when step 5 starts" is a trigger, not a date | Give a calendar date |

Interviewer's objection (high impact): "Your threat is a forged price in Product Service's reply. Which part of #03's Client Credentials flow stops that?"

---

## ADR 0010 — Dev login — 9/10 — ACCEPT

Gates: all ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 1.5/2 | "Postman and curl can't follow that flow" overstated: a Postman script can post Keycloak's login form | "Can't without scraping Keycloak's HTML login form" |
| Options | 2/2 | Five fair options | — |
| Decision & rationale | 1.5/2 | Alternatives rejected only inside the table; no prose on why Postman's helper or Playwright lose on the drivers | One "rejected because" line per runner-up |
| Consequences & risk | 2/2 | T26 "whatever the Gateway's profile" is the sharpest point | — |
| Accuracy & verifiability | 2/2 | Tests and realm flags verified; review date present | — |

Objection: "You fenced the endpoint off in the Gateway, but the password grant is live at Keycloak anyway. What did the flag protect?"
Strong answer: the flag protects the Gateway session path; T26 is the real exposure; the step 4 realm split is the fix.

---

## ADR 0011 — Realm roles, audience per service — 6/10 (capped) — REVISE

Gates: G1 ✓ G2 ✓ **G3 ✗** G4 ✓ G5 ✓

G3: Context says "Before Phase 4 … 'admin' was modelled as something granted to the client, so every user of the same client got it". Git history disagrees. Before `dab0968`, Product Service already used a **realm role** `product-admin` with `KeycloakRealmRoleConverter`; the realm-role-vs-scope choice was Phase 2 (PR #2), already argued in the old README. What Phase 4 changed: per-service `product-admin` → shared `admin`, `user` role required on Order, audience checks added. The ADR argues against a design that never existed and skips the real change (per-service roles are its own "client roles" runner-up, and the reason for leaving them is missing). Affects the decision → G3.

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 0.5/2 | False history (above) | Rewrite: Phase 2 chose realm roles over scopes; Phase 4 merged `product-admin` into shared `admin`, added `user`, added audience |
| Options | 2/2 | Two option tables, fair | (Could add token exchange as an audience option) |
| Decision & rationale | 1.5/2 | Client roles rejected as "would split one meaning into three", but `product-admin` was exactly that, and why it went isn't said | Give the real reason `product-admin` was replaced |
| Consequences & risk | 2/2 | "One token, three services", T17; service account's `default-roles-secure-shop` and default scopes verified in the realm file | — |
| Accuracy & verifiability | 1/2 | Tests verified; Date ("Decided … Phase 4, PR #4") half wrong, realm roles came in PR #2 | Date: "realm roles PR #2; shared `admin` + audience PR #4". Consider splitting into two ADRs (roles, audience) |

Required changes, in order of points gained:

1. Correct the Context history (removes the G3 cap)
2. State why per-service `product-admin` was replaced by a shared `admin`
3. Correct the Date line, or split into 0011 (roles, PR #2) and a new ADR (audience, PR #4)

Interviewer's objection (high impact): "You had a per-service `product-admin` role and replaced it with a global `admin`. That's the opposite of least privilege. Why?"

---

## ADR 0012 — Accepted risk: single Keycloak — 8.5/10 — ACCEPT

Gates: all ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Concrete outage behaviour (300 s, discovery at startup) | — |
| Options | 1.5/2 | Missing the middle option: one instance in production mode on Postgres (faster restart, data survives), or a longer access token lifespan to ride out short outages | Add one middle option |
| Decision & rationale | 1.5/2 | "No real users" carries the whole decision | State what makes it unacceptable (any real user, or an outage over X minutes) |
| Consequences & risk | 2/2 | H2 as a precondition for HA is a good catch | — |
| Accuracy & verifiability | 1.5/2 | "Gateway doesn't start" comes from a config comment, not a test; verification is manual | Run the manual check once and record the result |

Objection: "Accepted risk with no detection. How would you even know Keycloak is down before users tell you?"

---

## ADR 0013 — Accepted risk: PII unencrypted — 9/10 — ACCEPT

Gates: all ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Exact columns; passwords correctly placed in Keycloak | — |
| Options | 2/2 | Four options with fair limits (search, keys) | — |
| Decision & rationale | 2/2 | "Encryption at rest protects the disk, not SQL access" | — |
| Consequences & risk | 1.5/2 | "GDPR-style rules expect appropriate protection" vague; only two negatives | Name concrete exposures: backups, `docker/postgres-data/` copied off the laptop, logs with `show-sql=true` |
| Accuracy & verifiability | 1.5/2 | Verified, but verification is only "look at the files" | Name a check for the revisit trigger (deploy checklist item) |

Objection: "`spring.jpa.show-sql=true` is on in User Service. Does PII land in your logs, making the at-rest discussion moot?"
Strong answer: show-sql logs statements with `?` placeholders, not values, unless parameter binding is logged at TRACE; check it anyway and turn it off outside dev.

---

## Cross-ADR findings

1. **T38 attribution (0005, 0009, risk register):** all credit step 5 with closing forged replies. Client Credentials doesn't authenticate the reply; TLS (step 4) does. Fix all three together.
2. **Review dates missing** on security ADRs 0004, 0005, 0007 while the other security ADRs have one. Pick one rule and apply it to all.
3. **Check history against git before re-grading:** 0011 was wrong. Dates on 0001–0010 match the commits checked.
4. **0001 ↔ 0011:** both point `KeycloakRealmRoleConverter` duplication at finding #13. No contradiction.
5. **Index ↔ files:** match (13 rows, 13 files, Impact values agree).

## Review records (round 0, baseline)

Not gate scores. Paste only if you want the baseline kept in the ADRs.

| ADR | Row |
|---|---|
| 0001 | `\| 0 \| 2026-10-24 \| architecture-kata-reviewer (same session as drafting, baseline) \| 8/10 \| ACCEPT PENDING DEFENSE \|` |
| 0002 | `\| 0 \| 2026-10-24 \| architecture-kata-reviewer (same session as drafting, baseline) \| 9/10 \| ACCEPT PENDING DEFENSE \|` |
| 0003 | `\| 0 \| 2026-10-24 \| architecture-kata-reviewer (same session as drafting, baseline) \| 9/10 \| ACCEPT PENDING DEFENSE \|` |
| 0004 | `\| 0 \| 2026-10-24 \| architecture-kata-reviewer (same session as drafting, baseline) \| 9/10 \| ACCEPT \|` |
| 0005 | `\| 0 \| 2026-10-24 \| architecture-kata-reviewer (same session as drafting, baseline) \| 9/10 \| ACCEPT \|` |
| 0006 | `\| 0 \| 2026-10-24 \| architecture-kata-reviewer (same session as drafting, baseline) \| 9/10 \| ACCEPT \|` |
| 0007 | `\| 0 \| 2026-10-24 \| architecture-kata-reviewer (same session as drafting, baseline) \| 9/10 \| ACCEPT \|` |
| 0008 | `\| 0 \| 2026-10-24 \| architecture-kata-reviewer (same session as drafting, baseline) \| 8.5/10 \| ACCEPT \|` |
| 0009 | `\| 0 \| 2026-10-24 \| architecture-kata-reviewer (same session as drafting, baseline) \| 8.5/10 \| ACCEPT PENDING DEFENSE \|` |
| 0010 | `\| 0 \| 2026-10-24 \| architecture-kata-reviewer (same session as drafting, baseline) \| 9/10 \| ACCEPT \|` |
| 0011 | `\| 0 \| 2026-10-24 \| architecture-kata-reviewer (same session as drafting, baseline) \| 6/10 \| REVISE \|` |
| 0012 | `\| 0 \| 2026-10-24 \| architecture-kata-reviewer (same session as drafting, baseline) \| 8.5/10 \| ACCEPT \|` |
| 0013 | `\| 0 \| 2026-10-24 \| architecture-kata-reviewer (same session as drafting, baseline) \| 9/10 \| ACCEPT \|` |

## Comparing with the fresh-session run

Save the fresh run as `.review/adr-review-round1-<date>.md`. Compare per ADR: score delta, and whether it finds the same G3 in 0011 and the T38 issue in 0009. A fresh run that misses 0011's G3 means its fact-check was shallower, not that the ADR is fine.
