# STRIDE — Data flows

Legend, status values and how to update: [../README.md](../README.md). Diagram and IDs (`E…`, `F…`, `TB…`): [../system.md](../system.md). Scores and status per threat: [../risk-register.md](../risk-register.md).

| ID | Flow | STRIDE | Threat | Control today | Gap |
|---|---|---|---|---|---|
| T37 | All (F1–F9) | I/T | Plain HTTP and JDBC everywhere: cookies, passwords (F2, F7), client secrets (F3, F7), tokens (F5) and DB credentials (F9) readable and changeable on the path | — | No TLS (documented gap) |
| T38 | F6 Order → Product | S/T | Anyone can call Product Service as "Order Service", and a response can be changed in transit: a forged `price` in the reply prices the order, so price integrity depends on an unauthenticated, unencrypted call | None: the read is public and the reply is trusted as-is | No service authentication (v1 by design, #03) and no TLS |
| T39 | F8 Services → Keycloak (JWKS) | S | Whoever can answer for `localhost:8081`/the Keycloak host serves their own signing keys, and services accept tokens they mint | Issuer check; on one machine the path is short | JWKS over plain HTTP |
| T40 | F11 Direct service calls | E | Gateway-only controls are skipped. Today that's only CSRF (irrelevant for bearer calls), but **any rate limit added at the Gateway (step 6) is bypassed** while service ports stay reachable | Each service enforces its own rules (see T9–T24) | Services bound to all interfaces; no network isolation until step 3/4 containers |

