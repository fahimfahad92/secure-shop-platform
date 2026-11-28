# 19 — Wildcard matchers on permissive rules (ADR-0003)

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | SB-SEC |
| Service | product, order, gateway |

**Where:** Product `SecurityConfig` (`GET /products/**` `permitAll`), Order `SecurityConfig` (`GET /orders/**` read + `user`), Gateway `SecurityConfig` (`GET /products/**` `permitAll`) and `RouteConfig` (`publicRoute("product-read", GET, "/products/**")`, no token relay)

**What:** A new `GET` under `/products` is public on day one. A new `GET` under `/orders` needs only read scope + `user`, so a `GET /orders/all` that forgets `sub` leaks every user's orders.

**Why it matters:** Fail-closed `anyRequest()` defaults never apply to routes a wildcard already matches.

**Fix direction:** Exact routes: `GET /products`, `GET /products/{id}`; exact Order `GET` routes. Change both deployables: if only Product Service is narrowed, a future admin `GET /products/...` still hits the Gateway's public route, gets no token, and always answers `401`.

**When done:** ADR-0003 → move item 2 of "Scheduled hardening" into Decision outcome and update the Gateway rules list; drop the "Wildcard matchers" residual risk once #20 also lands.
