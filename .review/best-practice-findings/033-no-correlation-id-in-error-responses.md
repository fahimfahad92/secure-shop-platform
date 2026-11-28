# 33 — No correlation ID in error responses: a generic client error can't be matched to its log line

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | GEN-OBS |
| Service | order, product, user, gateway |

**Where:** every service's `GlobalExceptionHandler.body()` (order, product, user), and the Gateway's error responses. The body is `timestamp`, `status`, `error` and `message`, with no request or trace ID. No MDC request ID in the log pattern either.

**What:** since #31, a database failure answers the same generic `503` "Service temporarily unavailable, try again later" whether the pool is busy or the database is down, and only the log tells them apart (WARN `DB pool exhausted` vs. ERROR `DB unreachable`). That's deliberate (CWE-209). But when a customer reports an error, support has nothing to look up: no ID links the client's response to the server's log line. They can only search by time, which fails under load and across replicas.

**Why it matters:** generic client errors are only safe to keep if the server side stays easy to debug. Without a correlation ID, hiding the detail costs real support time. Found in the #31 interview drill (`Interview-Preparation/tasks/completed/db-failure-generic-error-log-levels/drill.md`, Q1).

**Fix direction:**
- Generate or accept a request ID at the Gateway (`X-Request-Id`, or the W3C `traceparent` trace ID once step 6 adds tracing) and forward it to the services.
- Put it in the MDC so every log line carries it.
- Return it in every error body, e.g. `instance` or a `traceId` extension in an RFC 9457 `ProblemDetail`.

Do it with #7 (one error format), which reshapes the body anyway. The ID is safe to expose; it carries no internal detail.

**Understand first:** Micrometer Tracing (Boot 3) puts `traceId`/`spanId` in the MDC automatically. `ProblemDetail`'s `instance` field vs. a custom property. Trust: never accept a client-supplied ID without validating its format (log injection).
