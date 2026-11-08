# ADR review — round 1 — ADR-0013 — 2026-11-08

Reviewer: `architecture-kata-reviewer` (ADR mode). Pass mark 7, target 8 (from `docs/adr/README.md`).

> **Independence:** fresh session (started after `/clear`). This session did not draft or edit the ADR, so the score can gate acceptance.

Target: `docs/adr/0013-accept-unencrypted-pii-at-rest.md`, working tree (uncommitted revision on `threat-model`: +17/−3 lines adding the Keycloak copy, the in-transit scope note, existing controls, the unencrypted laptop disk, and four verification bullets).

Earlier batch reviews of this ADR (not in its review record; see F5):

- 2026-10-24 round 0: 9/10, ACCEPT (same session as drafting, baseline) (`.review/adr-review-round0-2026-10-24.md`)
- 2026-10-24 round 1: 9.0/10, ACCEPT (`.review/adr-review-round1-2026-10-24.md`)

## 2026-10-24 findings: status

| # | Finding (2026-10-24 round 1) | Status | Where |
|---|---|---|---|
| 1 | One-minute answer: third sentence is a future plan, the main cost is missing | ✗ Not done | Text unchanged; cap still applies |
| 2 | Keycloak's H2 holds the same usernames/emails, unencrypted (T35), scope stops at `profiles` | ✓ Done | New Context paragraph, Decision line "same acceptance covers…", Negative bullet, Verification bullets |
| 3 | Objection: open `POST /users/register` lets real data in before the trigger notices; strong answer needs an observable check | ~ Partly | New trigger "Any real personal data in either folder on this laptop", but still no observable check |

Also new: in-transit explicitly out of scope with a link to 0014 (T37), "Controls that exist today" paragraph, the decider's check that the laptop disk is unencrypted.

The score drops from 9.0 to 8.0. Not a penalty for the revision: the new laptop fact makes the disk encryption option look nearly free, and the ADR never says why it isn't taken (F1). The new log claim is also too strong (F2).

---

## ADR 0013 — Accepted risk: profile PII unencrypted at rest (T33) — 8.0/10 — ACCEPT

