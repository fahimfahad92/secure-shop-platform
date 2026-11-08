# ADR review — round 1 — ADR-0012 — 2026-11-08

Reviewer: `architecture-kata-reviewer` (ADR mode). Pass mark 7, target 8 (from `docs/adr/README.md`).

> **Independence:** fresh session (started after `/clear`). This session did not draft or edit the ADR, so the score can gate acceptance.

Target: `docs/adr/0012-accept-single-keycloak-instance.md`, working tree (uncommitted revision on `threat-model`).

Earlier batch reviews of this ADR (not in its review record; see finding F4):

- 2026-10-24 round 0: 8.5/10, ACCEPT (same session as drafting, baseline) (`.review/adr-review-round0-2026-10-24.md`)
- 2026-10-24 round 1: 8.0/10, ACCEPT (`.review/adr-review-round1-2026-10-24.md`)

## 2026-10-24 findings: status

| # | Finding (2026-10-24 round 1) | Status | Where |
|---|---|---|---|
| 1 | Durability: H2 holds users and signing keys; losing it loses accounts | ✓ Done | New negative "No backup of Keycloak's data", with the orphaned profiles/orders effect and "register again" recovery |
| 2 | A service restarted during the outage fails every request | ✓ Done | New Context bullet (lazy `issuer-uri`), extended manual check |
| 3 | Options: let the Gateway start without discovery; single-node production mode on the existing Postgres | ✗ Not done | Neither appears in Considered options |
| 4 | "Unacceptable when …" | ~ Partly | New revisit trigger "Registered users becoming data someone would miss" |
| 5 | Extend the manual check (restart a service during the outage) | ✓ Done | Verification bullet 2; plus an honest "No automated test" |

Also new: `RouteConfig` named for the `401` mapping, production-mode HTTPS detail (`--http-enabled=true`), migration path no longer claims a step ("No step plans this"), cross-link to 0014.

---

## ADR 0012 — Accepted risk: single Keycloak instance (T30) — 8.5/10 — ACCEPT

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

### Fact-check

| Claim | Where | Result |
|---|---|---|
| One `keycloak` service, `start-dev`, `restart: unless-stopped` | `docker/docker-compose.yml:15-34` (`command: start-dev --import-realm`) | ✓ |
| H2 under `docker/keycloak-data/` | bind mount `./keycloak-data:/opt/keycloak/data` | ✓ |
| `accessTokenLifespan` 300 s | `docker/keycloak/secure-shop-realm.json:8` | ✓ |
| Failed refresh → `ClientAuthorizationException` → `401` | `RouteConfig.java:79-88` (`tokenRelay()`, `onError(ClientAuthorizationException.class, sessionExpired)`) | ✓ |
| Gateway reads discovery at startup | `gateway/.../application.properties:16` `provider.keycloak.issuer-uri`; Boot resolves client registrations from the issuer eagerly | ✓ (Spring Boot behaviour, not run) |
| Services resolve `resourceserver.jwt.issuer-uri` lazily | `issuer-uri` in all three services (`:14`, `:16`, `:16`), no custom `JwtDecoder` bean in `backend/*/src/main/java`; Boot wraps it in a supplier decoder | ✓ (Spring Boot behaviour, not run) |
| T30 = 2/2/2/3/1 = 10 Medium, accepted via ADR-0012, review 2027-04-24 | `docs/threat-model/risk-register.md:60` | ✓ |
| T35 = H2 store holds password hashes and keys | `risk-register.md:65`, `stride/data-stores.md:11` | ✓ (but see F3) |
| Re-import gives seeded users only; registered users gone | `docs/development.md:156-160` | ✓ |
| Production deployment out of scope | ADR-0001:47 ("production IdP … decided in a new ADR at first deployment") | ✓ |
| 0014 lists Keycloak production mode with HTTPS as migration step 1 of 0012 | ADR-0014:85 | ✓ consistent both ways |
| Index row | `docs/adr/README.md`: title, `security`, `normal`, `Proposed` match; score blank | ✓ |

Unverified at runtime (repo-only review): the outage behaviour itself. The ADR says so ("No automated test covers the outage behaviour").

### Findings

- **F1. The "cheap" backup is neither decided nor rejected.** Migration path: "Cheap, independent of HA: a backup of `docker/keycloak-data/` … next to the Postgres one." The Decision accepts the risk "unchanged". A reader can't tell if the backup is part of this decision, deferred, or rejected. An accepted-risk ADR that names a near-free mitigation and leaves it open looks like an unfinished decision.
- **F2. Gateway startup coupling still not weighed.** "The Gateway can't start without Keycloak" is a negative, but the fix (explicit `authorization-uri`, `token-uri`, `jwk-set-uri`, `user-info-uri` instead of `issuer-uri` discovery) is a few properties. It is not in the options and not rejected. Same for single-node production mode on the Postgres that already runs (durability without HA). Both carried over from 2026-10-24.
- **F3. The data-loss risk has no risk ID.** The ADR hangs it on T35, but T35 is *information disclosure* (host files leak), not loss. The durability risk this ADR now accepts is not in `risk-register.md`. Either add a register row (e.g. "Keycloak H2 lost, no backup", D/availability) that points to this ADR, or say explicitly that 0012 accepts it under T30.
- **F4. Review record is empty although two rounds happened.** 2026-10-24 rounds 0 (8.5) and 1 (8.0) are in `.review/` but not in the ADR. The convention says the reviewer's table goes into the ADR's record. Add them, so this round reads as round 2 and the delta is traceable.
- **F5 (minor). Session recovery has a bound you can state.** "Whether their Gateway session recovers … is not tested." The realm has `ssoSessionIdleTimeout: 1800` (`secure-shop-realm.json:10`): an outage longer than 30 minutes ends the SSO session for sure, so re-login is certain then; below that, recovery depends on the refresh succeeding. One line turns "unknown" into "unknown below 30 min".
- **F6 (minor). `restart: unless-stopped` only covers a dead process.** There is no `healthcheck` on the `keycloak` service, so a hung but running JVM is not restarted. The "partial control" is narrower than it reads.
- **F7 (minor). Hosted IdP rejection is thinner than it looks.** "Reverses 0001", but 0001:47 itself names "a hosted IdP" as a candidate for production. For the local phase the rejection holds; say "for the local phase" so it doesn't read as ruling hosted IdPs out of the later production ADR.

