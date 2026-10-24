# 0009. Order → Product call unauthenticated in v1

| | |
|---|---|
| Status | Proposed |
| Date | 2026-10-24 (recorded). Decided 2026-09-26, #01 Phase 2, PR #2 |
| Category | security |
| Impact | high |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5, retroactive) |
| Drivers | T38 (8 Medium), T40, T16; project #03 (step 5) |
| Supersedes | — |
| Superseded by | — (to be superseded by the step 5 service-to-service auth ADR, #03) |

## Context and problem statement

When an order is placed, Order Service calls `GET /products/{id}` on Product Service for the price and stock ([0005](0005-price-from-the-catalog-never-from-the-client.md)). This is the only service-to-service call in v1. The endpoint is public: anyone, logged in or not, can read it through the Gateway.

Service-to-service authentication is its own project (#03). Doing it in Phase 2 would have mixed two learning goals into one phase.

Should the Order → Product call carry credentials in v1?

## Decision drivers

- The call reads only public data
- v1's goal is user-facing OAuth2: the BFF, resource servers, ownership
- Service-to-service auth deserves a deliberate choice, not a quick one
- Nothing should rely on the call being unauthenticated once it changes

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **No credentials in v1; plain HTTP to the public endpoint; secured in #03** | Nothing to build now; the call can do nothing an anonymous caller can't | Product Service can't tell Order Service from anyone else; replies can be forged by anyone on the network path |
| Relay the user's access token | Already available; `aud` already contains `product-service` | Ties a system call to the user's session; Product gets a user token it doesn't need; wrong identity once Order Service makes system calls (`reserve-stock`) |
| Client Credentials token for Order Service | Standard; short-lived; scoped | The #03 work; needs a new Keycloak client, token caching, audience pinning |
| mTLS between services | Both ends authenticated; also encrypts | Certificate issuing and rotation without a mesh; heavy for a laptop |
| Shared API key | Easy | Static secret, no rotation, no audit; worse than the others |

## Decision outcome

**Chosen: no credentials in v1, by design, with #03 (step 5) as the owner of the fix.**

- `ProductClient` calls Product Service over plain HTTP with no `Authorization` header; its Javadoc flags it as the retrofit point for #03
- Connect timeout 2 s, read timeout 3 s (`ProductClientConfig`); an error or timeout becomes `503`
- Hard rule from here on: **no non-public endpoint is ever called service-to-service without authentication.** `reserve-stock` in step 5 is a write, so it can't ship before #03's auth does

Relaying the user's token was rejected because the caller of `reserve-stock` will be Order Service, not the user. Client Credentials vs mTLS is #03's decision.

## One-minute answer

In v1 Order Service calls Product Service without credentials, because the call only reads the public catalog and service-to-service auth is its own project. Securing it then would have mixed two learning goals into one phase, and it gives nothing an anonymous user doesn't already have. The cost is that the order price trusts an unauthenticated, unencrypted reply, and the call must be secured before any write endpoint goes service-to-service.

## Consequences

### Positive

- Phase 2 stayed focused on the catalog and admin-gated writes
- #03 has a real, single call site to secure

### Negative (residual risk)

- **Forged replies set the price.** Anyone with a man-in-the-middle position on the internal network can change the `price` in the reply (T38, 8 Medium). Low likelihood while everything is on one machine; every order is affected when it happens. Fixed by step 5 (auth) and step 4 (TLS)
- **No caller identity at Product Service.** No per-caller limits, no audit of which service read what
- **Service ports reachable directly** (T40), so "internal" means nothing yet. Step 3 publishes only the Gateway port
- **Stock isn't reserved** (T16); the fix, `reserve-stock`, needs this decision reversed first

## Verification

- `ProductClient` sets no `Authorization` header (code). There is no test for the absence; step 5 adds tests that the header is present and that Product Service rejects the call without it
- `OrderProductLookupTest` covers the price, not-found and unavailable paths

## Migration path

Step 5 (#03): a Client Credentials client for Order Service, a cached service token, Product Service checking `aud=product-service` and a service scope on `reserve-stock`. This ADR is then superseded.

## Revisit trigger

- Any service-to-service call to a non-public endpoint (first one: `reserve-stock`)
- The services running on a shared network (step 3 containers)

Review date: when step 5 starts.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
