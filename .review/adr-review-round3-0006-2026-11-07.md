# ADR review — round 3 — ADR-0006 — 2026-11-07

Reviewer: `architecture-kata-reviewer` (ADR mode). Pass mark 7, target 8.

> **Independence:** same session as the revision and as round 2. The score is provisional until a fresh-session run confirms it.

Target: `docs/adr/0006-realm-as-code-secrets-as-placeholders.md` after the round 2 fixes. Previous round: `.review/adr-review-round2-0006-2026-11-07.md` (8.0/10, ACCEPT).

---

## ADR 0006 — Realm as code, secrets as `${...}` placeholders — 9.5/10 — ACCEPT

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

### Fact-check (new or changed claims)

| Claim | Where | Result |
|---|---|---|
| Keycloak `8081` published on all interfaces; apps set no `server.address` | `docker-compose.yml:31`; `backend/*/application.properties` | ✓ |
| Gateway's only redirect URI and every `issuer-uri` are `localhost` | `secure-shop-realm.json:797-798`; four `application.properties` | ✓ |
| Only two clients have direct grants: `admin-cli` (public) and `secure-shop-test-client` (confidential, has `secure-shop-audience`) | `secure-shop-realm.json:670-674, 871, 897` | ✓ |
| `admin-cli` lacks `secure-shop-audience` | `secure-shop-realm.json:684-690` | ✓ |
| Step 4 binds Keycloak's port to localhost (T27) | `risk-register.md`, T27 "port bound to localhost" | ✓ (planned, attributed to the step) |
| Collection `password`/`adminPassword` = `test`; `README.md:46`, `docs/development.md:100` | files | ✓ |
| Second verification grep runs and finds the collection and docs | ran it | ✓, but it also lists the curl docs, which hold `<testuser password>` placeholders, not the value. "Every other file that carries the fixture password" is slightly loose |
| `"temporary": true` breaks the password grant | Keycloak required action blocks direct grants ("Account is not fully set up") | ✓ |
| Scanner described as planned, custom rule needed | Verification | ✓ (round 2 overclaim fixed) |
| Newman | removed | ✓ |

### Rubric

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Unchanged from round 2: two kinds of credential, a definition of secret, T43 in Drivers. | — |
| Options | 2/2 | New seeded-users table with four options. The placeholder runner-up is steelmanned ("same mechanism as the client secrets; nothing usable in git") and rejected on a real cost. `temporary` and a setup script cover what a reviewer would raise next. | — |
| Decision & rationale | 2/2 | "Fixtures beat placeholders here because the password is shared" names the separating criterion, and the localhost limits are stated honestly as "incidental, not designed", with the step that makes them by configuration. One-minute answer matches (six sentences, on the long side). | — |
| Consequences & risk | 2/2 | T43 bullet concrete and scored; exit condition stated; costs still the strongest part. | — |
| Accuracy & verifiability | 1.5/2 | All claims verified, and the round 2 errors are fixed. New gap: the LAN safety now rests on one incidental fact, `admin-cli` not having `secure-shop-audience`, and nothing checks it. The realm already puts `orders:write`/`orders:read` in `defaultDefaultClientScopes` (`:1814-1815`), so a later "make audience a default scope" edit would silently give LAN-reachable admin tokens. Second grep wording slightly loose. | Add a verification line: `admin-cli`'s `defaultClientScopes` don't include `secure-shop-audience`, and `secure-shop-audience` is not in `defaultDefaultClientScopes`. Reword the grep line to "lists every file that names the fixture users". |

**Total: 9.5/10** (pass 7, target 8). Normal impact. **ACCEPT** (provisional until a fresh-session run).

Optional change for 10: the `admin-cli` audience check above.

### Delta vs round 2 (2026-11-07, 8.0/10)

| Dimension | Round 2 | Round 3 | Δ | Why |
|---|---|---|---|---|
| Context & drivers | 2.0 | 2.0 | 0 | Unchanged |
| Options | 1.5 | 2.0 | +0.5 | Seeded-user options table added |
| Decision & rationale | 1.5 | 2.0 | +0.5 | Shared-fixture reason beats placeholders; limits stated as incidental |
| Consequences & risk | 2.0 | 2.0 | 0 | Unchanged |
| Accuracy & verifiability | 1.0 | 1.5 | +0.5 | Localhost claim, scanner, Newman fixed; collection/docs in verification; users in migration path. New: incidental `admin-cli` limit unchecked |
| **Total** | **8.0** | **9.5** | **+1.5** | |

### Interviewer's objection

"Your own ADR says the only thing stopping a LAN neighbour from getting a working admin token is that `admin-cli` is missing one client scope. Your realm already makes `orders:write` a default scope for every client. What stops someone adding `secure-shop-audience` there next month, and what would catch it?"

Why this is the weak point: the ADR is honest that the limit is incidental, but honest isn't enforced. A one-line realm edit, reviewed in a 2,900-line noisy file, turns T43 into a LAN-wide admin login, and no check fails.

What a strong answer would contain:
- A check today: a grep or `jq` over the realm file asserting `admin-cli` and `defaultDefaultClientScopes` lack `secure-shop-audience`, run with the other verification lines (and in CI with step 3)
- Or remove the dependency: disable direct grants on `admin-cli` in this realm (nothing in the project uses it; the User Service uses its own client)
- The structural fix already planned: step 4 binds Keycloak to localhost (T27)

---

## Review record

```
| 1 | 2026-10-24 | architecture-kata-reviewer | 6/10 (G3) | REVISE |
| 2 | 2026-11-07 | architecture-kata-reviewer (same session as revision, provisional) | 8/10 | ACCEPT |
| 3 | 2026-11-07 | architecture-kata-reviewer (same session as revision, provisional) | 9.5/10 | ACCEPT |
```
