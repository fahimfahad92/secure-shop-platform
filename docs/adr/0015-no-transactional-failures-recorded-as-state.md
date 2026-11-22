# 0015. No `@Transactional`; failures recorded as state

| | |
|---|---|
| Status | Proposed |
| Date | 2026-11-22 |
| Category | architecture |
| Impact | high |
| Deciders | Fahim |
| Step | 2 (review findings) |
| Drivers | Finding #30 (decision), #2 (HTTP call inside a DB transaction), #1 (profile delete can't roll back), #22; T18 (connection pool drained by slow Product calls, 10 Medium), T21 (Keycloak user / profile out of sync, 6 Low) |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

`OrderService` and `ProductService` are `@Transactional` at class level, so every public method runs in one transaction. `JpaTransactionManager` takes a connection from the pool when the transaction begins and returns it at commit. In `OrderService.create` that window includes `productClient.fetchProduct`, an HTTP call to Product Service with a 2 s connect and 3 s read timeout. A slow Product Service therefore holds Order Service's connections, and once the pool (5 connections since #29) is empty, `GET /orders`, which never calls Product Service, waits too and then gets `503` (T18, finding #2).

A rollback also leaves nothing behind. An order that failed on stock or on an unreachable Product Service is gone: nothing shows that it was tried, why it failed or how often. The calls that matter most were never inside a DB transaction anyway: Keycloak in `ProfileService.register` and `delete`, and Product Service in `create`. `register` already handles that by compensating and logging instead of rolling back.

What a local transaction protects today: every write method does exactly one write, so no method needs two writes to commit together.

| Method | Statements | Needs local atomicity? |
|---|---|---|
| `create` (Order, Product) | 1 `INSERT` | No: one statement is atomic by itself |
| `update` (Order, Product, Profile) | `SELECT`, then 1 `UPDATE` | No. The risk between the two is a lost update, which a `READ COMMITTED` transaction doesn't prevent either (#8, `@Version`) |
| `delete` (Order, Product) | `SELECT`, then 1 `DELETE` | No, same reason |
| Profile `register` | 1 `INSERT` plus a Keycloak call | Not local: Keycloak was never in the transaction |
| Profile `delete` | 1 `DELETE` plus a Keycloak call | Not local, same reason |

The planned failure-as-state flows add a second write, and it must be a separate commit: `INSERT … CREATING`, then the remote call, then `UPDATE` to the outcome (`tx-order-create`); `UPDATE … DELETE_PENDING`, then Keycloak, then `DELETE` (`tx-profile-delete`). The first commit is the failure record, so it must survive the failure.

`PENDING` is already taken. It is a business state: every new order starts in it (`Order.java:55`), the `orders.status` `CHECK` allows it, and until ADR-0016 the owner could set it through `PUT /orders/{id}`. The "operation in flight" marker therefore needs its own value.

Should services keep using Spring's declarative transactions, or should each repository call commit on its own, with failures recorded in the data?

## Decision drivers

