# 4 — `open-in-view` not disabled

| | |
|---|---|
| Status | Done 2026-11-14 (`97b7dca`) |
| Severity | MINOR |
| Rule | SB-CFG-08 |
| Service | order, product, user |

**Status:** Done 2026-11-14, commit `97b7dca` on `review-findings` (with #29). `spring.jpa.open-in-view=false` in order, product and user service; test `<Service>ApplicationTests.openInViewIsOff`. Task: `Interview-Preparation/tasks/osiv-off-hikari-config/`.

**Where:** `application.properties` of order-service, product-service, user-service (property absent)

**What:** `spring.jpa.open-in-view` defaults to `true`: the Hibernate session stays open for the whole web request, including JSON serialisation.

**Why it matters:**
- **Connections held per request.** The open session keeps its JDBC connection from the first query until the response is written, including serialisation and any remote call made after the first query. A slow request holds a pool connection for its whole length, the same pool-exhaustion failure as #2, on every endpoint. It isn't a transaction, but it holds the connection like one.
- Lazy associations can be loaded silently from the controller/serialiser, hiding N+1 queries until production. No lazy associations today, so no current bug there.
- Boot logs a warning about it at every startup.

**Fix direction:** `spring.jpa.open-in-view=false` in all three; run the tests. With #30 (no `@Transactional`), each repository call opens and closes its own session, so once this is off, no code path holds a connection longer than one query. Verify with #29's `leak-detection-threshold`.

**Understand first:** `OpenEntityManagerInViewInterceptor`, and what `LazyInitializationException` tells you once it's off.
