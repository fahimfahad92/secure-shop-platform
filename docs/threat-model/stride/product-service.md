# STRIDE — E4 Product Service

Legend, status values and how to update: [../README.md](../README.md). Diagram and IDs (`E…`, `F…`, `TB…`): [../system.md](../system.md). Scores and status per threat: [../risk-register.md](../risk-register.md).

| ID | STRIDE | Threat | Control today | Gap |
|---|---|---|---|---|
| T9 | E | Non-admin changes the catalog (e.g. sets a price to 0.01) | `hasRole("admin")` on writes. Tests: `ProductControllerSecurityTest.createProduct_withoutAdminRole_returns403`, `deleteProduct_withoutAdminRole_returns403`, `TokenValidationTest.userTokenForThisService_withoutAdminRole_returns403` | No test for `PUT` without `admin`, the write that changes prices |
| T10 | S | Token minted for another service or client accepted | `jwt.audiences=product-service`; `TokenValidationTest.adminTokenForAnotherService_returns401`, `publicRead_withTokenForAnotherService_returns401` | — |
| T11 | T | Mass assignment: client sets `id`, timestamps or other fields | `CreateProductRequest` / `UpdateProductRequest` records; entity never bound | — |
| T12 | D | Anonymous `GET /products` returns the whole table, no rate limit | — | No pagination (review finding #3), no rate limit (documented gap) |

