# ADR review — round 5 — ADR-0013 — 2026-11-08

Reviewer: `architecture-kata-reviewer` (ADR mode). Pass mark 7, target 8 (from `docs/adr/README.md`).

> **Independence:** fresh session (started after `/clear`). This session did not draft or edit the ADR, so this score can gate acceptance. Purpose: confirm or correct rounds 3 (8.5) and 4 (10), which were scored in the session that made the edits.

Target: `docs/adr/0013-accept-unencrypted-pii-at-rest.md` at `14944f8` (working tree clean).

Scored from scratch. Earlier rounds were read only afterwards, for the delta table.

---

## ADR 0013 — Accepted risk: profile PII unencrypted at rest (T33) — 9.5/10 — ACCEPT

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

### Fact-check

| Claim | Where | Result |
|---|---|---|
| `profiles`: `username`, `email`, `full_name`, `address`, `phone` plain `VARCHAR`; `username` `UNIQUE` | `backend/user-service/src/main/resources/db/migration/V1__create_profiles_table.sql` | ✓ |
| Postgres data on bind mount `./postgres-data` | `docker/docker-compose.yml:13` | ✓ |
| Keycloak data in H2 under `./keycloak-data` | `docker/docker-compose.yml:20,33` (`start-dev`, no `KC_DB`, so dev-file H2) | ✓ |
| Both folders gitignored, `git check-ignore` confirms | `.gitignore:1-2`; `git check-ignore -v` matches both | ✓ |
| Registration sends `username`, `email`, password to Keycloak | `KeycloakAdminClient.java:56-63` | ✓ (not `full_name`/`address`/`phone`) |
| `spring.jpa.show-sql=true`, placeholders only | `user-service/.../application.properties`; no `logging.*` or bind-trace config | ✓ |
| App log messages carry only `keycloak_sub` | `ProfileService.java:63-72`, `KeycloakAdminClient.java:126`; no other `log.*` in user-service | ✓ |
| `compensate` logs the exception, which can carry the username | `ProfileService.java:63-66` passes `cause` | ✓ |
| `Key (username)=(…) already exists` via PgJDBC detail, `SqlExceptionHelper`, Postgres server log | Library defaults (`logServerErrorDetail=true`, Hibernate ERROR log, Postgres `log_min_error_statement=error` to stderr) | ✓ plausible, not run. See N2 for a fourth path |
| `UsernameAlreadyTakenException` (Keycloak 409) carries the username, not logged | `GlobalExceptionHandler` returns it in the body, no log call | ✓ (not a log copy; consistent with the ADR) |
| T33 = 2/1/2/3/1 = 9 Medium (D2/R1/E2/A3/D1), review 2027-04-24 | `docs/threat-model/risk-register.md:63` | ✓ |
| T35 = 10 Medium, Open, steps 3 → 4 | `risk-register.md:65` | ✓ |
| T31/T32 fixed in step 2 (per-service role, credentials) | `risk-register.md:61-62` | ✓ (T32 continues to step 4 for the port; ADR doesn't claim otherwise) |
| In-transit accepted in 0014 (T37) | `docs/adr/0014-accept-no-tls-local-rnd.md` | ✓ |
| Laptop is Windows 11 Home, Device encryption off | Environment reports Windows 11 Home; Device encryption state needs the Settings UI or admin `manage-bde` | OS ✓; encryption state not verified by the reviewer, taken as the decider's dated check |
| Index row | `docs/adr/README.md`: title, `security`, `normal`, `Proposed` match; Score `8` | ✓ deliberately held at 8 pending this round (commit `14944f8`) |

No claim fails. G3 passes.

### Rubric

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Exact table, columns and folder; both copies (Postgres and Keycloak H2) with the split against T35; in-transit excluded with a link to 0014; driver traced to T33 with its score. The core argument is specific to this system: "Encryption at rest only helps against someone reading the disk or a backup, not against someone reading through SQL", and the larger gap today is T31/T32. | — |
| Options | 1.5/2 | Four real options with fair cons: disk encryption steelmanned (covers `keycloak-data/` and a stolen laptop), `AttributeConverter` costs named (rotation, no search), `pgcrypto` keys in SQL logs. The runner-up is presented well enough that choosing it would look reasonable. **Missing:** the option a privacy reviewer suggests first, *store less*. `address` and `phone` are stored and returned by User Service, but no other service reads them (no match in order- or product-service). Not collecting them, or making them optional and empty in seed data, is the only control that removes data from both a copied folder and a SQL reader. It may well be rejected (profile feature from #01, test data only), but it isn't weighed. Round 2 deducted for the same pattern with disk encryption. | Add a row "Data minimization: don't store `address`/`phone` until a feature reads them" with its real cost (changes the #01 profile API and the frontend form; out of this ADR's scope; nothing to protect today), and one clause in Decision outcome rejecting it. |
| Decision & rationale | 2/2 | Criteria explicit (only test data, at-rest vs SQL readers, step 2 reduces more risk). Each alternative rejected on them, including the runner-up: "Host disk encryption is not turned on for this: the laptop holds only test data, so it protects nothing today, and it's a machine setting outside what this repo decides." Scope clear (Postgres + Keycloak username/email; hashes and secrets stay T35). One-minute answer has decision, reason and cost, and matches Decision outcome. | — |
| Consequences & risk | 2/2 | Costs are the strongest part: what each folder exposes, the T35 half explicitly not accepted, the unencrypted laptop checked and dated, the log copy of the username named as residual, residual score stated unchanged, and what makes it unacceptable (real users, GDPR-style rules). | Optional, no deduction: see N1 (backup wording vs 0012). |
| Accuracy & verifiability | 2/2 | Every code claim checks out (table above). Verification names files, a command (`git check-ignore`) and a dated manual check. Three revisit triggers, the laptop one ordered ("disk encryption comes first"), review date 2027-04-24, migration path, review record present. | Optional, no deduction: N2 (fourth log path), N3 (triggers nothing detects, F6 carried). |

**Total: 2 + 1.5 + 2 + 2 + 2 = 9.5/10 → ACCEPT** (above target). `Impact: normal`, no defense required.

### Delta against round 4 (same-session 10/10)

| Dimension | Round 4 | Round 5 | Delta | Why |
|---|---|---|---|---|
| Context & drivers | 2 | 2 | — | Confirmed |
| Options | 2 | 1.5 | −0.5 | New finding F1 (data minimization not weighed). Not raised in any earlier round; not a misread of round 4, a gap it missed |
| Decision & rationale | 2 | 2 | — | Confirmed: one-minute answer and runner-up rejection both hold on a cold read |
| Consequences & risk | 2 | 2 | — | Confirmed |
| Accuracy & verifiability | 2 | 2 | — | Confirmed: round 4's narrowed log claim is true against `ProfileService.java` |

**Round 4's 10/10 is corrected to 9.5.** Rounds 3–4 fixes all hold; the half point is a new gap, not drift from the edits. The same-session score was close: independence cost half a point, not more.

### Findings

- **F1. Data minimization not weighed (−0.5, Options).** See rubric. The ADR treats the five fields as given. For an accepted-risk ADR on PII, "keep less" is the classic alternative and the only one that also helps against SQL readers without key management. One row plus one rejecting clause restores the point. Rejecting it is easy and honest: test data only, the field set belongs to the #01 profile feature, and changing it is an API change outside this ADR.

### Nits (no deduction)

- **N1. "A backup" vs ADR-0012.** 0013 names backups as an exposure path three times (Options pro "covers backups", one-minute cost, Negative bullet). ADR-0012 rejects backups of `keycloak-data/` on purpose, and the project has no Postgres backup either. Not wrong as a general statement, but read together it describes a copy that doesn't exist. Either say "a backup, if one is ever made (none today, see 0012)" or drop "backup" from the one-minute answer.
- **N2. Fourth log path for the username.** `ProfileService.register` rethrows the `DataIntegrityViolationException`; no `@ExceptionHandler` catches it, so the embedded Tomcat (`StandardWrapperValve`, "Servlet.service() … threw exception … with root cause") logs it again at ERROR with the same `Detail`. Same data (username), same console, so the "accepted with the rest" sentence still covers it; the "through X, Y and Z" list is just incomplete. Library behaviour, not run. The 500 response body doesn't leak it (Spring Boot default `server.error.include-message=never`).
- **N3. Revisit triggers are events nothing detects** (F6 from round 2, carried). With one user who is also the decider and no plan to go live, this is fine; not counted.
- **N4. Deployment-shaped wording.** "Accept the risk until a real deployment", the "deployment outside a local machine" trigger and the GDPR line assume a go-live that 0012 and the project's stance rule out. Harmless as triggers; not counted. If you tidy N1, consider framing the problem statement as "while this stays a local, test-data-only project".

## Interviewer's objection

"You accept that anyone who copies the data folder reads every address and phone number. But nothing in the system reads those two fields except the profile endpoint that returns them. Why store them at all? Not storing data is cheaper than any encryption option in your table."

Why this is the weak point: the options table is all about *how to protect* five fields and never asks *whether to keep* them. Minimization has no key management, no search limits and works against SQL readers too, so it beats every option on the ADR's own cost criteria. An architecture board would ask it first.

What a strong answer would contain:

- The fields belong to the #01 profile feature (a shop profile with a shipping address); removing them is a product/API change, out of an at-rest risk ADR's scope
- Value today ≈ zero, same as disk encryption: test data only, one user
- The migration path's later ADR (column encryption "only for fields that need it") is where minimization gets decided, field by field: which fields a real feature reads, which get encrypted, which get dropped
- Or concede: make `address`/`phone` optional, leave them empty in seed data, and the residual exposure shrinks to username, email and name

Answer it if you want to drill; I'll evaluate it like an interviewer.

---

## Review record (paste at the bottom of the ADR)

```
| 5 | 2026-11-08 | architecture-kata-reviewer (fresh session) | 9.5/10 | ACCEPT. Confirms rounds 3–4 fixes; round 4's 10 corrected to 9.5: data minimization (`address`/`phone` read by nothing else) not weighed as an option. Nits: "backup" vs 0012's no-backup decision; Tomcat logs the rethrown constraint violation too |
```

Index: Score column for 0013 → `9.5`.

Optional path to 10: F1 (one options row + one rejecting clause in Decision outcome).