### Rubric

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Five concrete outage behaviours, each tied to a property or class (`accessTokenLifespan` 300 s, `RouteConfig` `401`, Gateway `issuer-uri` at startup, lazy service decoder). Driver T30 with its score. Forces ("one laptop, test data, no real users"; HA needs production mode + external DB) are specific. | — |
| Options | 1.5/2 | Accept / cluster / hosted IdP, cluster cons now accurate (hostname, HTTPS, `--http-enabled`). Missing the options a reviewer asks for first: the partial mitigations (Gateway without discovery, single-node production mode on existing Postgres, backup) — F1, F2. | Add a row "Accept + cheap mitigations" (backup, explicit Gateway endpoints) and accept or reject each. |
| Decision & rationale | 1.5/2 | Sound acceptance reasoning; One-minute answer matches Decision (decision, reason, cost). Scope is ambiguous on the backup (F1), and there is no explicit "unacceptable when …" beyond the triggers. | State whether the backup is in or out of this decision, with one line why. |
| Consequences & risk | 2/2 | Costs are the strongest part now: `401` within 5 min with the untested recovery stated, Gateway/Keycloak restart coupling, H2 not a config change away from HA, and the data-loss path with orphaned Postgres rows and the recovery cost. | Optional: F5 (30-min bound), F6 (no healthcheck). |
| Accuracy & verifiability | 1.5/2 | Every code claim checked ✓. Revisit triggers and review date present; manual check extended; lack of automated test stated. Deductions: the data-loss risk is pinned to a risk ID that describes something else (F3); review record missing prior rounds (F4); verification is manual only. | Fix F3 and F4. Optional: a scripted outage check (stop Keycloak, curl a relay route after 300 s, restart a service) in `docs/` or a script. |

**Total: 8.5/10 — ACCEPT** (Impact `normal`, no defense needed). Delta vs 2026-10-24 round 1 (8.0): Consequences +0.5 (durability, restart), Context 0, Options 0, Decision 0, Accuracy 0 (gained the restart check, lost on F3/F4).

Optional changes toward a higher score, in order of points gained:

1. Decide the cheap mitigations (F1 + F2): one options row, accepted or rejected each. (+0.5 Options, +0.5 Decision)
2. Give the data-loss risk its own register ID or fold it into T30 explicitly; fill the review record with the 2026-10-24 rounds (F3, F4). (+0.5 Accuracy)
3. F5, F6, F7 one-liners.

## Interviewer's objection

"You reject HA because it's a project of its own. Fine. But your own ADR says a backup is cheap, and making the Gateway start without Keycloak is four properties. Why are you accepting the *full* risk when part of it costs almost nothing to remove?"

Why this is the weak point: an accepted-risk ADR is judged on whether the accepted part is the irreducible part. This one lists two near-free mitigations (one in its own migration path) and decides neither, so "residual risk unchanged" looks like inertia, not a trade-off.

What a strong answer would contain:

- Backup: either take it now (a `docker/keycloak-data/` copy or `kc.sh export --users realm_file` next to the Postgres dump) and lower the data-loss part of the risk, or reject it with a reason (test data, re-register is cheaper than maintaining a backup, and the realm-as-code export of 0006 already restores the configuration)
- Gateway discovery: explicit endpoints let the Gateway start, but it still can't log anyone in or refresh, so the user-visible outage is the same; the gain is restart independence only, and the cost is drift between hard-coded URIs and the realm. Reject or accept on that
- Single-node production mode on existing Postgres: buys durability, costs the hostname/HTTPS setup that 0014 defers; tied to 0014's trigger, so deferring both together is consistent
- The resulting statement of which part of T30 is accepted and which part was removed

Answer the objection and I'll evaluate it like an interviewer.

## Drill (2026-11-08)

Not required (Impact `normal`). The user's answer: one user, no plan to go live, little user data; uptime and working through a Keycloak outage are out of scope; when the project doesn't start, fixing it is part of the R&D goal.

Evaluation: **holds.** Landed: availability out of scope rejects HA and the Gateway change together; little data makes "register again" the full recovery cost; a Gateway that fails without Keycloak exposes a real dependency worth learning, and explicit endpoints would hide it and drift from the realm. Not yet in the ADR: the rejection of each cheap fix (F1, F2 stay open until it is written down), and the phrasing must make clear that startup failure is wanted, not a cost. With an options row and one Decision sentence: about 9.5.

## Review record

Recorded in the ADR on 2026-11-08 (round numbering follows ADR-0011: earlier batch rounds as `0` and `1 (pre)`). Fixes F4.

```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 0 | 2026-10-24 | architecture-kata-reviewer (same session as drafting, baseline) | 8.5/10 | ACCEPT |
| 1 (pre) | 2026-10-24 | architecture-kata-reviewer | 8/10 | ACCEPT |
| 1 | 2026-11-08 | architecture-kata-reviewer | 8.5/10 | ACCEPT. ... |
| 1 (drill) | 2026-11-08 | architecture-kata-reviewer (drill) | 8.5/10 | ACCEPT. ... |
```

Index score: 8.5.
