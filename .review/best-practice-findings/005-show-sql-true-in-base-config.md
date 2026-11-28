# 5 — `show-sql=true` in base config

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | GEN-OTHER |
| Service | order, product, user |

**Where:** `application.properties:9` in order-service, product-service, user-service

**What:** `spring.jpa.show-sql=true` prints every SQL statement with `System.out`, in every environment.

**Why it matters:** Bypasses the logging framework (no levels, no JSON format, no correlation IDs), can't be turned off at runtime, and floods logs under load.

**Fix direction:** Remove it. For local debugging use `logging.level.org.hibernate.SQL=debug` (+ `org.hibernate.orm.jdbc.bind=trace` for parameters) in a dev profile or local override.
