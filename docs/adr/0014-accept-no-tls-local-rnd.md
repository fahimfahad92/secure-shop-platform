# 0014. Accepted risk: no TLS on any flow, local R&D only (T1, T37, T38, T39)

| | |
|---|---|
| Status | Proposed |
| Date | 2026-11-06 |
| Category | security |
| Impact | high |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5) |
| Drivers | T37 (no TLS on any flow, 12 High); T1 (12 High); T38 (8 Medium, the reply-integrity half); T39 (9 Medium) |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

Every flow in the system is plain HTTP or plain JDBC (T37). The services, the Gateway, Keycloak and Postgres all run on one developer machine. The apps run on the host, and Keycloak and Postgres run in Docker Compose with ports published to the host. Every URL in the configuration is `http://localhost:…` or `jdbc:postgresql://localhost:…`, so today the traffic stays on the loopback interface.

The threat model scores the register as if v1 ran unchanged on a shared network, and on that basis four threats depend on TLS:

- **T1:** the session cookie can be read off the network, and without TLS it can't carry the `Secure` flag
- **T37:** passwords, client secrets, tokens and database credentials are readable and changeable in transit
- **T38:** a forged reply from Product Service sets an order's price ([0005](0005-price-from-the-catalog-never-from-the-client.md), [0009](0009-order-to-product-unauthenticated-in-v1.md)). Step 5's service-to-service authentication proves who is calling. It doesn't prove the reply is genuine
- **T39:** services fetch Keycloak's signing keys (JWKS) over HTTP, so whoever answers for Keycloak can mint tokens every service accepts

The register planned TLS for step 4. This is a local R&D project: the learning goals are OAuth2, the BFF, service-to-service auth and DevSecOps, not certificate operations, and nothing runs beyond one laptop.

Do we set up TLS, or accept running without it while the project stays local?

## Decision drivers

- No deployment beyond one developer machine is planned; all traffic is loopback today
- Caller identity on the internal call is still needed, and step 5 delivers it without TLS
- Certificate work (a local CA, a keystore and a truststore per service, Keycloak HTTPS and hostname, Postgres SSL) competes with the project's learning goals
- The risk must come back the moment the system leaves a single machine

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **Accept: no TLS; step 5 adds service-to-service auth for caller identity** | No certificate work; effort stays on the project's goals; step 5 still closes the spoofing half of T38 | Everything in T37 is readable and changeable by anyone who can see the traffic; no `Secure` cookie; reply and JWKS integrity rest on the network |
| TLS everywhere with a local CA (`mkcert`), Spring Boot SSL bundles, Keycloak in HTTPS, Postgres `sslmode=verify-full` | Closes T1, T37, T39 and the tampering half of T38 | A keystore and truststore per service, a fixed Keycloak hostname, `https://` issuer URIs everywhere, certificates in Compose; protects loopback traffic nobody else can see |
| TLS only on the integrity-critical flows: Order → Product (F6) and JWKS (F8) | Covers the two flows where a forged reply does the most damage | Still needs the CA, truststores and Keycloak HTTPS; leaves cookies and credentials (T1, T37) open, so half the work for part of the benefit |
| mTLS between services instead of Client Credentials | Authenticates both ends and encrypts in one mechanism | Everything in the TLS-everywhere option plus a certificate per caller and its rotation; replaces step 5's planned Client Credentials work |

## Decision outcome

**Chosen: accept the risk; no TLS while the project runs on one machine.** T1, T37 and T39 become `Accepted` when this ADR is accepted. T38 is split: step 5 fixes the spoofing half (anyone calling Product Service as Order Service), and this ADR accepts the tampering half (a forged reply).

- Every flow stays plain HTTP or JDBC on `localhost`
- Step 5 adds service-to-service authentication (Client Credentials) to the Order → Product call; that work does not depend on TLS
- The session cookie stays without the `Secure` flag

Residual risk: unchanged on the register's shared-network basis (T1 12, T37 12, T38 8, T39 9). On the actual setup, an attacker must already be on the machine to see loopback traffic, and at that point they can read the processes, files and `.env` directly, so TLS would add little.

TLS everywhere was rejected because it protects loopback traffic from an attacker who, in this setup, already has more direct ways in. TLS on F6 and F8 only was rejected because it needs nearly all of the same setup for part of the benefit. mTLS was rejected because it brings all of the certificate work back.

## One-minute answer

We don't set up TLS, because this is a local R&D project where every flow is loopback on one laptop, and anyone who can see that traffic can already read the processes and secrets directly. Step 5 still adds service-to-service authentication, so Product Service knows its caller. The cost is that the session cookie, credentials, tokens, the order price and the signing keys all travel unprotected, which is not acceptable the moment the system runs anywhere shared.

## Consequences

### Positive

- No certificate authority, keystores, truststores or hostname management
- Step 4 and step 5 effort goes to hardening and service identity, which the project is about

### Negative (residual risk)

- **Session hijack by sniffing** (T1): the cookie travels in clear and has no `Secure` flag
- **Credentials and tokens in clear** (T37): passwords (F2, F7), client secrets (F3, F7), access tokens (F5) and database credentials (F9)
- **Forged price** (T38): a forged Product Service reply prices the order, even after step 5
- **Forged signing keys** (T39): a fake JWKS lets an attacker mint tokens that every service accepts
- Not acceptable for any shared network, real users or real data

## Verification

- No `server.ssl`, SSL bundle or `sslmode` in any service configuration or in `docker/docker-compose.yml`
- Every base URL, `issuer-uri` and datasource URL in `backend/*/src/main/resources/application.properties` is `http://localhost` or `jdbc:postgresql://localhost`
- Keycloak runs `start-dev` (HTTP) in `docker/docker-compose.yml`

## Migration path

Before anything runs beyond one machine: a CA (or a managed certificate service), Spring Boot SSL bundles on every service and on `ProductClient`, Keycloak in production mode with HTTPS and a fixed hostname ([0012](0012-accept-single-keycloak-instance.md) migration step 1), `https://` issuer URIs everywhere, Postgres SSL with `sslmode=verify-full`, and the `Secure` cookie on the Gateway. Step 5's Client Credentials stay as they are; TLS adds server authentication and integrity on top.

## Revisit trigger

- Any service, Keycloak or Postgres used from another machine, or deployed anywhere other than a developer laptop
- Real users or real data
- A service URL that is no longer `localhost`

Review date: 2027-05-06.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-11-06 | architecture-kata-reviewer | 8.5/10 | REVISE (defense held; ADR missing enforcement, CI trigger, step 3 note) |
