# 12 — Inconsistent config binding

| | |
|---|---|
| Status | Open |
| Severity | SUGGESTION |
| Rule | SB-CFG-02 |
| Service | order, gateway |

**Where:** `order-service/.../config/ProductClientConfig.java:15-17`, `gateway/.../config/RouteConfig.java:38-40` (`@Value`) vs `user-service/.../config/KeycloakAdminProperties.java` (validated `@ConfigurationProperties` record)

**What:** Two binding styles for the same kind of config; the `@Value` ones also duplicate defaults (`:2000`) already set in properties.

**Fix direction:** `ProductServiceProperties` record (`baseUrl`, `connectTimeout`, `readTimeout` as `Duration`) with `@Validated` + `@NotNull`; same for Gateway upstream URLs.
