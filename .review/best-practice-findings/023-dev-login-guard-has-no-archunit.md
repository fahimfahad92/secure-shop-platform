# 23 — Dev-login guard has no ArchUnit rule and no startup-failure test (ADR-0010)

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | GEN-TEST-01 |
| Service | gateway |

**Where:** `backend/gateway/src/test/java/com/fahim/gateway/devlogin/`, `backend/gateway/pom.xml`

**What:** Dev login is safe because every bean in `devlogin` carries `@ConditionalOnDevLogin`, and because `KeycloakPasswordGrant` refuses to start without the `keycloak-test` registration. Neither is tested: a new unguarded bean would compile and pass, and the startup failure is code only (ADR-0010 Verification says so).

**Why it matters:** A regression in the guard puts a password endpoint into every Gateway start, not only `dev`.

**Fix direction:**
- ArchUnit (`archunit-junit5`, test scope; shared with #21), test class in the `devlogin` package because `ConditionalOnDevLogin` is package-private: every `@Component`/`@RestController` in `..devlogin..` is annotated with `ConditionalOnDevLogin`; no class outside `..devlogin..` depends on `KeycloakPasswordGrant`. `SecurityConfig`'s `ObjectProvider<DevLoginLogoutHandler>` is the one allowed outside dependency
- `ApplicationContextRunner` test: `secure-shop.dev-login.enabled=true` without the `keycloak-test` registration fails with the "Run with the dev profile" message
- Optional: move `application-dev.properties` out of `src/main/resources` (load it with `spring.config.additional-location` locally), so the deployed jar can't activate dev login by profile name

This is regression protection, not the T7 fix: the step 4 startup guard (refuse `dev-login.enabled=true` outside a local environment) stays the fix.

**When done:** ADR-0010 Verification → replace "No test covers the startup failure" with the test names.