Gates: G1 ✓ G2 ✓ G3 ✓ (the log claim in F2 is wrong but doesn't affect the decision) G4 ✓ G5 ✓

### Fact-check

| Claim | Where | Result |
|---|---|---|
| `profiles`: `username`, `email`, `full_name`, `address`, `phone` plain `VARCHAR` | `backend/user-service/src/main/resources/db/migration/V1__create_profiles_table.sql` | ✓ (`username` also `UNIQUE`, which matters for F2) |
| Postgres data on bind mount `./postgres-data` | `docker/docker-compose.yml:13` | ✓ |
| Keycloak H2 on bind mount `./keycloak-data` | `docker/docker-compose.yml:33` | ✓ |
| Both folders gitignored, `git check-ignore` confirms | `.gitignore:1-2`; `git check-ignore -v` matches both | ✓ |
| Registration sends `username`, `email`, password to Keycloak (not `full_name`, `address`, `phone`) | `KeycloakAdminClient.createUser` body map | ✓ |
| `spring.jpa.show-sql=true` prints `?` placeholders, no bound values | `user-service/.../application.properties:11`; no `org.hibernate.orm.jdbc.bind` trace level set | ✓ |
| "Logs carry only the `keycloak_sub`, never profile fields" | `ProfileService.compensate` logs `cause` with its stack trace | ✗ overstated, see F2 |
| T33 = 2/1/2/3/1 = 9 Medium, review 2027-04-24 | `docs/threat-model/risk-register.md:63` | ✓ |
| T35 = 10 Medium, steps 3 → 4 | `risk-register.md:65` | ✓ |
| Step 2 brings per-service role and no default superuser password | `risk-register.md:62` (T32: "2 (credentials + per-service roles, finding #6)") | ✓ |
| JDBC/HTTP plain text accepted in 0014 (T37) | `docs/adr/0014-accept-no-tls-local-rnd.md:11,17,48` | ✓ consistent |
| Laptop disk unencrypted (Device encryption off) | Decider's manual check, 2026-11-08 | Not verified by the reviewer (needs the Settings UI or admin `manage-bde`); taken as stated |
| Index row | `docs/adr/README.md`: title, `security`, `normal`, `Proposed` match; score blank | ✓ |

### Rubric

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Exact columns and location, both copies (Postgres, Keycloak H2), in-transit excluded with a link to 0014, driver traced to T33 with the DREAD breakdown. The central argument is specific: "encryption at rest only helps against someone reading the disk or a backup, not against someone reading through SQL". | — |
| Options | 2/2 | Four real options, each with honest cons (key rotation and no search for `AttributeConverter`; keys in SQL logs for `pgcrypto`). Disk encryption is now presented so well ("also covers `docker/keycloak-data/` and a lost or stolen machine") that choosing it would look reasonable. That meets the 2 bar, and it is also what exposes the gap in Decision. | Optional: put disk encryption's real cost on this laptop in its Cons (recovery key handling, Windows 11 Home Device encryption depends on hardware support, it's a per-machine setting the repo can't enforce for other people who clone it). That makes F1 easy to answer. |
| Decision & rationale | 1/2 | **One-minute answer cap (1.5):** sentence 3 ("Before any real data, storage encryption is the default…") is still the migration plan; the main cost ("a copied data folder, backup or lost laptop exposes the PII") is absent. **Below the cap:** the rationale beats column encryption and `pgcrypto` (step 2 access control reduces more risk, "only test data"), but not the runner-up. Disk encryption on the laptop is described as covering both folders and the lost-laptop case, the Negative says the disk is unencrypted, and nothing says why turning it on is not part of the decision. "Effort goes to database access control first" assumes the two compete for effort; a toggle doesn't. | Replace one-minute sentence 3 with the cost. Add one sentence in Decision outcome saying why host disk encryption is not taken now (or take it: see F1). |
| Consequences & risk | 1.5/2 | Strong and specific: what each folder exposes, the unencrypted laptop, GDPR-style unacceptability for real users. **Scope blur:** the Negative accepts "a copy of `docker/keycloak-data/` exposes usernames, emails and password hashes" and the lost-laptop path "with the rest", but password hashes are T35 (Open, fixed by steps 3 → 4). Neither secret scanning nor Docker secrets fixes a stolen laptop. So 0013 silently accepts part of T35, and the register doesn't say so. Log and Postgres-server-log copies of profile values are missing (F2). | Say "the password-hash half of this exposure is T35, still Open; this ADR accepts only the username/email copy", or add to T35's note in the register that its lost-machine path is accepted via 0013. |
| Accuracy & verifiability | 1.5/2 | Most claims check out (table above), verification is concrete (files, `git check-ignore`, the dated disk check), triggers and review date present. **Wrong claim:** "Logs carry only the `keycloak_sub`, never profile fields" (Decision) and "log only the `keycloak_sub`" (Verification). See F2. Review record empty although two rounds happened (F5). | Narrow the log claim to what is true, or fix the logging. Fill in the review record. |

**Total: 2 + 2 + 1 + 1.5 + 1.5 = 8.0/10 → ACCEPT** (meets the target). `Impact: normal`, so no defense needed.

### Findings

- **F1. The near-free mitigation is neither taken nor rejected.** The new text says the laptop disk is unencrypted and that disk encryption would cover both folders and a lost machine. The only reason not to turn it on is implicit ("only test data"). Since this is an accepted-risk ADR, a reader will ask why the risk is accepted when the fix is a Settings toggle. One sentence closes it, in either direction: reject it with a cost (recovery key escrow to a Microsoft account, it's host hygiene that the repo can't enforce or verify for other people who clone it, nothing to protect yet), or take it and move it to "Controls that exist today". This is the same pattern as F1 in 0012's review (backup named as cheap, left open).
- **F2. The log claim is too strong: profile values can reach the logs.** `ProfileService.compensate` logs `cause` (the profile write failure) with its stack trace. The realistic path is already documented in `docs/development.md:156-160`: wipe only `docker/keycloak-data/`, then re-register an existing username. Keycloak accepts it, the `profiles` insert fails on `username UNIQUE`, and the PgJDBC message carries Postgres's `Detail: Key (username)=(alice) already exists.` (on by default, `logServerErrorDetail=true`). Hibernate's `SqlExceptionHelper` logs the same message at ERROR on its own. Postgres also writes the error with `DETAIL` to its server log (`docker logs`), a third unencrypted copy outside both folders. Only usernames, not addresses or phones, and stdout only (no `logging.file`), so it doesn't change the decision. But the sentence as written is false. Library behaviour, not run.
- **F3. One-minute answer still has no cost.** Carried over from 2026-10-24. Suggested sentence 3: "The cost: anyone who copies either data folder, a backup, or this laptop can read every profile."
- **F4. Part of T35 gets accepted here without the register knowing.** See Consequences. Smallest fix: scope the password-hash exposure back to T35 in the Negative bullet, or update T35's note.
- **F5. Review record is empty although two rounds happened.** 2026-10-24 rounds 0 (9) and 1 (9.0) are in `.review/` but not in the ADR. Add them so this round reads as round 2 and the drop to 8.0 is traceable.
- **F6 (minor). The open-registration objection from 2026-10-24 still has no observable check.** The triggers are events ("any real personal data") that nothing detects. One line would do it, e.g. a README note "this stack accepts test data only, see ADR-0013", or a pointer to the step 4 change that binds ports to localhost.

### Optional changes (already at target; in order of points gained)

1. F1 + F3: one sentence on why disk encryption isn't taken, and the cost in the one-minute answer → Decision 1 → 2 (+1.0)
2. F2: narrow the log claim, e.g. "the app's own log messages carry only the `keycloak_sub`; a failed profile insert can log the username through the exception detail" → Accuracy +0.5 (with F5)
3. F4: scope password hashes back to T35 → Consequences +0.5

All three → 10/10.

## Interviewer's objection

"Your own ADR says the laptop disk is unencrypted, and that turning on disk encryption would cover both data folders and a stolen machine. That's one toggle. Why is this an accepted risk and not a fixed one?"

Why this is the weak point: the whole rationale is prioritisation ("step 2 access control reduces more risk"), but a free control doesn't compete for effort with step 2. An accepted-risk ADR that lists a free mitigation and doesn't take it reads as an oversight, not as a trade-off. An architecture board would send it back on that one line.

What a strong answer would contain:

- The real cost of the toggle on this machine: recovery key escrow (Device encryption on Windows Home backs the key up to a Microsoft account), lockout risk, hardware requirements
- Why it's out of the repo's scope: host hygiene the project can't enforce or verify for anyone else who clones it. The ADR decides what the *system* does at rest, and the laptop is the operator's
- Value today ≈ zero: seeded and test registrations only. The trigger "any real data in either folder → disk encryption first" already orders it before anything else
- Or concede: turn it on now, record it under "Controls that exist today", and the residual risk drops to the SQL-reader path the ADR already argues about

Answer it if you want to drill; I'll evaluate it like an interviewer.

---

## Review record (paste at the bottom of the ADR)

```
## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 0 | 2026-10-24 | architecture-kata-reviewer (same session as drafting, baseline) | 9/10 | ACCEPT |
| 1 | 2026-10-24 | architecture-kata-reviewer | 9.0/10 | ACCEPT |
| 2 | 2026-11-08 | architecture-kata-reviewer | 8.0/10 | ACCEPT |
```

Index: Score column for 0013 → `8`.

---

## Drill — 2026-11-08

Objection: "One toggle fixes both folders and the stolen-laptop case. Why accepted, not fixed?"

Answer (Fahim): only test data, used only by the decider; a stolen laptop harms no real user; no production plans, so the risk is accepted.

Evaluation: **holds on impact.** Harm ≈ zero is the right axis, and the revisit triggers cover the day it changes. Gap: the objection was about the *cost* of a free fix, not the size of the risk. A complete answer adds "benefit is zero, so even a small cost (recovery key escrow, lockout, a host setting the repo can't enforce) isn't worth paying". The answer was not yet in the ADR. Not pressed further; `Impact: normal`, so no defense is required.

---

## Re-review — 2026-11-08 (after drill)

> **Independence:** this session made the edit below (one sentence, wording suggested in the drill). Re-run in a fresh session if this score has to gate acceptance.

Change: one sentence added to Decision outcome:

> Host disk encryption is not turned on for this: the laptop holds only test data, so it protects nothing today, and it's a machine setting outside what this repo decides. The revisit trigger turns it on first, before any real data.

Consistent with the revisit trigger "Any real personal data in either folder on this laptop: turning on disk encryption comes first".

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Before | Now | Delta | Why |
|---|---|---|---|---|
| Context & drivers | 2 | 2 | — | Unchanged |
| Options | 2 | 2 | — | Unchanged |
| Decision & rationale | 1 | 1.5 | +0.5 | F1 closed: runner-up (disk encryption) now rejected on stated criteria (zero value today, outside the repo's scope) with an ordering for later. Still capped at 1.5: one-minute answer has no cost (F3) |
| Consequences & risk | 1.5 | 1.5 | — | F4 open (password hashes are T35) |
| Accuracy & verifiability | 1.5 | 1.5 | — | F2 (log claim), F5 (empty review record) open |

**Total: 8.5/10 → ACCEPT.**

Open, optional (in order of points gained):

1. F3: one-minute answer sentence 3 → the cost ("anyone who copies either data folder, a backup, or this laptop can read every profile") → +0.5
2. F2 + F5: narrow the log claim; fill in the review record → +0.5
3. F4: scope password hashes back to T35 → +0.5

Updated review record:

```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 0 | 2026-10-24 | architecture-kata-reviewer (same session as drafting, baseline) | 9/10 | ACCEPT |
| 1 | 2026-10-24 | architecture-kata-reviewer | 9.0/10 | ACCEPT |
| 2 | 2026-11-08 | architecture-kata-reviewer | 8.0/10 | ACCEPT |
| 3 | 2026-11-08 | architecture-kata-reviewer (same session as the edit) | 8.5/10 | ACCEPT |
```

Index: Score column for 0013 → `8.5`.

---

## Re-review — 2026-11-08 (after F2–F5)

> **Independence:** this session wrote every change below. The score grades the reviewer's own wording; re-run in a fresh session before this score gates acceptance.

Changes:

- **F3** One-minute answer sentence 3 is now the cost: "anyone who copies either data folder, a backup, or this laptop can read every profile". The migration plan stays in Migration path.
- **F2** "Controls that exist today" narrowed: the app's own log messages carry only the `keycloak_sub`; a failed insert on the `UNIQUE` username logs `Key (username)=(…) already exists` via `ProfileService.compensate`, `SqlExceptionHelper` and the Postgres server log; accepted. Verification bullet reworded to match.
- **F4** Negative bullets: password hashes, secrets and signing keys in `keycloak-data/` scoped back to T35 (Open, not accepted here), both for a copied folder and a stolen laptop.
- **F5** Review record filled with rounds 0–3.

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Round 3 | Now | Delta | Why |
|---|---|---|---|---|
| Context & drivers | 2 | 2 | — | Unchanged |
| Options | 2 | 2 | — | Unchanged |
| Decision & rationale | 1.5 | 2 | +0.5 | One-minute answer now has decision, reason and cost, and matches Decision outcome; cap lifted |
| Consequences & risk | 1.5 | 2 | +0.5 | Scope against T35 explicit; log copy named as residual risk |
| Accuracy & verifiability | 1.5 | 2 | +0.5 | Log claim now true (matches `ProfileService.java:63-73`, PgJDBC/Hibernate default behaviour, not run); review record present |

**Total: 10/10 → ACCEPT.**

Nits (no deduction):

- "only to stdout": the Postgres server log goes to the container's stderr (`docker logs`). "Only to the console" is more exact.
- F6 still open: the revisit triggers are events nothing detects. Not counted against the score, as stated in round 2.

Review record row to add:

```
| 4 | 2026-11-08 | architecture-kata-reviewer (same session as the edit) | 10/10 | ACCEPT |
```

Index: Score column for 0013 → `10`, after a fresh-session confirmation.
