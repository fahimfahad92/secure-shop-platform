# ADR review — round 2 — ADR-0001 re-review

- Date: 2026-10-31
- Reviewer: architecture-kata-reviewer (ADR mode)
- Branch: `threat-model` @ `f658d41` plus the uncommitted revision of `docs/adr/0001-keycloak-as-the-only-authorization-server.md` (`git diff`: 27 insertions, 10 deletions)
- Pass mark 7/10, target 8/10 (from `docs/adr/README.md`)
- Independence: this session did not draft or edit ADR-0001 and did not run the 2026-10-31 defense drill. `.review/adr-review-round1-2026-10-24.md` was read only to compute deltas, as requested. The score below comes from the current text alone.
- Fact-check: verified against `docker/docker-compose.yml`, `docker/keycloak/secure-shop-realm.json`, every `application.properties` under `backend/`, the Java sources under `backend/*/src/main`, the three `AbstractIntegrationTest`s, `docs/threat-model/risk-register.md`, ADR-0012, `.github/` and `docs/`. Not verifiable from the repo: Keycloak's Argon2 default and its CNCF status (both are true of upstream Keycloak 26 as far as this reviewer knows, and the decision doesn't depend on them).

## ADR 0001 — Keycloak as the only Authorization Server — 8.0/10 — ACCEPT PENDING DEFENSE

Gates: G1 ✓ G2 ✓ G3 ✓ (two present-tense claims about environments that don't exist, see Accuracy; they soften a residual risk but don't flip the choice) G4 ✓ G5 ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 1.5/2 | Fixed since round 1: `Drivers` now reads "learn OAuth2/OIDC on a real IdP; offline/free local stack; Admin API for registration", with no T-IDs. New problem: the added driver "social login and SSO through identity brokering … planned for later" has no source. Nothing in `docs/`, `README.md` or the threat model plans it, and `identityProviders: []` (realm line 1841). A speculative feature is listed as a force. The Argon2/brute-force bullet holds for every real IdP option, so it argues against SAS and home-grown but doesn't separate Keycloak from the runner-up. That's acceptable, but it's padding. | Drop the social-login driver or link the step that plans it. Optionally say which drivers rule out SAS/home-grown and which ones decide against a hosted IdP. |
| Options | 1.5/2 | Table unchanged since round 1. The hosted IdP con "config lives in a vendor console, harder to commit and review" is still there, even though round 1 flagged it: the Terraform Auth0/Okta providers and the Auth0 Deploy CLI keep a hosted tenant's config in the repo. The hosted row's pros also omit the free dev tenant, which is the version a reviewer would raise. | Correct the con (config-as-code exists, but it is a second toolchain with drift against the console) and add "free dev tenant" to the pros. The offline con carries the rejection on its own. |
| Decision & rationale | 2/2 | New paragraph splits building an AS from running one and names the hosted IdP as "the real runner-up". It then weighs three criteria against saved operations work and states that cost alone doesn't decide. A senior engineer would sign that reasoning. One-minute answer now matches it: "Keycloak builds it for us … the cost is that running it is ours". The "realm in the repo" criterion rests on the weak con above. That's deducted under Options, not counted twice. | — |
| Consequences & risk | 1.5/2 | Fixed: the switching cost is now stated truthfully, with the class list under Negative. Still missing: (1) The rationale says patching is "ours", but there is no owner for it. The image is pinned at `quay.io/keycloak/keycloak:26.5.2` (`docker-compose.yml:16`), `.github/` has no Dependabot config, and no step owns Keycloak upgrades. The main cost the ADR admits to has no plan. (2) The migration cost leaves out the two largest items: the ~2,900-line Keycloak-format realm export (ADR-0006) and the users' password hashes. Moving IdP means importing the hashes or forcing every user to reset; `sub` is not the only thing to remap. (3) The ADR still doesn't say what would make the residual risk unacceptable before the triggers fire. | Add a patching owner (Dependabot `docker` ecosystem on `docker/`, or a step that owns upgrades plus a cadence). Add the realm export and password-hash migration to Negative and Migration path. Add one line starting "unacceptable when …". |
| Accuracy & verifiability | 1.5/2 | Fixed: the issuer gap is stated honestly ("not covered by tests … `issuer-uri` to `""`", confirmed at `AbstractIntegrationTest.java:35/35/42`). The class list is complete enough: `KeycloakRoles`, `KeycloakOidcUserService` and `KeycloakPasswordGrant` exist, and "`KeycloakAdminClient` and its config" covers `KeycloakAdminClientConfig` and `KeycloakAdminProperties`. Other references to Keycloak in `ClientSecretCheck`, `LoginFailureHandler` and the registration ID `keycloak` are naming only. Verified: `issuer-uri` in all four apps, no `JwtEncoder`/`PasswordEncoder`, `bruteForceProtected: false`, no `passwordPolicy` key, `admin`/`admin` bootstrap, DREAD 15/15/10 for T25/T27/T30, review date 2027-04-24 in ADR-0012. **New unsupported claims:** "On a deployment such as ECS, the service scheduler keeps one task running" and "test and production environments run `kc.sh start` (production mode) with an external database". No test or production environment exists, and no ECS or deployment plan exists anywhere in `docs/` or `.github/`. These are policies written as facts, and they are used to soften T30 and T35. ADR-0012, which owns T30, says nothing about ECS (cross-ADR drift). The decision's core property, "exactly one issuer", still has no automated check, and the planned test has no step or owner. | Change both claims to future tense with an owner ("step N: production mode on Postgres"), or remove them. Give the wrong-`iss` test a step. |

**Total: 8.0/10** (pass 7, target 8). High impact, so the verdict is **ACCEPT PENDING DEFENSE**. The 2026-10-31 defense failed, so this round needs a new one.

