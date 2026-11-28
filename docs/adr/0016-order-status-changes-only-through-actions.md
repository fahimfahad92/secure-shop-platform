# 0016. Order status changes only through actions; transitions are conditional updates

| | |
|---|---|
| Status | Proposed |
| Date | 2026-11-22 |
| Category | architecture |
| Impact | normal |
| Deciders | Fahim |
| Step | 2 (review findings) |
| Drivers | T15 (client sets order status / quantity without stock check, 11 Medium); finding #30; [0015](0015-no-transactional-failures-recorded-as-state.md) (system-owned statuses, no `@Transactional`) |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

`PUT /orders/{id}` takes `{"quantity": n, "status": "…"}`, and `OrderService.update` applies both: `setStatus(request.status())`, `setQuantity`, `save`. Any status the owner sends is accepted, in any direction: a `CANCELLED` order can be set back to `PENDING`, and an owner can mark their own order `CONFIRMED`. The quantity change doesn't ask Product Service, so 2 can become 1000 with no stock check. Both are T15 (`docs/threat-model/risk-register.md`), and both were listed as known gaps (`docs/architecture.md` → Next plans, `docs/services/order-service.md`).

[0015](0015-no-transactional-failures-recorded-as-state.md) makes the status field riskier. It adds system-owned values (`CREATING` in flight, `FAILED` and `REJECTED` as terminal outcomes) that no client may set or leave. A request field that carries a status would need a growing list of forbidden values.

0015 also removes `@Transactional`, so a read-modify-save is no longer protected even by a transaction. `save()` on a detached entity merges every column, including the `status` read earlier. The quantity change now calls Product Service (up to 5 s) between the read and the write. A cancel from another tab in that window would be overwritten, and the order written back as `PENDING`.

How should a client change an order's status and quantity, and how is each change made safe without a transaction?

## Decision drivers

