# 0004. Owner from the token's `sub`, `404` for other users' data

| | |
|---|---|
| Status | Accepted |
| Date | 2026-10-24 (recorded). Decided 2026-09-26, #01 Phase 3, PR #3 |
| Category | security |
| Impact | normal |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5, retroactive) |
| Drivers | T13 (BOLA on orders); OWASP API Security Top 10, API1 |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

Before Phase 3 (PR #3), orders had no owner. The `orders` table had no user column, and any caller with a valid token could list, read, change or delete every order. Phase 3 adds User Service with one profile per user and makes orders per-user, so both services need a rule for where the owner comes from.

The usual way to get this wrong is broken object level authorization (BOLA), OWASP API1: the API accepts an id from the client and forgets to check that the caller owns it. A second, smaller leak is the status code: answering `403` for someone else's order and `404` for a missing one tells an attacker which ids exist. Order ids are sequential (`BIGSERIAL`), so walking them costs nothing.

How does a service know whose data a request is about, and what does a caller see for data that isn't theirs?

## Decision drivers

- No request field a client could change to reach another user's data
- Ownership can't be forgotten in a new code path
- Other users' ids can't be probed for existence
- Works with the identity Keycloak already gives us ([0001](0001-keycloak-as-the-only-authorization-server.md))

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **Owner from `sub`; ownership in the query; a miss is `404`** | Nothing to substitute; the check can't be skipped after loading; existence not revealed | Every repository method needs the `userSub`; admins can't see other users' data through these endpoints |
| Owner from `sub`; load by id, then compare owner; `403` for others | Clear error for the caller; same number of queries | Reveals that the id exists; the comparison is a separate step a new method can forget |
| Owner id in the path or query (`/users/{id}`, `/orders?user=`), checked against the token | Familiar REST shape; easy admin access | The client chooses the id; one missed check is a BOLA |
| Framework-level ownership filter: Postgres row-level security on `user_sub`, or a Hibernate `@Filter` | Applies to every query, including inherited, `@Query` and native ones; a forgotten `userSub` can't leak | RLS needs the caller's `sub` set with `SET LOCAL` per transaction (connections are pooled) and a non-superuser DB role, which doesn't exist until step 2 (#6). A Hibernate filter must be enabled per session and doesn't cover native SQL. Both are new infrastructure for one owner-scoped table |

Non-guessable ids (UUIDs instead of `BIGSERIAL`) were considered as an addition, not an option: they make walking ids harder but don't remove BOLA. A leaked or shared id still works for anyone if ownership isn't checked.

## Decision outcome

**Chosen: owner from the token's `sub`, ownership part of the lookup, `404` for a miss.**

- No endpoint takes a user id. Controllers pass `jwt.getSubject()` to the service (`OrderController`, `UserController`)
- Order Service queries with `findByIdAndUserSub` and `findByUserSub`; a miss throws `OrderNotFoundException` → `404`. Update and delete go through the same lookup
- User Service keys the profile by `keycloak_sub` (primary key). Its endpoints are `/users/me` (GET, PUT, DELETE), plus unauthenticated `POST /users/register`, which takes no user id
- Another user's order is indistinguishable from an order that doesn't exist

Load-then-check with `403` was rejected because it reveals existence and adds a step that can be forgotten. Ids in the path were rejected because they hand the choice of owner to the client. A framework-level filter was rejected on cost for v1: it needs per-service DB roles first, and the guards scheduled below close most of the gap more cheaply. It stays the migration path.

**Scope.** The driver "ownership can't be forgotten in a new code path" is met for the current endpoints only. `OrderRepository` extends `JpaRepository<Order, Long>`, so unscoped `findById`, `findAll`, `getReferenceById`, `deleteById` and the rest are callable today, and a new derived or `@Query` method can leave out `userSub`. `ProfileRepository` extends `JpaRepository<Profile, String>`: its `findById` takes the owner's `sub`, but `findAll` and `count` don't. Until step 2, code review and the ownership tests are the only guard.

### Scheduled hardening (step 2, review findings)

Decided here, implemented in step 2 of the build sequence. Not in Verification until the code exists.

1. **Narrow both repositories.** `OrderRepository` becomes `Repository<Order, Long>` declaring only the scoped finders plus `save` and `delete`; `ProfileRepository` becomes `Repository<Profile, String>` declaring `findById`, `save` and `delete`. The compiler then blocks the inherited unscoped methods. Cost: no free CRUD methods; test cleanup (`deleteAll()` in `OrderOwnershipTest` and `UserControllerSecurityTest`) moves to `@Sql`; admin access gets its own `AdminOrderRepository` (see Migration path)
2. **ArchUnit rules** (`OwnershipArchitectureTest` in each service, run by `mvn verify` in CI): every query method declared on `OrderRepository` takes a `String userSub` parameter, and every query method on `ProfileRepository` takes the `sub` as its id, with `save` and `delete` the only exceptions; neither repository may extend `CrudRepository` or `JpaRepository` again; only `..service..` classes may use `..repository..`. They catch derived and `@Query` methods without an owner and a widened interface. They check signatures only, not whether the JPQL uses the parameter
3. **Cross-user ownership test** (already item 4 in [0003](0003-gateway-checks-session-services-enforce-rules.md)): seed data for user A, call every `/orders` and `/users/me` route as user B, assert A's data never appears. It catches a `userSub` parameter that a query takes but ignores

Tracked as step 2 review findings #21 (items 1, 2) and #20 (item 3) in [`.review/best-practices-findings.md`](../../.review/best-practices-findings.md).

## One-minute answer

The owner always comes from the token's `sub`, never from the request, and ownership is part of the database query. Another user's order looks exactly like one that doesn't exist, so the answer is `404` and ids can't be probed. The costs are that admin or support access needs separate endpoints with their own rules, and that until step 2 narrows the repository, an unscoped inherited method is one call away.

## Consequences

### Positive

- BOLA on the current order and profile endpoints is closed, and tested
- No user ids in URLs, so none in logs or browser history

### Negative (residual risk)

- **Unscoped repository methods are callable today.** `OrderRepository` inherits `findById`, `findAll`, `getReferenceById`, `existsById`, `deleteById` and `count` from `JpaRepository`. `ProfileRepository` inherits `findAll` and `count`. Calling one in a new code path reintroduces BOLA without writing any repository method, and so does a new method that leaves out `userSub`. Until step 2 (items 1–3 above), only code review and the ownership tests guard this; there is no framework-level filter
- **No admin view.** An admin or support use case needs a separate, role-gated endpoint and its own ADR
- **`404` hides real authorization failures** from monitoring. Without an audit log (T36, T41, step 6) a probing user leaves no trace
- **Data is keyed by Keycloak's `sub`.** Re-creating the realm or changing IdP changes `sub` values and orphans the data ([0001](0001-keycloak-as-the-only-authorization-server.md) → migration path)

## Verification

- `OrderOwnershipTest.getById_anotherUsersOrder_returns404NotForbidden`
- `OrderOwnershipTest.update_anotherUsersOrder_returns404AndLeavesItUnchanged`
- `OrderOwnershipTest.delete_anotherUsersOrder_returns404AndKeepsIt`
- `OrderOwnershipTest.getAll_showsOnlyTheCallersOwnOrders`
- `OrderServiceTest.getById_ownedBySomeoneElse_throwsOrderNotFoundException`
- `UserControllerSecurityTest.getMyProfile_withToken_returnsTheCallersOwnProfile`

Not yet written (step 2): narrowed `OrderRepository` and `ProfileRepository`, ArchUnit ownership rule, cross-user ownership test (see Scheduled hardening)

## Migration path

If admin access is needed: add `/admin/orders/**` gated on the `admin` role, with its own `AdminOrderRepository`, rather than loosening the owner queries. If an ownership regression gets past the step 2 guards: Postgres row-level security on `user_sub`, with the caller's `sub` set by `SET LOCAL` per transaction and a non-superuser role per service (#6), or a Hibernate filter.

## Revisit trigger

- An admin or support use case for other users' orders or profiles
- Ids that become shareable on purpose (for example a public order link)
- An ownership regression that reaches `main` despite the narrowed repository, the ArchUnit rule and the cross-user test

Review date: 2027-10-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 0 | 2026-10-24 | architecture-kata-reviewer (same session as drafting, baseline) | 9/10 | ACCEPT |
| 1 | 2026-10-24 | architecture-kata-reviewer | 8.5/10 | ACCEPT |
| 2 | 2026-11-02 | architecture-kata-reviewer | 7.5/10 | ACCEPT (below target) |
| 3 | 2026-11-02 | architecture-kata-reviewer (same session as round 2 review; confirm in a fresh session) | 9.5/10 | ACCEPT |

Revised after round 2: inherited `JpaRepository` methods stated as residual risk, three-layer guard scheduled for step 2 (#20, #21), framework-filter option and UUID line added, `/users/me` line corrected, T24 dropped, review date added. Round 2 and 3 reports: `.review/adr-review-round2-0004-2026-11-02.md`, `.review/adr-review-round3-0004-2026-11-02.md`.
