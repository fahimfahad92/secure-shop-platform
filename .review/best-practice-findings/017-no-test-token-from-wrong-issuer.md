# 17 — No test for a token from the wrong issuer (ADR-0001 round 1)

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | GEN-TEST-01 |
| Service | order, product, user |

**Where:** `AbstractIntegrationTest` and `TestJwts` in Order, Product and User Service; `TokenValidationTest` in each

**What:** Each service's `AbstractIntegrationTest` sets `spring.security.oauth2.resourceserver.jwt.issuer-uri` to `""` so Boot builds the public-key decoder, and `TestJwts` mints tokens with no `iss`. So no test runs the issuer check. `TokenValidationTest` proves the audience check only.

**Why it matters:** ADR-0001's claim "every service trusts exactly one issuer" rests on config nobody tests. If a later change drops or misspells `issuer-uri`, a token signed by a trusted key with any `iss` would pass, and nothing would fail.

**Fix direction:** First check whether Boot adds `JwtIssuerValidator` to the public-key decoder when `issuer-uri` is set alongside `public-key-location`. If yes: set a fake test issuer in `AbstractIntegrationTest`, make `TestJwts` put it in `iss`, add `request_withWrongIssuer_returns401` to each `TokenValidationTest`. If no: the services need their own `JwtDecoder` bean that combines the issuer and audience validators; test that bean the same way.

**When done:** ADR-0001 Verification → replace the "not covered by tests" line with the test names.