### Delta vs round 1 (2026-10-24, 7.0/10)

| Dimension | Round 1 | Round 2 | Δ | Why |
|---|---|---|---|---|
| Context & drivers | 1.5 | 1.5 | 0 | T-IDs moved out of `Drivers` (fixed); an unsourced social-login driver was added (new deduction) |
| Options | 1.5 | 1.5 | 0 | Table unchanged; the round-1 "can't commit hosted config" con was not fixed |
| Decision & rationale | 1.5 | 2.0 | **+0.5** | Build/run paragraph names and weighs the runner-up on explicit criteria |
| Consequences & risk | 1.5 | 1.5 | 0 | Switching cost now truthful (fixed); patching has no owner, realm/hash migration left out, no "unacceptable when" |
| Accuracy & verifiability | 1.0 | 1.5 | **+0.5** | Issuer overclaim and class list fixed; new present-tense ECS/production-mode claims |
| **Total** | **7.0** | **8.0** | **+1.0** | |

### Optional changes (above target, in order of points gained)

1. Make the ECS / `kc.sh start` lines future tense with an owner, or remove them. Give the wrong-`iss` test a step. (Accuracy +0.5)
2. Name who patches Keycloak and how. Add the realm export and password-hash migration. Add "unacceptable when …". (Consequences +0.5)
3. Fix the hosted IdP con (config-as-code exists) and add the free dev tenant to its pros. (Options +0.5)
4. Drop or source the social-login driver. (Context +0.5)

### Note on the review record

The author's row `1 (defense) … Fixed by revision 2026-10-31: …` puts a claim of a fix into a reviewer row. A reviewer row should record only the verdict. Shorten it to `REVISE: defense not passed (no build/run split, cost-only reason vs hosted IdP)`. This round 2 row is what records that the revision was graded.

## Interviewer's objection (defense round 2)

"Your three reasons for Keycloak over a hosted IdP are that it runs offline, keeps the realm in the repo, and lets you inspect the server. All three are about a developer laptop, and Terraform keeps a hosted tenant's config in the repo too. Yet your consequences already put Keycloak on ECS in production mode. So is this a dev-environment decision or a production one? If it's production, which of your reasons still holds once there's a real deployment, and who patches a pinned 26.5.2 image that nothing updates?"

Why this is the weak point: the revision fixed the build/run split. But the run side the ADR now admits to owning has no owner, and the deployment it uses to soften the SPOF doesn't exist. Meanwhile the Decision says a hosted IdP is "a fair alternative" on a real deployment. The ADR's scope (dev only, or the production identity choice too) is the gap. Without a clear scope, the rationale and the consequences describe two different decisions.

(High impact: answer in drill mode. The shape of a strong answer is withheld until you answer.)

## Review record (append to ADR-0001)

```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 7/10 | ACCEPT PENDING DEFENSE (below target) |
| 1 (defense) | 2026-10-31 | architecture-kata-reviewer (drill) | — | REVISE: defense not passed (no build/run split, cost-only reason vs hosted IdP) |
| 2 | 2026-10-31 | architecture-kata-reviewer | 8/10 | ACCEPT PENDING DEFENSE |
```

On a passed defense, add: `| 2 (defense) | <date> | architecture-kata-reviewer (drill) | — | ACCEPT. Defense: passed <date> |`, then put 8 in the index Score column.

## Defense round 2 — 2026-10-31 — PASSED

Answer, as given: the decision is for local development during the learning phase. Production runs on ECS with better CI/CD and admin-level user management. The production IdP is decided in a new ADR at first deployment, and the ECS lines move out of ADR-0001.

Evaluation:
- Landed: the scope (local stack only) settles the dev-or-production tension. The reasons that remain (offline, free, an inspectable server) are enough at that scope. The production IdP is deferred to a named event: a new ADR at first deployment.
- Rejected argument: "the configs are in a private repo". Repo privacy helps a hosted IdP's Terraform config just as much, so it doesn't separate the options. A private repo is not a secret store either: access spreads through clones, CI, forks and history. Seeded credentials still need the ADR-0006 treatment.
- Not answered, acceptable at this scope: patching. Local-only with no real users; the production ADR owns it.

Verdict: **ACCEPT**. The answer isn't in the ADR yet, so it goes in before merge (A4: a strong answer the ADR doesn't contain is a revision item). These edits narrow the scope and remove unsupported claims, so no re-grade is needed:
1. Decision outcome: one scope line, "Scope: local development and the learning phase. The production IdP (Keycloak on ECS or a hosted IdP) is decided in a new ADR at first deployment."
2. Remove the ECS sentences from the T30 bullet and the T30 row in Future work. Remove "test and production environments run `kc.sh start` …" from the dev-mode bullet, or reword it as "a production deployment is out of scope; see the production IdP ADR".
3. Revisit trigger: replace the cost-based first bullet with "First deployment outside a developer machine: write the production IdP ADR".
4. Optional, same edit: stop using "keeping the realm in the repo" as a reason against hosted IdPs (Options con and Decision paragraph).

Final review record:

```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 7/10 | ACCEPT PENDING DEFENSE (below target) |
| 1 (defense) | 2026-10-31 | architecture-kata-reviewer (drill) | — | REVISE: defense not passed (no build/run split, cost-only reason vs hosted IdP) |
| 2 | 2026-10-31 | architecture-kata-reviewer | 8/10 | ACCEPT PENDING DEFENSE |
| 2 (defense) | 2026-10-31 | architecture-kata-reviewer (drill) | — | ACCEPT. Defense: passed 2026-10-31 (scope: local/learning; production IdP in a new ADR at first deployment) |
```

Index: Score column for 0001 = 8.
