# STRIDE — E7 Keycloak

Legend, status values and how to update: [../README.md](../README.md). Diagram and IDs (`E…`, `F…`, `TB…`): [../system.md](../system.md). Scores and status per threat: [../risk-register.md](../risk-register.md).

| ID | STRIDE | Threat | Control today | Gap |
|---|---|---|---|---|
| T25 | S | Password guessing on the login page or token endpoint | Registration enforces 8+ characters (`RegisterRequest`) | `bruteForceProtected: false`, no realm password policy, no MFA |
| T26 | S | Direct password grant: `secure-shop-test-client` has direct access grants on in the realm itself, whatever the Gateway profile. With its secret, anyone exchanges username + password for tokens straight at Keycloak (no PKCE, no login page) and calls services directly (F11) with all three audiences | Secret in `docker/.env` | The client exists wherever the realm is imported |
| T27 | E | Keycloak admin console with `admin`/`admin`, published on all interfaces (TB7): full control of users, roles, clients, secrets and signing keys | — | Default credentials hardcoded in `docker-compose.yml` |
| T28 | R | No record of logins, failed logins or admin changes | — | `eventsEnabled: false`, `adminEventsEnabled: false` |
| T29 | E | Stolen refresh token stays usable for the SSO session (idle 30 min, max 10 h) | Tokens live only in the Gateway session; access token 5 min | Refresh rotation off (documented gap) |
| T30 | D | Keycloak down: no login, no refresh, no registration | Services keep validating with cached JWKS | Single instance (accepted for this project) |

