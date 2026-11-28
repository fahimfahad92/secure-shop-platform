# 29 — HikariCP not configured: defaults everywhere, 30 s connection wait

| | |
|---|---|
| Status | Done 2026-11-14 (`97b7dca`) |
| Severity | MINOR |
| Rule | SB-CFG |
| Service | order, product, user |

**Status:** Done 2026-11-14, commit `97b7dca` on `review-findings` (with #4). Each service: `pool-name=<service>-pool`, `maximum-pool-size=5`, `minimum-idle=2`, `connection-timeout=3000`, `leak-detection-threshold=2000`; a pool timeout answers `503` + `Retry-After: 1` (`PoolTimeoutTest.exhaustedPoolAnswers503`), tests `<Service>ApplicationTests.poolIsConfigured`. Values and reasons: `docs/development.md` → Database connections. Follow-up: #31 (an outage also reads as "busy").

**Where:** `application.properties` of order-service, product-service, user-service (no `spring.datasource.hikari.*` property anywhere)

**What:** Every service runs Hikari's defaults: `maximum-pool-size` 10, `minimum-idle` = pool size, `connection-timeout` 30 s, `idle-timeout` 10 min, `max-lifetime` 30 min, `leak-detection-threshold` off, pool name `HikariPool-1` in all three.

**Why it matters:**
- A request that can't get a connection waits up to 30 s before failing. With 2 s + 3 s timeouts on the Product call, a 30 s pool wait is the slowest part of any outage, and it ties up a web thread the whole time
- Nothing reports a connection held too long, so #2- and #4-type holds stay invisible
- Logs and metrics can't tell the three pools apart

**Fix direction:** Per service, in `application.properties`:
- `spring.datasource.hikari.pool-name=<service>-pool`
- `maximum-pool-size`: small and explicit (start at 5; 3 services × 5 stays far under Postgres' default `max_connections` of 100, and step 2's per-service roles can add a `CONNECTION LIMIT` to match)
- `minimum-idle` lower than the max (e.g. 2) for a laptop
- `connection-timeout=3000` (fail fast, answer `503`)
- `leak-detection-threshold=2000` in dev (logs a stack trace for any connection held over 2 s)
- `max-lifetime` below any network or Postgres idle cut-off (keep the 30 min default unless one exists)
- Map `SQLTransientConnectionException` (pool timeout) to `503` in each service's error handler, not `500`

**Understand first:** why a smaller pool is often faster (HikariCP "About Pool Sizing"); what the leak detection log shows and why it isn't a leak by itself; how `connection-timeout` interacts with the web server's thread pool.

**When done:** note the values in `docs/development.md`; #4 and #30 use the leak detection log as their check.
