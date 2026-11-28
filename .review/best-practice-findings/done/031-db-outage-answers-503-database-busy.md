# 31 — A DB outage answers 503 "Database busy" and logs at WARN; 500 branch untested

| | |
|---|---|
| Status | Done 2026-11-14 (`82e0c92`) |
| Severity | MINOR |
| Rule | SB-API |
| Service | order, product, user |

**Status:** Done 2026-11-14, commit `82e0c92` on `review-findings`. Busy and unreachable both answer `503` "Service temporarily unavailable, try again later" with no database detail or stack trace; the log tells them apart: WARN `DB pool exhausted` (Hikari timeout without a cause) vs. ERROR `DB unreachable` with the cause's stack. Other failures stay `500` "Internal error". Tests: `GlobalExceptionHandlerTest` (busy, unreachable, other; body has only the 4 generic keys) and `PoolTimeoutTest` (no `trace`/`exception`). Manual check with Postgres stopped: `503` generic body after 3.0 s, ERROR with `PSQLException: Connection to localhost:5432 refused`; back to `200` after a restart. Task: `Interview-Preparation/tasks/db-failure-generic-error-log-levels/`.

**Where:** `GlobalExceptionHandler#handleNoTransaction` and `isPoolTimeout` in order-, product- and user-service (added by #29, commit `97b7dca`)

**What:** Hikari throws `SQLTransientConnectionException` for every `getConnection()` that times out. That includes a pool that can't open connections because Postgres is down; then the last connection error is the cause. So an outage after startup gets the same answer as a busy pool: `503` "Database busy, try again", logged at WARN. The handler's Javadoc says an outage "stays a 500", which is wrong. Seen on 2026-11-14 with product-service running and the Postgres container stopped: `GET /products` → `503` after 3.08 s, log `No database connection free within the pool timeout: Connection refused: getsockopt`. Also, no test covers the handler's `500` branch.

**Why it matters:** `503` is a fair answer for an outage. But the message tells the client it's load, and WARN hides an outage among routine saturation warnings. An alert on ERROR would miss it.

**Fix direction:** keep `503`, but split the cases. If the `SQLTransientConnectionException` has a cause (Hikari couldn't connect), log at ERROR and answer "Database unavailable". With no cause, keep "Database busy" at WARN. Fix the Javadoc. Add a test for the `500` branch, e.g. a `CannotCreateTransactionException` whose cause isn't a pool timeout, thrown from a mocked repository. Small; it can ride along with #7 (one error shape) or go before #30.

**Also noted in the same review (not separate findings):**
- **One WARN per rejected request** under saturation. Hikari's pool metrics (`hikaricp.connections.timeout`) are the better signal: step 6 observability.
- **`Retry-After: 1` is shorter than the 3 s pool wait.** Clients that honour it would retry in sync while the pool is still full.
- **Security review, INFO:** the smaller pool (10 → 5) halves the number of concurrent anonymous `GET /products` calls needed to fill it. The 3 s timeout bounds the effect; #3 (pagination) and rate limiting (step 6) are the real fixes.
