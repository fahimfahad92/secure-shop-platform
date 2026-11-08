# ADR review — round 3 — ADR-0003 re-review

- Date: 2026-11-01
- Reviewer: architecture-kata-reviewer (ADR mode)
- Branch: `threat-model` @ `7927654` (revision committed in `3f274d3`)
- Pass mark 7/10, target 8/10 (from `docs/adr/README.md`)
- Independence: **same session as round 2 and the defense**, which also wrote the required-changes list the revision follows. ADR content not edited by the reviewer. Same-context scores run high; a fresh-session re-run is recommended for the gating score.
- Defense: passed in round 2 (2026-11-01). Decision unchanged since, so no new defense. Optional round 3 objection skipped by the author.
- Fact-check (match): `OrderOwnershipTest` methods `getAll_showsOnlyTheCallersOwnOrders`, `getById_anotherUsersOrder_returns404NotForbidden`, `update_anotherUsersOrder_returns404AndLeavesItUnchanged`, `delete_anotherUsersOrder_returns404AndKeepsIt`; findings #18, #19, #20 in `.review/best-practices-findings.md` map to the items as stated; ADR-0004 migration path names RLS; not-yet-written tests are kept out of Verification.
- Fact-check (mismatch): "Today Order and Product fail closed (`anyRequest()` is the strictest rule in each)". True for Product, false for Order in effect. `docker/keycloak/secure-shop-realm.json`: `secure-shop-gateway` has `orders:read` and `orders:write` as **default** client scopes, and `default-roles-secure-shop` includes `user`. Every logged-in user's relayed token carries `orders:write` + `user`, so Order's `anyRequest()` default admits any logged-in user, the same exposure the ADR calls fail-open for User Service. The round 2 defense evaluation wrongly accepted "forgetting fails closed on Order"; the rest of the defense does not depend on it.

## ADR 0003 — The Gateway checks only "is there a session"; services enforce the rules — 8.5/10 — ACCEPT

Gates: G1 ✓ G2 ✓ G3 ✓ (the wrong claim narrows the hardening plan, not the split itself) G4 ✓ G5 ✓

| Dimension | Score | Δ vs round 2 | Evidence | To gain points |
|---|---|---|---|---|
| Context & drivers | 2/2 | 0 | Unchanged: TB7, T8, T13, T40 (13 High), "today" vs step 3. | — |
| Options | 1.5/2 | 0 | Table unchanged. Header identity rejected in one time-bound line ("forgeable while service ports are reachable"); its step-3 form (Gateway-signed internal token) not considered; no externalized-policy (OPA) row. | Steelman header identity in its step-3 form, reject on the internal-callers argument already in the "Both" rejection. |
| Decision & rationale | 2/2 | +0.5 | Three bullets against "Both" on the stated criteria (internal callers, "moves the discipline", "neither authorization bug is visible at the edge"). Scope clear: Scheduled hardening decided now, implemented in step 2, not in Verification. Migration path and Revisit trigger agree. One-minute answer matches. | — |
| Consequences & risk | 2/2 | +0.5 | "No dependency on the Gateway" replaces "Defense in depth". Wildcard risk and `GET /orders/all` leak named with what closes them; RLS backstop. Costs are the most convincing part. | — |
| Accuracy & verifiability | 1/2 | −0.5 | Verification complete and honest. But the Order fail-closed claim is false, so: Scheduled hardening item 1 covers User Service only; residual risk "Until step 2, User Service fails open" omits Order; findings #18–20 referenced without a path. | Fix the Order claim; link `.review/best-practices-findings.md`. |

Total 8.5/10 (+0.5 vs round 2).

## Optional changes (by points gained)

1. **Order fail-closed claim (factual; fix before merge).** "Today only Product fails closed. Order's default (`orders:write` + `user`) is held by every logged-in user, because the Gateway client gets both scopes by default." Extend Scheduled hardening item 1 to Order: explicit matchers for `POST /orders`, `PUT`/`DELETE /orders/{id}`, then `anyRequest().denyAll()`. Widen the residual risk to "User and Order Service fail open for new endpoints" and widen finding #18 to match.
2. **Link the findings file** next to "#18, #19, #20". Items 1 + 2 together: +0.5 Accuracy.
3. **Header-identity row in its step-3 form** (Gateway-signed internal token), rejected on internal callers. +0.5 Options.

## Next

Index score for 0003 in `docs/adr/README.md`: 8.5 now, or re-grade after the optional changes (fresh session).

## Review record (as in the ADR)

```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 7.5/10 | ACCEPT PENDING DEFENSE (below target) |
| 2 | 2026-11-01 | architecture-kata-reviewer | 8/10 | ACCEPT PENDING DEFENSE |
| 2 (defense) | 2026-11-01 | architecture-kata-reviewer | 8/10 | Defense: passed 2026-11-01; required changes 1–5 open |
| 3 | 2026-11-01 | architecture-kata-reviewer | 8.5/10 | ACCEPT (same-session re-review; required changes 1–5 done; Order fail-closed claim open) |
```
