# ADR review — round 2 — ADR-0006 — 2026-11-07

Reviewer: `architecture-kata-reviewer` (ADR mode). Pass mark 7, target 8 (from `docs/adr/README.md`).

> **Independence:** this round ran in the same session that wrote the revision (commit `e0ffd7a`). Per the convention, the score that gates acceptance should come from a fresh-session re-run. This score is provisional.

Target: `docs/adr/0006-realm-as-code-secrets-as-placeholders.md` at `e0ffd7a`. Previous round: `.review/adr-review-round1-2026-10-24.md` (6.0/10, rubric 8.5 capped by G3, REVISE).

---

## ADR 0006 — Realm as code, secrets as `${...}` placeholders — 8.0/10 — ACCEPT

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

G3 cleared: the round-1 failure was the decision's *scope* (seeded credentials outside the definition of "secret", verification blind to them). The Context now defines a secret as "any value that authenticates against an imported realm", the Decision names both users as dev-only fixtures, and the Verification checks credentials. One new wrong claim was found (the localhost line, below). It is scored under Accuracy, not as a gate failure, because the decision holds once the reasoning is corrected: correcting the line changes the justification, not the decision.

### Fact-check

Verified against the code:

| Claim | Where | Result |
|---|---|---|
| Three client secrets are `${...}` | `secure-shop-realm.json:796/859/989` | ✓ |
| Compose `${VAR:?set it in docker/.env}` | `docker-compose.yml:27-29` | ✓ |
| `start-dev --import-realm`, import skipped with existing `keycloak-data/` | `docker-compose.yml:18-20, 33` | ✓ |
| `testuser`/`test`, `adminuser`/`test` (`admin` role), `"temporary": false` | `secure-shop-realm.json:485-506` | ✓ |
| `grep -n '"type": "password"'` returns exactly lines 492 and 505 | ran it | ✓ |
| `adminuser` logs in through `secure-shop-gateway` (standard flow on) | `secure-shop-realm.json:806` | ✓ |
| T43 is 13 High, step 4, in the register, the Keycloak STRIDE file and the changelog | `docs/threat-model/` | ✓ |
| ADR-0010 links back | `0010…md`, T26 bullet | ✓ |
| About 2,900 lines | 2,862 | ✓ |

Wrong or unsupported:

