# ADR review — round 2 — 0004 — 2026-11-02

Reviewer: `architecture-kata-reviewer` (ADR mode), fresh session — did not draft or edit 0004.
Pass mark 7, target 8. Impact `normal`, so no defense is required. A drill was run anyway (below).

ADR text is unchanged since round 1 (`adr-review-round1-2026-10-24.md`). None of round 1's optional items were applied.

## Fact-check

Matches:
- Controllers pass `jwt.getSubject()` — `OrderController.java:40-65`, `UserController.java:41-54`
- `OrderRepository.findByIdAndUserSub` and `findByUserSub` exist; `OrderService.update`/`delete` go through `getById` → same scoped lookup
- `OrderNotFoundException` → `404` in `GlobalExceptionHandler`
- `profiles.keycloak_sub VARCHAR(36) PRIMARY KEY`; `orders.user_sub` added in `V2__add_user_sub_to_orders.sql`; ids are `BIGSERIAL`
- All six Verification tests exist (`OrderOwnershipTest` ×4, `OrderServiceTest`, `UserControllerSecurityTest`)
- T13, T36, T41 match `docs/threat-model/risk-register.md`; 0001's migration path links back to 0004

Mismatches:
1. **New, missed in rounds 0 and 1:** `OrderRepository extends JpaRepository<Order, Long>`, so unscoped `findById`, `findAll`, `findAllById`, `getReferenceById`, `existsById`, `deleteById`, `count` are callable **today**. The ADR's main residual risk says "a new repository method without `sub` is a BOLA". The real risk is wider: calling an inherited method reintroduces BOLA without writing any repository method. The decision still holds, so G3 doesn't fail, but the risk is understated.
2. "User Service … exposes only `/users/me`" — `POST /users/register` (permitAll) also exists. It takes no user id, so ownership is intact, but the sentence is wrong.
3. Drivers list T24 (mass assignment on profiles). It is not a force behind ownership-by-`sub` (also flagged in round 1).

## Score — 7.5/10 — ACCEPT (below target)

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Orders had no owner before PR #3; sequential `BIGSERIAL` ids make walking them free; the 403-vs-404 leak is named. | Drop T24 from Drivers, or say why it's there. |
| Options | 1.5/2 | Three fair options. Missing: a framework-level ownership filter (RLS / Hibernate `@Filter` / narrowed `Repository<Order, Long>`), which appears only under Migration path; and "non-guessable UUID ids", the first counter a reviewer raises against the 404 argument. | Add a "framework-level ownership filter" row, rejected on cost for v1. One line on UUIDs: they make guessing harder but don't remove BOLA. |
| Decision & rationale | 1.5/2 | Beats load-then-check-403 on the stated criteria; the one-minute answer matches. But the driver "Ownership can't be forgotten in a new code path" is not met, and "closed by construction" says it is. | Say the driver is met for the current endpoints, and name the guards for new code (see the three layers below). |
| Consequences & risk | 1.5/2 | Honest: no admin view, 404 hides probing (T36/T41), data keyed by `sub`. Down from round 1's 2: the main residual risk understates the exposure (mismatch 1). | Restate the main Negative: unscoped methods inherited from `JpaRepository` are callable today. |
| Accuracy & verifiability | 1/2 | Six named tests, migration path, revisit triggers. But: no review date (required for security ADRs; flagged in rounds 0 and 1); review record empty despite two rounds; index score empty; `/users/me only` is wrong; no fitness function for the main risk. | Add a review date, paste the review record, fix the `/users/me` line, name a mechanical check once it exists. |

Delta vs round 1 (8.5): Consequences −0.5, Accuracy −0.5. The text didn't change. The drop comes from mismatch 1 (missed earlier) and the review date still missing after two flags.

## Changes to reach target 8 (optional, most points first)

1. **Main Negative:** state that the unscoped `JpaRepository` methods are on `OrderRepository` today. (+0.5 Consequences)
2. **Decision / Migration path:** record the three-layer guard from the drill, each marked **done**, **scheduled (step N)** or **rejected on cost**:
   - Narrow `OrderRepository` to `Repository<Order, Long>` exposing only the scoped finders plus `save`/`delete`, so the compiler blocks the inherited methods. Cost: no free CRUD methods; admin needs its own `AdminOrderRepository`, which matches the existing migration path.
   - ArchUnit **allowlist** rule: every query method declared on `OrderRepository` takes a `String userSub` parameter. It catches derived and `@Query` methods without an owner. It checks signatures only, not whether the JPQL actually uses the parameter.
   - Runtime check for queries that take `userSub` but don't use it: a generic cross-user test (seed user A, call every `/orders` route as user B, assert A's data never appears), and/or Postgres RLS on `user_sub`.
   Reword "closed by construction" to match whatever is actually done. (+0.5 Decision)
3. **Options:** add the framework-filter row and the UUID line. (+0.5 Options)
4. **Accuracy:** review date; paste the review record below; "`/users/me`, plus unauthenticated `POST /users/register`, which takes no user id"; list the ArchUnit rule or cross-user test in Verification **only once it exists**. Fill the index score. (+0.5 to +1 Accuracy)
5. Drop or justify T24.

Any one of items 1–4 reaches 8. Use the field name `userSub`, not "user id", throughout.

## Interviewer's objection

"`OrderRepository` extends `JpaRepository`, so `findById(id)` is one autocomplete away and returns any user's order. Your 'closed by construction' depends on nobody ever calling an inherited method. Why didn't you make the unsafe call impossible to write?"

## Drill (not required: Impact `normal`)

- **Answer 1:** admitted that `findById` is available; plan to use ArchUnit to block those methods. Landed: admission, a mechanical guard. Gaps: only "a plan"; a denylist (about 20 inherited methods, new ones in later Spring Data versions); ArchUnit doesn't see `@Query`/native SQL/`JdbcTemplate`; skipped the cheaper fix of narrowing the interface.
- **Press:** a later `@Query("... where o.id = :id") findOne(Long id)` — does the rule catch it?
- **Answer 2:** block any query or repository method that doesn't use the owner. Landed: switched to an allowlist, which catches the press example. Open: ArchUnit checks signatures, not meaning (a `userSub` parameter left out of the JPQL still leaks); inherited methods still need narrowing; it's `userSub`, not `userId`.
- **Outcome:** principle holds, enforcement incomplete. The senior answer is the three layers in change 2. Does not affect the verdict.

Next-strongest objection, if drilled: 404 hides probing, and there's no audit log (T36/T41) to notice a user walking ids.

## Review record (paste at the bottom of 0004)

```
| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 0 | 2026-10-24 | architecture-kata-reviewer (same session as drafting, baseline) | 9/10 | ACCEPT |
| 1 | 2026-10-24 | architecture-kata-reviewer | 8.5/10 | ACCEPT |
| 2 | 2026-11-02 | architecture-kata-reviewer | 7.5/10 | ACCEPT (below target) |
```

Index Score column for 0004: `7.5` (update after re-grade).

After revising, re-grade in a fresh session: `/architecture-kata-reviewer adr docs/adr/0004-owner-from-token-sub-404-for-others.md`.
