# Architecture Decision Records

Why the system is built the way it is. One file per decision, numbered once for the whole repo: `NNNN-short-title.md`. Security decisions carry `Category: security`; there is no separate series.

Pass mark: 7/10
Target: 8/10

## Index

| # | Title | Category | Impact | Status | Score | Step |
|---|---|---|---|---|---|---|
| [0001](0001-keycloak-as-the-only-authorization-server.md) | Keycloak as the only Authorization Server | security | high | Proposed | | 1 (#05) |
| [0002](0002-bff-tokens-stay-in-the-gateway.md) | BFF: tokens stay in the Gateway, the browser holds only a session cookie | security | high | Proposed | | 1 (#05) |
| [0003](0003-gateway-checks-session-services-enforce-rules.md) | The Gateway checks only "is there a session"; services enforce the rules | security | high | Proposed | | 1 (#05) |
| [0004](0004-owner-from-token-sub-404-for-others.md) | Owner from the token's `sub`, `404` for other users' data | security | normal | Proposed | | 1 (#05) |
| [0005](0005-price-from-the-catalog-never-from-the-client.md) | Price from the catalog, never from the client | security | normal | Proposed | | 1 (#05) |
| [0006](0006-realm-as-code-secrets-as-placeholders.md) | Realm as code, secrets as `${...}` placeholders | security | normal | Proposed | | 1 (#05) |
| [0007](0007-fail-at-startup-on-a-missing-secret.md) | Fail at startup on a missing secret | security | normal | Proposed | | 1 (#05) |
| [0008](0008-schema-per-service-on-one-postgres.md) | Schema per service on one Postgres instance | architecture | normal | Proposed | | 1 (#05) |
| [0009](0009-order-to-product-unauthenticated-in-v1.md) | Order → Product call unauthenticated in v1 | security | high | Proposed | | 1 (#05) |
| [0010](0010-dev-login-password-grant-behind-a-flag.md) | Dev login: password grant behind a property and the `dev` profile | security | normal | Proposed | | 1 (#05) |
| [0011](0011-realm-roles-and-per-service-audience.md) | Realm roles for `user`/`admin`, an audience per service | security | high | Proposed | | 1 (#05) |
| [0012](0012-accept-single-keycloak-instance.md) | Accepted risk: single Keycloak instance (T30) | security | normal | Proposed | | 1 (#05) |
| [0013](0013-accept-unencrypted-pii-at-rest.md) | Accepted risk: profile PII unencrypted at rest (T33) | security | normal | Proposed | | 1 (#05) |

0001 to 0011 are retroactive: they record decisions #01 v1 already made. 0012 and 0013 accept risks from the [threat model](../threat-model/risk-register.md).

## Format

[MADR](https://adr.github.io/madr/) with a few additions. Every ADR has:

- A header table: `Status`, `Date`, `Category`, `Impact`, `Deciders`, `Step`, `Drivers` (risk IDs, findings, requirements), `Supersedes`, `Superseded by`. On an override: `Accepted with override: <score>/10, <date>, reason: <reason>`
- Context and problem statement, decision drivers, considered options (table with pros and cons, including the rejected option people will ask about)
- Decision outcome, then a **one-minute answer**: the decision, the main reason and the main cost, in about three sentences
- Consequences, with the negatives stated as residual risk
- How the decision is verified (code or test names)
- A migration path, a revisit trigger, and for security ADRs a review date
- A review record at the bottom

`Impact: high` means the decision crosses services or sets a security boundary.

## Status

`Proposed` while its step is open, `Accepted` when the step's PR merges, `Superseded by NNNN` when a later step changes it. An Accepted ADR's decision is never edited in place: write a new ADR that supersedes it.

## Review

The reviewer is the `architecture-kata-reviewer` skill in ADR mode; the approver is Fahim. Full flow: `Interview-Preparation/project-ideas/README.md` → ADR Convention.

1. Grade: `/architecture-kata-reviewer adr docs/adr/<file>`, or the folder for a whole step. Run it in a fresh session if Claude helped draft the ADR in the current one.
2. Score /10 over five dimensions (context and drivers, options, decision and rationale, consequences and risk, accuracy and verifiability), 0–2 each. Hard gates cap the score at 6.
3. Score at or above the pass mark: ACCEPT. Between the pass mark and the target: ACCEPT (below target), with the changes that would reach the target. Below the pass mark: REVISE, then re-grade.
4. `Impact: high` needs a passed defense too: Fahim answers the reviewer's objection in drill mode before the ADR is accepted.
5. Only Fahim can accept below the pass mark, recorded as an override.
6. The reviewer's table goes into the ADR's review record; the final score goes into the index above.

Raising the bar: the pass mark goes to 8 once the last 5 Accepted ADRs average 8 or more, and to 9 once 10 or more Accepted ADRs average 9 or more.
