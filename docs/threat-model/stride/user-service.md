# STRIDE — E6 User Service

Legend, status values and how to update: [../README.md](../README.md). Diagram and IDs (`E…`, `F…`, `TB…`): [../system.md](../system.md). Scores and status per threat: [../risk-register.md](../risk-register.md).

| ID | STRIDE | Threat | Control today | Gap |
|---|---|---|---|---|
| T19 | I | Account enumeration: `POST /users/register` answers `409 "Username or email already registered: <name>"`, so anyone can test which usernames exist | — | Unauthenticated, no rate limit |
| T20 | D | Registration abuse: scripted sign-ups create unlimited Keycloak users and profile rows | `RegisterRequest` validation | No rate limit or CAPTCHA (documented gap) |
| T21 | T | Inconsistent identity: Keycloak user without a profile (or the reverse) after a partial failure | Compensating delete on register (`ProfileServiceTest.register_profileWriteFails_deletesTheKeycloakUserAgainAndRethrows`); orphan logged at `ERROR` | Account delete isn't transactional (review finding #1): Keycloak failure leaves a user who can log in with no profile |
| T22 | I | New user's password (passes through on F7) ends up in logs | `RegisterRequest.toString()` masks it; request bodies aren't logged | — |
| T23 | E | Stolen admin-client secret: create, change or delete any user | Secret from env with fail-fast (`KeycloakAdminPropertiesTest`); service account limited to `manage-users`, `view-users` | **Open question:** can `manage-users` grant the `admin` realm role to a user? If yes, the secret is a path to catalog admin. Verify in Phase 3 |
| T24 | T | Mass assignment on profile update: change username, email or identity | `UpdateProfileRequest` has only `fullName`, `address`, `phone`; `UserControllerSecurityTest.updateMyProfile_changesOnlyProfileFields` | — |