1. **"the realm is reached only through `localhost`"** (Decision → Seeded users, the acceptance's stated reason). False: `docker-compose.yml:31` publishes `"8081:8080"` on all interfaces, and no app sets `server.address`, so the Gateway (`:8090`) and services are reachable from the LAN too. The threat model says so itself (T27 "published on all interfaces (TB7)", T40). So the ADR's security rationale contradicts its own threat model.
   What actually limits `adminuser`/`test` from the LAN today is different, and I checked it:
   - The browser login fails off-host: the Gateway's only redirect URI and every `issuer-uri` are `http://localhost:…` (`secure-shop-realm.json:798`, `application.properties`), so a remote browser is sent to *its own* localhost.
   - The audience-bearing password grant (`secure-shop-test-client`) needs a secret from `docker/.env` (T26).
   - `admin-cli` in the realm *is* public with direct grants on (`secure-shop-realm.json:670-674`), so anyone on the LAN can get a token for `adminuser`/`test` at `host:8081`. That token is rejected by the services only because `admin-cli` lacks the `secure-shop-audience` scope (`:684-690`) and audience is enforced (T10 verified). The ADR mentions none of this.
2. **"Its allowlist names only the two fixture passwords, so any other credential fails the build"** (Verification). Stated as how a step 3 check behaves, but no step 3 design exists, and default secret-scanner rules (gitleaks, trufflehog) don't flag a short JSON `"value"` like `test` in the first place. Without a custom rule for Keycloak `credentials`, nothing fails. This is the same "policy written as fact" pattern round 2 flagged in ADR-0001.
3. **"Postman and Newman need a known user"**. There is no Newman run in the repo (no CI yet). The Postman collection is real.
4. **The fixture lives in more places than the ADR knows.** `test` is also committed in `postman/secure-shop-platform.postman_collection.json:55, 79` (`password`, `adminPassword`), `README.md:46` and `docs/development.md:100`. The Verification grep only covers the realm file, and the Decision's exit path ("seeded passwords become `${...}` placeholders") would leave these behind.

### Rubric

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Manual setup pain, then "two kinds of credential", a precise definition of secret, and a problem statement that covers both. T43 in Drivers. | — |
| Options | 1.5/2 | The table still compares only how the *realm* is stored. The decision now has a second axis, the seeded users, and its alternatives aren't in the table: password placeholders from `.env` (the obvious runner-up, same mechanism as the client secrets), `"temporary": true`, or no seeded users (create them through the Admin API or registration in a setup script). | Add a second small table, or rows, for the seeded users: placeholder, temporary, none, fixture. |
| Decision & rationale | 1.5/2 | Says why fixtures are *acceptable* (local, no real users), not why they *beat* placeholders. The one reason given, "Postman and Newman need a known user and a known admin", doesn't separate them: a placeholder value is just as known to the developer who set it. The real separating reason is in the repo but not the ADR: the fixture password is shared with the Postman collection and docs, so a placeholder would need collection variables and doc changes too. One-minute answer matches the Decision (5 sentences, slightly long). | Two sentences: placeholders rejected because the password is a shared fixture across the collection and docs, and changing it buys nothing while the stack is local. Name that cost. |
| Consequences & risk | 2/2 | The new T43 bullet is concrete (admin role, real Gateway login, catalog change or delete, combines with T26) and scored. "Unacceptable when" is stated as the end of the acceptance. Costs remain the most convincing part. | — |
| Accuracy & verifiability | 1/2 | Strong mechanical checks (two greps, compose `:?`), all verified. But the acceptance's stated reason is false (localhost), the scanner allowlist is a planned behaviour stated as fact and wouldn't work with default rules, Newman doesn't exist, and the verification misses three other files carrying the same password. Migration path still talks about "different client sets" only. | Replace the localhost line with the real limits (localhost redirect URI and issuer, test client secret, `admin-cli` without audience). Reword the scanner as "step 3: a custom rule over `credentials` values, fixtures allowlisted". Add the collection and docs to the verification. Mention users in the migration path. |

**Total: 8.0/10** (pass 7, target 8). Normal impact, no defense needed. **ACCEPT** (provisional until a fresh-session run).

Optional changes, in order of points gained:
1. Fix the localhost claim with the real limits (Accuracy +0.5). This is the one a security reviewer would stop on.
2. Name the separating reason against placeholders: shared fixture in collection and docs (Decision +0.5).
3. Add the seeded-user options (Options +0.5).
4. Scanner wording, Newman, collection/docs in verification, users in migration path (Accuracy, with item 1 up to +1).

### Delta vs round 1 (2026-10-24, 6.0/10 capped, rubric 8.5)

| Dimension | Round 1 | Round 2 | Δ | Why |
|---|---|---|---|---|
| Gates | G3 ✗ (cap 6) | all ✓ | cap lifted | Seeded users now inside the decision's scope and its verification |
| Context & drivers | 2.0 | 2.0 | 0 | Strengthened (definition of secret), already full |
| Options | 2.0 | 1.5 | −0.5 | Decision scope grew to the users; the options table didn't |
| Decision & rationale | 2.0 | 1.5 | −0.5 | New user decision lacks a reason that beats the runner-up |
| Consequences & risk | 1.5 | 2.0 | +0.5 | Seeded admin added as residual risk with T43 |
| Accuracy & verifiability | 1.0 | 1.0 | 0 | Credentials grep added (fixed); new wrong localhost claim, scanner overclaim, missed collection/docs (new) |
| **Total** | **6.0** (8.5 uncapped) | **8.0** | **+2.0** | |

Round-1 required changes: 1 ✓ (choice stated), 2 ✓ (T43, register, changelog), 3 ✓ in part (realm grep yes; scanning stated as fact), 4 ✓ (ADR-0010 link).

### Interviewer's objection

"You already trust `${...}` placeholders for three client secrets. Two more lines in `.env.example` would placeholder the passwords too. Why accept a known admin password at all, and how sure are you that 'localhost only' is true? Your compose file publishes Keycloak on every interface."

Why this is the weak point: the acceptance rests on one sentence, and that sentence is false; the protection that holds is accidental (redirect URIs, missing audience on `admin-cli`). And the obvious alternative costs almost nothing in the realm file, so "why not" needs a real answer.

What a strong answer would contain:
- The honest exposure: Keycloak and the apps bind all interfaces; LAN reach is limited by localhost redirect URI and issuer, the test client's secret, and audience checks that reject `admin-cli` tokens, not by binding
- A cheap fix that removes the luck: bind ports to `127.0.0.1` (already planned in step 4 for T27/T32), or move it earlier
- The real cost of placeholders: the password is shared with the Postman collection and docs, so it's a fixture, not a secret, while nothing leaves the machine
- The exit: step 4's dev/non-dev split, and the revisit trigger on any non-dev import

---

## Review record

Paste at the bottom of ADR-0006:

```
| 1 | 2026-10-24 | architecture-kata-reviewer | 6/10 (G3) | REVISE |
| 2 | 2026-11-07 | architecture-kata-reviewer (same session as revision, provisional) | 8/10 | ACCEPT |
```