- **No connection held across remote or slow code.** A connection should be out of the pool only while a statement runs.
- **Failures stay visible.** A failed attempt should leave a row that can be queried, with a reason and a time.
- **One rule that a test can enforce.** It should be checked by ArchUnit, not by review.
- **Honesty about what a local transaction covers.** It never covered the remote calls or lost updates (#8).

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **A. No `@Transactional`, with one exception:** a local multi-statement unit (read or write) with no remote call inside may use a method-level transaction | Keeps atomicity where it is cheap: an order and its lines, or a future outbox row and its entity. Reads that must agree can use `REPEATABLE_READ` | Two rules to learn. `tx-99`'s ArchUnit rule needs an allow-list, and the exception tends to grow. No write needs it today |
| **B. No `@Transactional` and no exception** (chosen) | One rule, trivially enforced. Every write today is a single `save` or `delete`, so it costs nothing now | A future multi-row write needs a status for each partial state, or a new ADR. An outbox is impossible without a transaction, which is the revisit trigger |
| C. Keep transactions and ban only remote calls inside them (the ADR originally planned for #2): split `create` into an orchestrator and a `@Transactional` persist method on another bean | Smallest change; conventional Spring; atomic local writes | A failure still leaves no trace, which was the main reason for #30. The ban on remote calls is easy to break without noticing, because a call two levels down is still inside the transaction |
| C+. **C plus a status column:** the orchestrator (`OrderService`, no transaction) writes the in-flight (`CREATING`) row through a `@Transactional` writer bean (`OrderWriter`), calls Product with no transaction open, then records the outcome through the writer in a second transaction | Fixes both problems: no connection held during the remote call, and every attempt leaves a row. Keeps atomic local writes for the day a step writes two rows (an outbox event with its order) | **Today it buys nothing:** every step is one statement (see the table above), so the writer's `@Transactional` wraps single statements that are already atomic. **It costs a second class per service:** the writer must be a separate bean, because a `this.` call skips the proxy and the annotation silently does nothing (SB-JPA-01). **Its guard can't be automated:** ArchUnit can check annotations, but not that no remote call runs inside a transaction (the call can sit two layers down), so the rule falls back to review, with Hikari leak detection catching it only after the fact. **It is the original bug's shape:** one added call in `OrderWriter.confirm` brings T18 back. It is Option A with the default reversed ("transactions, except around remote calls" instead of "none, except local units") |
| D. Do nothing | No work | T18 stays open; #1 and #2 stay unfixed |

**Why B over C+ and A:** both of them pay a class per service and a rule that is harder to enforce in exchange for multi-row atomicity, and nothing in the code needs that today. B gives the same two fixes with one class per service and a rule that one ArchUnit test enforces. The ban is not meant to last forever: the first write that needs two rows to commit together is the revisit trigger, and A (with an enforceable allow-list) is the planned successor.

## Decision outcome

**Option B.** Services don't use `@Transactional`: not on classes, not on methods, and not as `readOnly`.

- **Each repository call commits on its own.** Spring Data's `SimpleJpaRepository` runs each call in its own short transaction (`readOnly` for queries) and returns the connection at commit.
- **`spring.jpa.open-in-view=false`** is part of the rule. With OSIV on, the request-scoped `EntityManager` would keep the first query's connection until the response is written, with or without `@Transactional`.
- **Every change is saved explicitly** with `save()` or `delete()`. Nothing relies on dirty checking, because there is no persistence context that outlives the call.
- **Reads that must agree** use one query (a join or a fetch join), never a method transaction.
- **Status model: three kinds of value in one `status` column.**

  | Kind | Meaning | Examples | Who sets it |
  |---|---|---|---|
  | **In-flight** | A multi-step operation has started and has not finished | Order `CREATING`; profile `DELETE_PENDING` | The system only, as the operation's first commit |
  | **Outcome of the attempt** | The operation ended badly | `FAILED` (a dependency was unavailable), `REJECTED` (business rule, e.g. stock), profile `DELETE_FAILED` | The system only, with `failure_reason` |
  | **Business lifecycle** | The aggregate's normal states, unchanged by this ADR | Order `PENDING` → `CONFIRMED` / `CANCELLED` | As today |

  - **No in-flight value reuses a business value.** In particular, an order is `CREATING` until Product Service has answered; only then does it become `PENDING` (placed, as today), `REJECTED` or `FAILED`.
  - **In-flight and outcome values are system-owned.** No client request can set them, and no client request can move a row out of an in-flight value. Since [0016](0016-order-status-changes-only-through-actions.md) no request carries a status at all: status changes only through confirm and cancel, each a conditional update whose allowed source states never include an in-flight or outcome value.
  - **Order outcome values are terminal.** Once an order is `FAILED` or `REJECTED`, nothing updates it again, neither a client request nor the code. A retry is a new attempt and a new row.
  - **The one retryable outcome is profile `DELETE_FAILED`.** The profile still exists and there is one per user (`username` is `UNIQUE`), so a retry can't be a new row. A new delete request moves the same row back to `DELETE_PENDING`, and the previous `failure_reason` is logged before it is overwritten. This keeps the failure history intact and keeps code like `OrderService.update` (`getUnitPrice().multiply(...)`) away from rows that may have no price.
  - **Order prices are nullable only before pricing.** A `CREATING` row is inserted before Product Service has answered, so `unit_price` and `total_price` become nullable. A `CHECK` keeps the invariant that 0005 relies on, that a placed order always has a catalog price:
    ```sql
    CHECK (status IN ('CREATING', 'FAILED', 'REJECTED')
           OR (unit_price IS NOT NULL AND total_price IS NOT NULL))
    ```
    Rejected: inserting the row after the Product call. The columns could stay `NOT NULL`, but a Product Service outage (T18, the case this ADR exists for) would then leave no row at all.
  - **Every status change sets `status_changed_at`.**
  - Each aggregate's concrete values and its Flyway migration (new `CHECK` values, `failure_reason`, `status_changed_at`) are decided in its own task (`tx-order-create`, `tx-profile-delete`).
  - **Rejected alternative:** a separate `operation_state` column next to the business `status`. It separates the two concerns completely, but gives two columns that must agree, and every query and index would have to filter on both. One column with disjoint value sets gives the same separation at the query level (`status IN (<in-flight values>)`).
- **Failed and in-flight rows are listed with their status** in list endpoints such as `GET /orders`. The history is the point, and an in-flight row tells the user that their request is still being processed.
- **Retention:** failure rows are kept. A purge rule waits for the revisit trigger.
- **Logging.** Every recorded failure logs the aggregate id, the owner's `sub`, the new status, the failing dependency and the cause, at WARN for an expected rejection and at ERROR for an outage. The request ID joins these fields once #33 adds it.
- **Stuck in-flight rows.** When the failure itself can't be written (the DB is down, or the process dies between the first commit and the final update), the row stays in its in-flight value (`CREATING`, `DELETE_PENDING`) with no reason. Such rows are found with `status IN (<in-flight values>) AND status_changed_at < now() - <threshold>`, and the failed write is logged at ERROR. The threshold is well above the longest the operation can take: for order create that is 5 s (2 s connect + 3 s read on the Product call), so 1 minute. Business `PENDING` orders are never in that query, because waiting is normal for them. There is no reaper yet.

## One-minute answer

Our services don't use `@Transactional`. Each repository call commits on its own, and a failure is written down as a row status with a reason instead of being rolled back. We do this because a transaction held a DB connection through a remote call, so one slow dependency could drain the pool, and a rollback erased every trace of what failed. The cost: two writes are no longer atomic, which is fine while every write is one row, and a crash in the middle leaves a row stuck in an in-flight state such as `CREATING`. That value is separate from the business `PENDING`, so a query can find the stuck rows without flagging normal ones.

## Consequences

### Positive

- A connection leaves the pool only for one statement. A slow Product or Keycloak call can't hold it, which fixes T18 once `tx-order-create` lands.
- Every failed attempt is a row that can be queried, counted and shown to the user.
- One rule, enforced by ArchUnit (`tx-99`) with no allow-list.
- The same pattern as `ProfileService.register` (compensate and record), now applied everywhere.

### Negative (residual risk)

- **No atomic multi-row writes.** Two `save` calls commit separately, so a crash between them leaves half the change. Today no method needs two writes to commit together (see the table in the Context), so nothing is affected. The first feature that does is the revisit trigger, not a workaround.
- **Stuck in-flight rows** when the failure can't be recorded. They are detected (in-flight value + 1-minute threshold + ERROR log), not repaired. Alerting comes with step 6 observability.
- **More status values to keep straight.** Each aggregate's `status` now mixes business, in-flight and outcome values. A mistake (such as a client allowed to set `CREATING`, or a new in-flight state named like a business one) breaks detection silently. The rules above and per-task tests (`PUT` rejects system-owned values) are the guard.
- **No consistent snapshot across two reads.** Harmless while every read is one query. Under Postgres' default `READ COMMITTED`, a method transaction wouldn't have given one either.
- **Lost updates** are not prevented. A transaction never prevented them; optimistic locking (`@Version`, #8) does.
- **Failure rows grow** without a purge rule.
- **`save` on a detached entity becomes `merge`:** a SELECT plus an UPDATE for each write.
- **Row-level security costs more.** The RLS migration path in [0003](0003-gateway-checks-session-services-enforce-rules.md) and [0004](0004-owner-from-token-sub-404-for-others.md) sets the caller's `sub` with `SET LOCAL` per transaction. With one transaction per repository call, that has to happen inside every repository call, for example in a connection-preparing hook.
- **[0005](0005-price-from-the-catalog-never-from-the-client.md)'s consequence "The HTTP call runs inside the database transaction"** becomes stale once `tx-order-create` lands. 0005 is Accepted and its decision doesn't change, so it is not edited; this ADR records the change.

## Verification

- Today:
  - `OrderServiceApplicationTests`, `ProductServiceApplicationTests` and `UserServiceApplicationTests`: `openInViewIsOff` and `poolIsConfigured`
  - `spring.datasource.hikari.leak-detection-threshold=2000` in every service's `application.properties`, so a connection held for over 2 s logs a leak warning, in tests too
- Planned, in the per-method tasks of finding #30:
  - each removes one annotation and adds the tests for its status rows (for example, a failed order leaves a `FAILED` / `REJECTED` row, and a successful create ends in `PENDING`, never left in `CREATING`)
  - ADR-0016 (`tx-order-update`): no request carries a status (`OrderStatusTransitionsTest.put_withStatusField_returns400`); `PUT`, confirm and cancel change only `PENDING` / `CONFIRMED` rows (`put_onConfirmedOrder_returns409AndLeavesItUnchanged`); refusals of `CREATING` / `FAILED` / `REJECTED` are tested in `tx-order-create`
  - `tx-order-create`: a migration test that inserting a `PENDING` or `CONFIRMED` order with a `NULL` price violates the `CHECK`
  - `tx-99-archunit-no-transactional` adds an ArchUnit rule per service: no class or method in `..service..` or `..controller..` is annotated with `@Transactional`

  This section gets the test names when those tasks land.

## Migration path

1. Prep: class-level annotations moved onto each method, with no behaviour change (this ADR's PR).
2. Per method, one task each. **Order `update` lands before or together with Order `create`**: until no request can set a status, a client could overwrite a recorded `FAILED` or `REJECTED`. Order `update`, `confirm` and `cancel` ([0016](0016-order-status-changes-only-through-actions.md): status only through actions, conditional updates), `create` (with #2, #22: `CREATING` first, new statuses, `failure_reason`, `status_changed_at`, nullable `unit_price` / `total_price` with the status-tied `CHECK`), `getAll`, `getById`, `delete`; Product `create`, `getAll`, `getById`, `update`, `delete`; Profile `register`, `getBySub`, `update`, `delete` (with #1: `DELETE_PENDING` / `DELETE_FAILED`).
3. `tx-99`: the ArchUnit rule, after which finding #30 is closed.

Reversal: put the annotations back. The status columns stay useful either way. Restoring `NOT NULL` on `unit_price` / `total_price` needs one data step first: delete the unpriced `CREATING` / `FAILED` / `REJECTED` rows, or move them to an archive table.

## Revisit trigger

- **A feature needs two rows written atomically:** an outbox row with its entity, order lines, or a stock reservation with its order. Write a superseding ADR, most likely Option A: `@Transactional` allowed only on `*Writer` classes, which ArchUnit checks may not depend on any `*Client` (remote call) class. Consider a Saga orchestrator if the unit spans services.
- **Stuck in-flight rows show up** in step 6 observability (any in-flight row older than 1 minute): add a reaper that marks them `FAILED` with reason `stale`.
- **A failure table passes 1 M rows:** add a retention or purge rule.
- **Row-level security is adopted:** settle where `SET LOCAL` runs.

## Related

- [0003](0003-gateway-checks-session-services-enforce-rules.md), [0004](0004-owner-from-token-sub-404-for-others.md): the RLS migration path (see Negative)
- [0005](0005-price-from-the-catalog-never-from-the-client.md): the "HTTP call inside the transaction" consequence this ADR makes stale
- [0008](0008-schema-per-service-on-one-postgres.md): one Postgres, schema per service; the pool limits that T18 is about
- [0016](0016-order-status-changes-only-through-actions.md): order status changes only through confirm / cancel, as conditional updates; how no request can set a system-owned value
- Findings #1, #2, #4, #8, #21, #22, #29, #31, #33 in [`.review/best-practices-findings.md`](../../.review/best-practices-findings.md)

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-11-22 | architecture-kata-reviewer | 8/10 | ACCEPT PENDING DEFENSE |
| 2 | 2026-11-22 | architecture-kata-reviewer | 8.5/10 | REVISE (defense failed: in-flight vs business `PENDING` not separated) |
| 3 | 2026-11-22 | architecture-kata-reviewer | 9/10 | ACCEPT (Defense: passed 2026-11-22) |
