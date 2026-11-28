# 7 — Two error response shapes

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | SB-API-02 |
| Service | order, product, user |

**Where:** `GlobalExceptionHandler` in order-service, product-service, user-service (hand-built `Map` with `timestamp/status/error/message`)

**What:** Handled exceptions return the custom map. Anything not handled there — malformed JSON (`HttpMessageNotReadableException`), `/orders/abc` (`MethodArgumentTypeMismatchException`), 405, 415 — falls through to Spring's default error body, which has a different shape.

**Why it matters:** Clients (the coming frontend) need two parsers or break on the unhandled cases. Shape is defined three times by hand.

**Fix direction:** Extend `ResponseEntityExceptionHandler` and return `ProblemDetail` (RFC 9457) for the custom exceptions too; `spring.mvc.problemdetails.enabled=true`. Then one shape everywhere, standard `type/title/status/detail/instance` fields.

**Understand first:** RFC 9457 fields; how `ResponseEntityExceptionHandler` already maps the built-in MVC exceptions.
