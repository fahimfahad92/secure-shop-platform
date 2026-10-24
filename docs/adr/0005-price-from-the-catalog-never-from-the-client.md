# 0005. Price from the catalog, never from the client

| | |
|---|---|
| Status | Proposed |
| Date | 2026-10-24 (recorded). Decided 2026-09-26, #01 Phase 2, PR #2 |
| Category | security |
| Impact | normal |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5, retroactive) |
| Drivers | T14 (client sets its own price), T38; OWASP API Security Top 10, API6 (unrestricted access to sensitive business flows) |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

An order has a unit price and a total. Before Phase 2 there was no catalog, so the price had to come from somewhere. Once Product Service exists, Order Service can either take a price from the client or look it up. A client-supplied price lets anyone order a 99.99 keyboard for 0.01.

Where does an order's price come from, and what happens when it can't be found?

## Decision drivers

- A client must not be able to influence the price
- An order keeps the price it was placed at, even if the catalog changes later
- Clear errors for a missing product or an unreachable catalog
- No extra infrastructure in v1

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **Request has no price field; Order Service fetches price and stock from Product Service at order time and stores the unit price on the order** | Nothing to tamper with; price snapshot kept; simple | Order creation depends on Product Service being up; price integrity depends on that call |
| Client sends the price; server checks it against the catalog and rejects a mismatch | Client can show "price changed" cleanly | Still a field to tamper with; the check is code that can be skipped or wrong; same dependency on Product Service |
| Order Service keeps a local copy of prices, updated by events from Product Service | Orders can be placed when Product Service is down | Stale prices; event infrastructure (broker, outbox) for one field; two sources of truth |
| Trust the client's price | Nothing to build | The vulnerability itself |

## Decision outcome

**Chosen: no price in the request; the catalog is the only source.**

- `CreateOrderRequest` is `(productId, quantity)`. There is nowhere to send a price
- `OrderService.create` calls `ProductClient.fetchProduct`, checks stock, and stores `unitPrice` and `totalPrice` from the catalog
- `OrderService.update` recalculates the total from the stored `unitPrice`, never from the request
- Product not found: `400`. Product Service down or erroring: `503`. There is no fallback price; a cached or default price is a later decision (#01 Phase 6 resilience, its own ADR)

The verify-and-reject option was rejected because it keeps the attack surface for a UX gain we don't need yet. A local price copy was rejected as too much infrastructure for v1; it is the answer if Product Service availability becomes the limit on placing orders.

## One-minute answer

The create-order request has no price field: Order Service looks the price up in Product Service and stores it on the order. A client can't send a fake price because there is nowhere to put one. The cost is that placing an order needs Product Service up, and the price is only as trustworthy as that internal call.

## Consequences

### Positive

- Price tampering is impossible through the API, and tested
- Orders keep their original price after catalog changes

### Negative (residual risk)

- **Availability coupling.** No Product Service, no orders (`503`)
- **The price comes over an unauthenticated, unencrypted call** (T38, 8 Medium, [0009](0009-order-to-product-unauthenticated-in-v1.md), fixed in step 5 and step 4)
- **The HTTP call runs inside the database transaction** (`OrderService` is `@Transactional` at class level), so a slow Product Service holds a DB connection (T18, finding #2, step 2)
- **Stock is checked, not reserved**, so two orders can both pass the check (T16, step 5 `reserve-stock`)
- **The owner can change quantity on update without a stock check** (T15, step 6)

## Verification

- `OrderProductLookupTest.createOrder_pricesFromProductServiceAndIgnoresClientSuppliedPrice` (sends a `price` field and asserts it is ignored)
- `OrderServiceTest.create_stampsTheCallersSubAndPricesFromProductService`
- `OrderServiceTest.update_recalculatesTotalPriceAndUpdatesStatus`
- `OrderServiceTest.create_unknownProduct_propagatesAndPersistsNothing`

## Migration path

Step 5 authenticates the call and adds `reserve-stock`. If the catalog dependency becomes the bottleneck, a local price projection updated from Product Service events, with the stored snapshot rule unchanged.

## Revisit trigger

- Discounts, coupons or per-user prices (the price then depends on more than the catalog)
- Product Service downtime that blocks orders often enough to matter

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
