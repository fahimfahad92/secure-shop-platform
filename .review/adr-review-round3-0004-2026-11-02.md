# ADR review — round 3 — 0004 — 2026-11-02

Reviewer: `architecture-kata-reviewer` (ADR mode). Pass mark 7, target 8. Impact `normal`, so no defense is required.

**Independence:** this session did not edit 0004, but it wrote the round 2 review (`adr-review-round2-0004-2026-11-02.md`) and suggested the three-layer guard the revision adopts. Grading text that follows the reviewer's own advice can inflate the score. Confirm in a fresh session before treating 9.5 as the score that gates acceptance.

## Fact-check (new claims in the revision)

Matches:
- `ProfileRepository extends JpaRepository<Profile, String>`
- `deleteAll()` in `OrderOwnershipTest:48` and `UserControllerSecurityTest:49` (the cleanup the ADR says moves to `@Sql`)
- `.review/best-practices-findings.md`: #6 "Shared hardcoded DB superuser", #20 "No route-manifest or cross-user ownership test (ADR-0003)", #21 "Unscoped `JpaRepository` methods on owner-scoped repositories (ADR-0004)"
- 0003 Scheduled hardening item 4 is the cross-user ownership test
- `UserController`: `/users/me` GET/PUT/DELETE plus `POST /users/register` (permitAll), which takes no user id. The corrected line is accurate
- Index score `7.5` matches the last review row
- ArchUnit is not yet in any `pom.xml`, consistent with "not yet written"

Precision notes (none affects the decision):
1. The planned rule "only `..service..` classes may use `..repository..`" fails if test classes are imported: `OrderOwnershipTest` and `UserControllerSecurityTest` use the repositories directly. State "main classes only" (`ImportOption.DoNotIncludeTests`).
2. `ProfileRepository` declares `existsByUsername`. It is unused. The narrowed interface (`findById`, `save`, `delete`) drops it without saying so, which is fine, but name it.
3. `ProfileRepository` also inherits `findAllById`, `existsById` and `deleteById`, not only `findAll` and `count`. These take arbitrary subs, the same exposure as `findById`.

## Score — 9.5/10 — ACCEPT

Gates: G1 ✓ G2 ✓ G3 ✓ G4 ✓ G5 ✓

| Dimension | Score | Evidence | To gain points |
|---|---|---|---|
| Context & drivers | 2/2 | Unchanged and specific. T24 dropped, so Drivers now matches the problem. | — |
| Options | 2/2 | The framework-filter row is steelmanned with costs specific to this system ("`SET LOCAL` per transaction (connections are pooled)", "non-superuser DB role, which doesn't exist until step 2 (#6)"). UUIDs correctly framed as an addition, not an option. Choosing RLS would also look reasonable. | — |
| Decision & rationale | 2/2 | The Scope paragraph admits the driver is "met for the current endpoints only". Filter rejected on a concrete dependency. The one-minute answer includes the interim cost and matches the Decision section. | — |
| Consequences & risk | 2/2 | The main Negative names the inherited methods and the "new method that leaves out `userSub`" path, plus what guards it until step 2. Costs are the most convincing part. | Optional: complete `ProfileRepository`'s inherited list (note 3). |
| Accuracy & verifiability | 1.5/2 | Verification lists only existing tests, with a separate "Not yet written (step 2)" list. Review date 2027-10-24, revisit trigger on regression, finding links, index in sync. −0.5: the guard for the main residual risk can't be run today, and the planned ArchUnit rule as written would fail against the current tests (note 1). | State "main classes only" in the rule. When step 2 lands, move the narrowed repositories, `OwnershipArchitectureTest` and the cross-user test into Verification by name. |

**Delta vs round 2 (7.5):** Context 0, Options +0.5, Decision +0.5, Consequences +0.5, Accuracy +0.5. Total **+2.0 → 9.5**.

## Optional changes

1. ArchUnit rule wording: "main classes only" (note 1).
2. Name the `existsByUsername` removal in Scheduled hardening item 1 (note 2).
3. Complete `ProfileRepository`'s inherited list in Scope and Negative (note 3).
4. Review record housekeeping: the round 2 row puts revision notes inside the Verdict cell. Keep that cell to `ACCEPT (below target)` and move the notes to a line under the table.
5. After step 2: move the three guards into Verification and re-grade. That is the last 0.5.

## Interviewer's objection

"Your ArchUnit rule proves every query *takes* a `userSub`. Nothing proves the value is the caller's. Tomorrow someone adds `GET /orders/by-user/{sub}` for a support screen and passes the path variable straight into `findByUserSub`. The narrowed repository compiles, ArchUnit passes, the existing tests pass. What catches it?"

Why this is the weak point: all three layers check the repository side. The real guarantee ("no endpoint takes a user id", where the owner comes from the token) has no mechanical check. Only the cross-user test can catch it, and only if it finds routes automatically rather than from a hand-written list.

What a strong answer would contain:
- The owner has exactly one source: a resolver such as `@CurrentUserSub String sub` that reads the `SecurityContext`, plus an ArchUnit rule that no controller method takes a parameter named `sub` or `userSub` and only the resolver calls `Jwt.getSubject()`
- The cross-user test enumerates routes from `RequestMappingHandlerMapping`, so a new endpoint is covered automatically
- RLS removes the question entirely: the DB session variable is set in one place from the authenticated principal, and request parameters can't reach it. That's an argument for the migration path, not for doing it now

Not answered yet (optional drill). A strong answer is a good one-line addition to Scheduled hardening.

## Review record (append to 0004)

```
| 3 | 2026-11-02 | architecture-kata-reviewer (same session as round 2 review; confirm in a fresh session) | 9.5/10 | ACCEPT |
```

Index Score column for 0004: `9.5`.
