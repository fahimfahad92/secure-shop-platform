# 30 — Decision: no `@Transactional`; failures recorded as state, not rolled back

| | |
|---|---|
| Status | Open |
| Severity | MAJOR |
| Rule | SB-JPA |
| Service | order, product, user |

**Where:** `OrderService` (class-level `@Transactional`, two `readOnly`), `ProductService` (class-level, two `readOnly`), `ProfileService` (`getBySub` `readOnly`, `update`); every future service class

**What (the decision, by Fahim):** Services don't use `@Transactional`. Each repository call runs in its own short transaction and returns its connection at once. Instead of rolling back, a failed operation leaves a record: the row carries a status (e.g. `PENDING` → `CONFIRMED` / `FAILED`) with the reason and time, and the failure is logged with enough context (ids, the failing dependency) to follow it. Error handling maps each failure to a clear response.

**Why:**
- `@Transactional` holds a DB connection from the start of the method to the end, including remote calls (#2) and slow code
- A rollback removes every trace of a failed attempt: nothing shows that an order was tried, what failed, or how often. A status row keeps that history and can be queried
- It matches the cross-system reality: Keycloak and Product Service calls were never part of a DB transaction anyway (`ProfileService.register` already says so)

**Trade-offs to settle in the ADR:**
- **Atomicity of multi-row writes is gone.** Today every write is one row, so nothing breaks. A future write that changes two rows in one service (e.g. an order and its lines) would commit them separately; a crash between them leaves half the change. The ADR needs either an explicit exception ("a single local multi-row write may use a transaction with no remote call inside it") or a rule that every partial state has a status that shows it is incomplete
- **Read consistency:** two reads without a transaction can see different snapshots. Harmless today (single-row reads)
- **Lost-update protection** still needs optimistic locking (#8); a transaction never provided it
- **Data model work:** status and reason columns per aggregate (`orders` needs new status values and `failure_reason`; `profiles` needs a status for pending or failed deletes), and a decision on whether failed rows show in list endpoints
- **Retention:** failure rows grow; decide how long to keep them

**Fix direction:**
1. ADR first (replaces the "#2 rule: no remote calls inside a DB transaction" ADR planned for step 2): the rule, the exception for local multi-row writes or why there is none, the status model, the logging fields
2. Remove `@Transactional` from the three services; the readOnly ones go too
3. Apply the status pattern in #1 (profile delete) and #2 + #22 (order create)
4. An ArchUnit rule (shared with #21, #23): no class in `..service..` or `..controller..` is annotated with `@Transactional` and no method is, outside the ADR's exception
5. Verify with #29's `leak-detection-threshold` and #4 off: no connection held over 2 s in any test. Both are in place since `97b7dca` (2026-11-14): every service runs `open-in-view=false` and `leak-detection-threshold=2000`, tests included

**Understand first:** how Spring Data's repository methods run in their own transaction when there is no outer one (`SimpleJpaRepository` is `@Transactional`); why dirty checking needs an explicit `save()` once there's no surrounding transaction; the Saga pattern's "compensate and record" as the general form of this decision.

**Breakdown (2026-11-08):** one task per method, tracked in `Interview-Preparation/tasks/` (`/task-workflow <slug>`): `tx-00-adr-no-transactional` first (the ADR, and class-level `@Transactional` moved onto each method so the parts stay independent), then one task per method: Order `create` (with #2, #22), `getAll`, `getById`, `update`, `delete`; Product `create`, `getAll`, `getById`, `update`, `delete`; Profile `register`, `getBySub`, `update`, `delete` (with #1); `tx-99-archunit-no-transactional` last.

**Progress (2026-11-22, `tx-00`):** [ADR-0015](../../docs/adr/0015-no-transactional-failures-recorded-as-state.md) drafted, `Proposed`. Option B: no exception at all, `readOnly` included. Failed rows are listed with their status and kept; orders start in a separate in-flight value (`CREATING`, not the business `PENDING`), and stuck in-flight rows are detected, not reaped. #1 and #2 now point to it. The class-level `@Transactional` on `OrderService` and `ProductService` has moved onto each write method, with no behaviour change. Stays Open until `tx-99`.

**Progress (2026-11-28, `tx-order-update`):** `OrderService.update` has no `@Transactional`. It and the new `confirm` / `cancel` are each one conditional `@Modifying` update in `OrderRepository` (that call's own transaction, as for `save()`), re-reading the order when 0 rows change. No connection is held during the Product stock check. [ADR-0016](../../docs/adr/0016-order-status-changes-only-through-actions.md) (`Proposed`, 8/10), commits `80b9880`…`3f03998`. Still Open: 13 method tasks and `tx-99` remain.

**When done:** ADR in `docs/adr/`; mark it `Done`; #1 and #2 reference the ADR.
