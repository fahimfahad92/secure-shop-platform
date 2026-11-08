# STRIDE — E5 Order Service

Legend, status values and how to update: [../README.md](../README.md). Diagram and IDs (`E…`, `F…`, `TB…`): [../system.md](../system.md). Scores and status per threat: [../risk-register.md](../risk-register.md).

| ID | STRIDE | Threat | Control today | Gap |
|---|---|---|---|---|
| T13 | E | BOLA: read, change or delete another user's order by id | Owner in the query (`findByIdAndUserSub`), `404` not `403`. Tests: `OrderOwnershipTest` (get/update/delete another user's order → 404, unchanged), `OrderServiceTest.*ownedBySomeoneElse*` | — |
| T14 | T | Client sets its own price | No price field in `CreateOrderRequest`; `OrderProductLookupTest.createOrder_pricesFromProductServiceAndIgnoresClientSuppliedPrice` | — |
| T15 | T | Client sets order status (`CONFIRMED`, `CANCELLED`) and changes quantity with `PUT` without a stock re-check | Bean Validation on `UpdateOrderRequest` only | Documented gap: status should belong to the system |
| T16 | T | Overselling: stock checked, never decremented; two orders race for the last unit | Check-at-order-time only | Documented gap; closed by #03's `reserve-stock` |
| T17 | S | Token from another client accepted. Near miss: `user-service-admin-client`'s token carries `orders:read`/`orders:write` (realm default scopes) **and** realm role `user` (via `default-roles-secure-shop`) — only the missing `order-service` audience stops it | `jwt.audiences=order-service`; `TokenValidationTest.tokenForAnotherService_returns401`, `tokenWithKeycloakDefaultAudienceOnly_returns401`; scope + role both required (`OrderControllerSecurityTest`, 8 cases) | Realm default client scopes hand `orders:*` to every client (open nit); audience is the single line of defence here |
| T18 | D | Product Service slow → each order holds a DB connection for up to ~5 s; 10 concurrent orders drain the pool and stall every endpoint | 2 s connect / 3 s read timeouts (`ProductClientConfig`) | HTTP call inside the transaction (review finding #2); no circuit breaker |

