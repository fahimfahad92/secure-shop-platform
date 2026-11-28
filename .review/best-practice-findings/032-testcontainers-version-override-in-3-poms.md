# 32 — Testcontainers version override in 3 POMs; remove when the Boot parent is >= 3.5.16

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | GEN-DEP |
| Service | order, product, user |

**Where:** `backend/order-service/pom.xml`, `backend/product-service/pom.xml`, `backend/user-service/pom.xml`: `<testcontainers.version>1.21.4</testcontainers.version>` in `<properties>`, with a comment pointing here (commit `0cbbb6b`)

**What:** Spring Boot 3.5.5 manages Testcontainers 1.21.3. Its Docker client asks for API 1.32, and the `ubuntu-latest` runner's Docker now requires 1.40, so every integration test on PR #8 failed to start Postgres (`client version 1.32 is too old. Minimum supported API version is 1.40`). The three POMs override the version to 1.21.4 ("makes version 1.21.x work with recent Docker Engine changes"; it is managed by Boot 3.5.16). PR #8 went green with it (2026-11-14, `0cbbb6b` and `82e0c92`).

**Why it matters:** the override keeps pinning 1.21.4 after a Boot bump, silently ignoring the newer Testcontainers that later Boot versions bring. It is a test-scope pin that is easy to forget.

**Fix direction:** when the parent moves to Boot ≥ 3.5.16, delete the property and its comment in all three POMs. Check that `./mvnw dependency:tree -Dincludes=org.testcontainers` shows the Boot-managed version (≥ 1.21.4) and that `verify` passes on CI.

**When done:** mark it `Done`. Task: `Interview-Preparation/tasks/ci-testcontainers-docker-api/`.
