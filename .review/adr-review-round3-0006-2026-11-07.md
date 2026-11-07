# ADR review — round 3 — ADR-0006 — 2026-11-07

Reviewer: `architecture-kata-reviewer` (ADR mode). Pass mark 7, target 8.

> **Independence:** graded in a fresh session that did not draft or edit the ADR. This replaces the provisional same-session round 3 score (9.5/10); the review is the same round, so no new round is added.

Target: `docs/adr/0006-realm-as-code-secrets-as-placeholders.md` after the round 2 fixes. Previous round: `.review/adr-review-round2-0006-2026-11-07.md` (8.0/10, ACCEPT).

---

## ADR 0006 — Realm as code, secrets as `${...}` placeholders — 9/10 — ACCEPT

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

### Fact-check

| Claim | Where | Result |
|---|---|---|
| Three client secrets are `${...}` placeholders | `secure-shop-realm.json:796, 859, 989` | ✓ |
| Compose refuses to start without them (`${VAR:?set it in docker/.env}`) | `docker-compose.yml:27-29` | ✓ |
| `docker/.env` and `docker/keycloak-data/` gitignored; `.env.example` lists the variables | `.gitignore:2, 4`; `docker/.env.example` | ✓ |
| Two password credentials at lines 492 and 505, `"value": "test"`, `"temporary": false`; `adminuser` has `admin` | `secure-shop-realm.json:485-507` | ✓ |
| Keycloak `8081` published on all interfaces; apps set no `server.address` | `docker-compose.yml` `"8081:8080"`; `backend/*/application.properties` | ✓ |
| Gateway's only redirect URI and every `issuer-uri` are `localhost` | `secure-shop-realm.json:797-798`; four `application.properties` | ✓ |
| `admin-cli` public, direct grants on, no `secure-shop-audience` | `secure-shop-realm.json:659-690` | ✓ |
| `secure-shop-test-client` confidential with direct grants and `secure-shop-audience`; Gateway direct grants off | `secure-shop-realm.json:808, 871, 873, 897` | ✓ |
| Every service checks audience (T10) | `jwt.audiences=` in order, product, user `application.properties` | ✓ |
| `defaultDefaultClientScopes` already holds `orders:write`/`orders:read` | `secure-shop-realm.json:1805-1816` | ✓ |
| `bruteForceProtected: false`, `revokeRefreshToken: false` | `secure-shop-realm.json:38, 6` | ✓ |
| File about 2,900 lines | 2,862 lines | ✓ |
| T26, T27, T35, T43 owners and steps | `docs/threat-model/risk-register.md` | ✓ |
| Wiping `keycloak-data/` loses registered users | `docs/development.md:160` | ✓ |
| Second grep "lists every other file that carries the fixture password" | ran it | ~ Also lists the curl docs (`<testuser password>` placeholders) and `user-service-curl.md` (janedoe passwords). Wording loose |

### Rubric

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Two kinds of credential, a definition of secret, T26/T35/T43 in Drivers. A reader knows why it had to be decided. | — |
| Options | 2/2 | Two tables. Runner-ups steelmanned: the declarative tool ("idempotent… small, readable config") and password placeholders ("same mechanism as the client secrets; nothing usable in git"). `temporary` and a setup script cover the next questions. | — |
| Decision & rationale | 1.5/2 | "Fixtures beat placeholders here because the password is shared" is a real criterion, and the LAN limits are honestly "incidental, not designed". Gap: the driver says "No usable secret in the repository, except dev-only fixtures this ADR names", but `docker-compose.yml` commits `KC_BOOTSTRAP_ADMIN_PASSWORD: admin` and `POSTGRES_PASSWORD: postgres`, unnamed and not scoped out (ADR-0001 owns them, T27). The LAN paragraph argues three limits on `adminuser`/`test` while the master `admin`/`admin` console on the same port gives full realm control from the LAN, so T43's marginal LAN risk is about zero while T27 is open. That is the strongest argument for the decision, and the ADR doesn't make it. | Scope the driver to the realm file ("compose bootstrap and Postgres credentials: ADR-0001, T27"), and add: "on the LAN, `admin`/`admin` (T27) already gives more than `adminuser`/`test`; both close in step 4". |
| Consequences & risk | 2/2 | T43 bullet concrete, scored and owned; exit condition stated; costs are the strongest part. | — |
| Accuracy & verifiability | 1.5/2 | All claims verified. Open: the LAN limit rests on one incidental fact, `admin-cli` not having `secure-shop-audience`, and nothing checks it. The realm already puts `orders:*` in `defaultDefaultClientScopes`, so a later "make audience a default scope" edit would silently give LAN-reachable admin tokens. Second grep wording loose. | Add a verification line: `admin-cli`'s `defaultClientScopes` and `defaultDefaultClientScopes` don't include `secure-shop-audience`. Reword the grep line to "lists every file that names the fixture users". |

**Total: 9/10** (pass 7, target 8). Normal impact, no defense needed. **ACCEPT.**

Optional changes for 10, in order:
1. Scope the driver to the realm file and add the T27 marginal-risk line (+0.5)
2. Add the `admin-cli` audience verification check (+0.5)

### Delta vs round 2 (2026-11-07, 8.0/10)

| Dimension | Round 2 | Round 3 | Δ | Why |
|---|---|---|---|---|
| Context & drivers | 2.0 | 2.0 | 0 | Unchanged |
| Options | 1.5 | 2.0 | +0.5 | Seeded-user options table added |
| Decision & rationale | 1.5 | 1.5 | 0 | Shared-fixture reason and incidental limits added; driver overclaims and the T27 marginal-risk argument is missing |
| Consequences & risk | 2.0 | 2.0 | 0 | Unchanged |
| Accuracy & verifiability | 1.0 | 1.5 | +0.5 | Localhost claim, scanner, Newman fixed; collection/docs in verification; users in migration path. Open: incidental `admin-cli` limit unchecked |
| **Total** | **8.0** | **9.0** | **+1.0** | |

The provisional same-session score was 9.5; the fresh session found the Decision & rationale gap it missed (−0.5).

### Interviewer's objection

"Your ADR spends a paragraph on why a LAN neighbour can't easily use `adminuser`/`test`, yet your committed compose file puts the master `admin`/`admin` console on the same port, on all interfaces. Why does this ADR say 'no usable secret in the repository', and what does T43 add while T27 is open?"

Why this is the weak point: the risk analysis is correct but aimed at the smaller hole. A reader of this ADR alone thinks the LAN exposure is one narrow `admin-cli` path, when anyone on the LAN has full realm admin through the master console. The stated driver also doesn't match what the repository holds.

What a strong answer would contain:
- Scope: this ADR covers the realm file; the compose bootstrap and Postgres credentials are ADR-0001 and T27/T42
- Marginal risk: while T27 is open, T43 adds nothing on the LAN, which supports the fixture choice rather than excusing it
- One fix closes both: step 4 binds Keycloak to localhost and moves the bootstrap admin to `.env`, closing T27, the `admin-cli` path and the LAN part of T43 together
- Optionally, disable direct grants on `admin-cli` in this realm; nothing in the project uses it

---

## Review record

```
| 1 | 2026-10-24 | architecture-kata-reviewer | 6/10 (G3) | REVISE |
| 2 | 2026-11-07 | architecture-kata-reviewer (same session as revision, provisional) | 8/10 | ACCEPT |
| 3 | 2026-11-07 | architecture-kata-reviewer (confirmed in fresh session) | 9/10 | ACCEPT |
```
