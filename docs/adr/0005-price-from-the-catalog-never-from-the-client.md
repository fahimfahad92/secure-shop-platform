# 0005. Price from the catalog, never from the client

| | |
|---|---|
| Status | Proposed |
| Date | 2026-10-24 (recorded). Decided 2026-09-26, #01 Phase 2, PR #2 |
| Category | security |
| Impact | normal |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5, retroactive) |
| Drivers | T14 (client sets its own price), T38; OWASP API Security Top 10, API3 (broken object property level authorization: mass assignment) |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

Before Phase 2 (PR #2) there was no catalog, so the client sent the price. PR #1's `CreateOrderRequest` was `(productId, quantity, unitPrice)`, and `OrderService.create` stored that `unitPrice` and computed the total from it. The only check was `@DecimalMin("0.0", inclusive = false)`, so `0.01` passed, and `productId` was never checked because there were no products to check it against.

Phase 2 adds Product Service, which owns each product's `price` and `stock`. Order Service can now keep taking the price from the client or look it up.

Keeping the client's price lets anyone order a 99.99 keyboard for 0.01 with one request. Authentication doesn't help: the caller is a real, logged-in customer ordering for themselves. The flaw is a client setting a field it should never own, which is OWASP API3 (broken object property level authorization, the mass-assignment half), a business-logic flaw rather than an access-control one.

Two more forces come with a lookup. An order must keep the price it was placed at, so a later catalog change doesn't rewrite past orders. And placing an order now depends on a second service that can be missing or down.

The lookup moves trust from the public API to the internal call from Order Service to Product Service. This is a local R&D project: everything runs on one machine and every call goes to `localhost`, so TLS is out of scope and won't be set up ([0014](0014-accept-no-tls-local-rnd.md)). Step 5 will secure the internal call with service-to-service authentication instead (a Client Credentials token from Keycloak, see [0009](0009-order-to-product-unauthenticated-in-v1.md)), so Product Service knows the caller is Order Service. Caller authentication doesn't protect the reply, so the price can be trusted only as far as the local network can. That risk is accepted in [0014](0014-accept-no-tls-local-rnd.md).

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
- **The price comes over an unauthenticated, unencrypted call** (T38, 8 Medium, [0009](0009-order-to-product-unauthenticated-in-v1.md)). Step 5 adds service-to-service authentication, so the call is authenticated. TLS is out of scope for this local R&D project, so a reply forged on the local network stays an accepted risk ([0014](0014-accept-no-tls-local-rnd.md))
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

Review date: 2027-10-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 0 | 2026-10-24 | architecture-kata-reviewer (same session as drafting, baseline) | 9/10 | ACCEPT |
| 1 | 2026-10-24 | architecture-kata-reviewer | 9.5/10 | ACCEPT |
| 2 | 2026-11-06 | architecture-kata-reviewer | 9/10 | ACCEPT |
| 3 | 2026-11-06 | architecture-kata-reviewer | 10/10 | ACCEPT |
