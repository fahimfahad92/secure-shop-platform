# 0004. Owner from the token's `sub`, `404` for other users' data

| | |
|---|---|
| Status | Proposed |
| Date | 2026-10-24 (recorded). Decided 2026-09-26, #01 Phase 3, PR #3 |
| Category | security |
| Impact | normal |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5, retroactive) |
| Drivers | T13 (BOLA on orders), T24; OWASP API Security Top 10, API1 |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

Orders and profiles belong to one user. Broken object level authorization (BOLA) is the most common API flaw: the API accepts an id from the client and forgets to check that the caller owns it. A second, smaller leak is the status code: answering `403` for someone else's order and `404` for a missing one tells an attacker which ids exist.

How does a service know whose data a request is about, and what does a caller see for data that isn't theirs?

## Decision drivers

- No request field a client could change to reach another user's data
- Ownership can't be forgotten in a new code path
- Other users' ids can't be probed for existence
- Works with the identity Keycloak already gives us ([0001](0001-keycloak-as-the-only-authorization-server.md))

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **Owner from `sub`; ownership in the query; a miss is `404`** | Nothing to substitute; the check can't be skipped after loading; existence not revealed | Every repository method needs the `sub`; admins can't see other users' data through these endpoints |
| Owner from `sub`; load by id, then compare owner; `403` for others | Clear error for the caller; same number of queries | Reveals that the id exists; the comparison is a separate step a new method can forget |
| Owner id in the path or query (`/users/{id}`, `/orders?user=`), checked against the token | Familiar REST shape; easy admin access | The client chooses the id; one missed check is a BOLA |

## Decision outcome

**Chosen: owner from the token's `sub`, ownership part of the lookup, `404` for a miss.**

- No endpoint takes a user id. Controllers pass `jwt.getSubject()` to the service (`OrderController`)
- Order Service queries with `findByIdAndUserSub` and `findByUserSub`; a miss throws `OrderNotFoundException` → `404`. Update and delete go through the same lookup
- User Service keys the profile by `keycloak_sub` (primary key) and exposes only `/users/me`
- Another user's order is indistinguishable from an order that doesn't exist

Load-then-check with `403` was rejected because it reveals existence and adds a step that can be forgotten. Ids in the path were rejected because they hand the choice of owner to the client.

## One-minute answer

The owner always comes from the token's `sub`, never from the request, and ownership is part of the database query. Another user's order looks exactly like one that doesn't exist, so the answer is `404` and ids can't be probed. The cost is that admin or support access to other users' data needs separate endpoints with their own rules.

## Consequences

### Positive

- BOLA on orders and profiles is closed by construction, and tested
- No user ids in URLs, so none in logs or browser history

### Negative (residual risk)

- **A new repository method without `sub` is a BOLA.** Only code review and the ownership tests guard this; there is no framework-level filter (such as a Hibernate filter or row-level security)
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

## Migration path

If admin access is needed: add `/admin/orders/**` gated on the `admin` role, with its own query methods, rather than loosening the owner queries. If missing ownership filters become a real risk as the code grows: Postgres row-level security keyed on a session variable, or a Hibernate filter.

## Revisit trigger

- An admin or support use case for other users' orders or profiles
- Ids that become shareable on purpose (for example a public order link)

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
