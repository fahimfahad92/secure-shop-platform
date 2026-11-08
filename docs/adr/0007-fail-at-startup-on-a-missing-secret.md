# 0007. Fail at startup on a missing secret

| | |
|---|---|
| Status | Accepted |
| Date | 2026-10-24 (recorded). Decided 2026-10-04, #01 Phase 4, PR #5 |
| Category | security |
| Impact | normal |
| Deciders | Fahim |
| Step | 1 (#05 Phase 5, retroactive) |
| Drivers | Misconfiguration found during Phase 4 (IDE started before the env vars existed) |
| Supersedes | — |
| Superseded by | — |

## Context and problem statement

The Gateway and User Service read their Keycloak client secrets from environment variables (`KEYCLOAK_GATEWAY_CLIENT_SECRET`, `KEYCLOAK_TEST_CLIENT_SECRET`, `KEYCLOAK_ADMIN_CLIENT_SECRET`), referenced as `${...}` in `application.properties`.

When the variable is missing, property binding keeps the unresolved `${KEYCLOAK_..._CLIENT_SECRET}` as literal text instead of failing. The service starts normally and breaks only at the first login or registration, where Keycloak answers `401` to a secret that is the placeholder string. This happened in Phase 4 when IntelliJ had been started before the variables were set, and the `401` pointed at the wrong problem.

How should a service behave when a required secret is missing?

## Decision drivers

- A missing secret is found at deploy time, not by the first user
- The error says which variable to set
- No secret gets a default value
- Small, local code; no new dependency

## Considered options

| Option | Pros | Cons |
|---|---|---|
| **Check at startup in code: reject a blank value or one still containing `${`, name the variable in the error** | Fails before serving traffic; clear message; tested | A small check per service; only covers the secrets it lists |
| Bean validation (`@Validated` + `@NotBlank`) on the properties | Declarative, standard | The literal `${...}` text is not blank, so it passes |
| Empty default (`${KEYCLOAK_ADMIN_CLIENT_SECRET:}`) plus `@Validated` + `@NotBlank` | Declarative; a missing variable becomes blank, which `@NotBlank` catches. Works for User Service's own `KeycloakAdminProperties` record | Doesn't reach the Gateway: its secrets bind into Boot's `OAuth2ClientProperties`, which we can't annotate and which only checks that `client-id` is set. Every property needs the `:` suffix, and forgetting it brings the literal back silently. Two mechanisms for one rule |
| Leave it; rely on the runtime `401` | Nothing to build | Late, misleading failure; looks like a wrong password or wrong client |
| Check outside the app (compose `:?`, a start script) | No app code | Doesn't cover starting from the IDE, which is where it happened |

## Decision outcome

**Chosen: a startup check in each service that holds a secret.**

- Gateway: `ClientSecretCheck` (`InitializingBean`) walks every client registration and throws `IllegalStateException` if the secret is blank or contains `${`. It maps each client id to its variable name
- User Service: the compact constructor of the `KeycloakAdminProperties` record does the same check at binding time
- Both messages name the variable and point to `docker/.env`, and mention that an IDE only sees variables that existed when it started
- Order and Product Service hold no secrets, so they have no check

`@NotBlank` was rejected because it doesn't catch the placeholder text, which is the actual failure. The empty-default variant does catch it, and is the runner-up, but only in User Service. The Gateway needs a custom check anyway, because Boot owns its client properties. So the choice is between one rule written the same way in both services, or a hand-written check in the Gateway plus bean validation in User Service. One rule wins on review and on the step 2 shared-code move: the same check, the same message, the same tests, and nothing that depends on remembering a `:` suffix.

## One-minute answer

If a client secret is missing, the service refuses to start and names the environment variable to set. Without that, Spring keeps the unresolved placeholder as the secret, the service starts, and the first login fails with a confusing `401` from Keycloak. The cost is a small check in each service, which only covers the secrets it knows about.

## Consequences

### Positive

- Misconfiguration shows up in seconds, with the fix in the message
- No default secret anywhere

### Negative (residual risk)

- **Two implementations of one rule** (Gateway and User Service). Part of the shared-code question (finding #13, step 2)
- **Database passwords aren't covered.** They are hard-coded as `postgres` in each `application.properties` (T32, step 2)
- **A wrong secret still starts.** The check proves presence, not correctness; a wrong secret shows at the first Keycloak call (`DevLoginTest.wrongClientSecret_returns502_notAWrongPasswordMessage` keeps that error distinct)
- **Heuristic.** A real secret that contains `${` would be rejected. Generated hex secrets never do

## Verification

- `ClientSecretCheckTest.realSecret_passes`, `unresolvedPlaceholder_failsStartup_namingTheVariable`, `blankSecret_failsStartup`
- `KeycloakAdminPropertiesTest.realSecret_binds`, `unresolvedPlaceholder_failsStartup_namingTheVariable`, `missingSecret_failsStartup`

## Migration path

When step 4 moves secrets to Docker secrets (files) or a vault, the same rule applies to whatever source replaces the environment variable. If more services get secrets, move the check into shared code per the step 2 decision.

## Revisit trigger

- Secrets delivered by something other than environment variables (step 4)
- A third service that needs a secret

Review date: 2027-04-24.

## Review record

| Round | Date | Reviewer | Score | Verdict |
|---|---|---|---|---|
| 1 | 2026-10-24 | architecture-kata-reviewer | 8.5/10 | ACCEPT |
| 2 | 2026-11-07 | architecture-kata-reviewer | 9.5/10 | ACCEPT |