- **The client can't express an illegal transition.** That's better than validating one after the fact.
- **System-owned values are refused by construction,** so a new one doesn't need adding to a deny-list.
- **Each change is atomic without a service transaction** (0015), and doesn't depend on #8 (`@Version`) landing first.
- **A quantity change goes through the same stock check** as placing the order.
- **Safe retries:** a repeated request after a lost response does no harm.

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **A. Action endpoints** (chosen): `POST /orders/{id}/confirm`, `POST /orders/{id}/cancel`; `PUT` keeps only `quantity`; every change is a conditional `UPDATE … WHERE <allowed current status>` | The client sends an intent, never a status, so an illegal status can't be sent. The allowed source states are the whitelist, and new system values match none of them. Each change is one atomic statement. Logs and the API read as business actions | A contract change; one endpoint per transition; the allowed states sit in JPQL, not in one Java method |
| B. Keep `status` in `PUT`, validate (current, requested) against a transition table | One endpoint; small change for clients | The client still has to know the statuses. System values need an explicit deny rule that every new value must join. Read-validate-save still needs `@Version` or a transaction to be safe |
| C. `PATCH /orders/{id}/status` with `{"status": …}` plus transition rules | Status is separated from other fields | Same as B: it's still a client-sent target status, with only a different URL |
| D. Refuse only the system-owned values in `PUT` | Smallest change | The owner can still confirm and revive orders; the client still has to know the statuses; it doesn't fix the race |
| E. `PATCH` instead of `POST` for the actions | Reads as "change part of the order" | `PATCH` means "apply this partial representation", usually with a body. A body-less `PATCH` on an action path is an unusual mix, while `POST` for actions is the common convention (Stripe `POST …/cancel`, Google AIP-136). The verb doesn't make an action idempotent; the design does |
| F. Load-modify-save with `@Version` (#8) instead of conditional updates | Conventional JPA; it also protects any future field | It needs #8 first (column, migration, 409 mapping). A version mismatch says "something changed", not "you may not do this from this state", so the service still has to re-check the state |

## Decision outcome

**Option A.** Order status is read-only to clients. It changes only through these actions:

| From | `PUT` (quantity) | `POST …/confirm` | `POST …/cancel` |
|---|---|---|---|
| `PENDING` | ✅ stock re-checked: 409 not enough stock; 409 product gone; 503 Product down | → `CONFIRMED` | → `CANCELLED` |
| `CONFIRMED` | 409 | 200, unchanged | → `CANCELLED` within `orders.cancel-window` (30 min) of confirmation; after that 409 |
| `CANCELLED` | 409 | 409 | 200, unchanged |
| `CREATING`, `FAILED`, `REJECTED` (0015) | 409 | 409 | 409 |

- **Every change is a conditional update** in `OrderRepository`: `updateQuantity`, `confirm`, `cancel`. Each is one `UPDATE … WHERE id AND user_sub AND <allowed status>` (the window is `status_changed_at > now − window`). The `WHERE` is the check, so the transition is atomic.
- **These repository methods carry their own `@Transactional`.** `@Modifying` queries need one, and it is 0015's "each repository call commits on its own". Services and controllers carry none.
- **When no row changed**, the service re-reads the order:
  - absent → 404, the same as another user's order;
  - already in the target state → 200;
  - otherwise → 409, with a message naming the state, plus a WARN log line (order id, `sub`, action, status).
- **`PUT` keeps the creation price.** Product Service is asked only for stock. A product deleted since the order was placed → 409 "product no longer available".
- **Every status change sets `status_changed_at`.** The cancel window and 0015's stuck-row query both read it.
- **Request bodies reject unknown fields** (`fail-on-unknown-properties=true`), so an older client's `"status"` gets 400, not a silent 200. The Product Service response type stays tolerant.
- **The owner triggers confirm and cancel** until payment exists.

## One-minute answer

A client can't set an order's status. It asks to confirm or cancel, and each action is one conditional `UPDATE … WHERE status = <allowed>`, so an illegal transition can't be sent at all, and system states like `FAILED` can never be left. We chose this over validating a status field because the field would need a growing deny-list, and without a transaction, a read-then-save could overwrite a concurrent cancel during the 5-second stock check. The cost is one endpoint per transition, and an owner can still confirm their own order until payment exists.

## Consequences

### Positive

- T15 is fixed. Status can't be set by the client, and quantity changes pass the stock check.
- Illegal transitions can't be expressed; system-owned values are safe by construction.
- Transitions are atomic with no transaction and no `@Version`.
- Retries of confirm and cancel are safe (idempotent).
- The API says what the user did ("cancel"), not which field changed.

### Negative (residual risk)

- **An owner can confirm their own order.** Confirm means nothing without payment yet. That's residual T15, until payment.
- **The allowed source states live in JPQL** in three queries, not in one Java transition table. A new transition means a new query and endpoint. Tests per transition are the guard.
- **API break:** `PUT` no longer accepts `status`. Strict JSON may reject fields a future client adds without coordination. The only clients today are Postman and tests.
- **Bulk updates skip entity callbacks.** `updatedAt` and `statusChangedAt` are set in each query, and a test checks them.
- **The stock check on `PUT` is a check, not a reservation.** Stock can still be oversold between the check and a parallel order (`architecture.md` → Next plans, #03).
- **`DELETE /orders/{id}` still erases orders,** including cancelled ones whose history 0015 wants kept. That's `tx-order-delete`'s decision.

## Verification

- `OrderStatusTransitionsTest` (integration, real Postgres):
  - `confirmThenCancel_withinWindow_endsCancelled`
  - `cancel_confirmedOutsideWindow_returns409`
  - `confirm_twice_secondIs200AndUnchanged`
  - `cancel_cancelled_isIdempotentAndConfirmIsRefused`
  - `put_withStatusField_returns400`
  - `put_onConfirmedOrder_returns409AndLeavesItUnchanged`
  - `put_aboveStock_returns409`
  - `put_whileCancelledDuringProductCall_staysCancelled` (the race this ADR closes)
  - `confirm_anotherUsersOrder_returns404`
  - `statusChangedAt_isSetOnCreateAndOnEachTransition`
- `OrderServiceTest`: `update_*`, `confirm_*`, `cancel_*` (the stock check, the creation price kept, the cutoff passed, idempotency, 0-row re-reads).
- `OrderControllerSecurityTest`: `confirm_noToken_returns401`, `cancel_noToken_returns401`, `confirm_readScopeOnly_returns403`, `cancel_readScopeOnly_returns403`.
- Refusals of `CREATING` / `FAILED` / `REJECTED`: tested in `tx-order-create`, where those values are added. No conditional update has them as a source state.

## Migration path

Done in one change: V3 (`status_changed_at`, backfilled from `updated_at`), the endpoints, the queries, the docs and Postman. To reverse: restore `status` in `UpdateOrderRequest` and `setStatus` in `update`. V3 can stay.

## Revisit trigger

- **Payment exists** (#01 v2): confirm and cancel move from the owner to the payment flow, or to an admin; the owner may keep cancel only.
- **A shipping or fulfilment state is added:** cancel must stop at it, and the window may go.
- **#8 lands** (`@Version`): keep the conditional updates for transitions; `@Version` covers other fields.
- **More than about five transitions:** consider one transition table in Java that generates or validates the queries.

## Related

- [0015](0015-no-transactional-failures-recorded-as-state.md): no `@Transactional`, the status model, system-owned values
- [0005](0005-price-from-the-catalog-never-from-the-client.md): price from the catalog; `PUT` keeps the creation price
- [0004](0004-owner-from-token-sub-404-for-others.md): owner from `sub`, 404 for other users' orders
- T15 in [`risk-register.md`](../threat-model/risk-register.md); findings #8, #30, #34 in [`.review/best-practices-findings.md`](../../.review/best-practices-findings.md)

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-11-28 | architecture-kata-reviewer | 8/10 | ACCEPT |
