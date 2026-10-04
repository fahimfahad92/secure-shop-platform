# User Service

Registration and user profiles. Keycloak owns the account (username, email, password, roles). This service owns the profile data around it (full name, address, phone), keyed by the Keycloak user id (`sub`).

| | |
|---|---|
| Port | `8083` |
| Code | `backend/user-service` |
| Type | OAuth2 resource server (validates JWTs, stateless), plus a Keycloak Admin API client |
| Database | Postgres, schema `user_schema`, table `profiles` |
| Called by | Gateway |
| Calls | Keycloak token endpoint and Admin REST API |
| Secret | `KEYCLOAK_ADMIN_CLIENT_SECRET`, required at startup |

## Endpoints

There is no `/users/{id}`. A user can only ever reach their own profile, through `/users/me`.

| Method | Path | Who can call | Success |
|---|---|---|---|
| `POST` | `/users/register` | Anyone | `201`, profile in the body |
| `GET` | `/users/me` | Any logged-in user | `200`, or `404` if there's no profile |
| `PUT` | `/users/me` | Any logged-in user | `200` |
| `DELETE` | `/users/me` | Any logged-in user | `204` |

<details>
<summary>Request and response bodies</summary>

Register:

```json
{ "username": "alice", "email": "alice@example.com", "password": "s3cret-pass",
  "fullName": "Alice Smith", "address": "Dhaka", "phone": "+8801..." }
```

| Field | Rules |
|---|---|
| `username` | Required, 3 to 100 characters |
| `email` | Required, valid email, up to 255 |
| `password` | Required, 8 to 100 characters |
| `fullName`, `address`, `phone` | Optional, up to 200 / 500 / 30 |

Update takes only `fullName`, `address` and `phone`. Sending `username`, `email` or `password` changes nothing: those belong to Keycloak.

Response:

```json
{ "keycloakSub": "7663bcb4-...", "username": "alice", "email": "alice@example.com",
  "fullName": "Alice Smith", "address": "Dhaka", "phone": "+8801...",
  "createdAt": "2026-10-04T10:00:00Z", "updatedAt": "2026-10-04T10:00:00Z" }
```

</details>

## How security works

Registration is open, because a new user has no token yet. Everything else needs a valid token meant for this service, and works only on the caller's own profile.

| Request | Result |
|---|---|
| `POST /users/register` with no token | Allowed |
| `/users/me` with no token | `401` |
| Token without `user-service` in `aud` | `401` |
| Valid token | Allowed, on the profile whose key is the token's `sub` |

<details>
<summary>How the token is checked</summary>

1. Spring Security's resource server validates the JWT: signature against Keycloak's JWKS, issuer, expiry.
2. `jwt.audiences=user-service` rejects a token without `user-service` in `aud` (`401`).
3. `POST /users/register` is `permitAll`. Everything else is `authenticated()`. No role or scope check: any logged-in user may manage their own profile.
4. The controller reads `sub` from the token and uses it as the profile key. There's no id in the path or the body to swap.

Stateless, CSRF off.

</details>

<details>
<summary>How User Service talks to Keycloak</summary>

It logs in to Keycloak as its own service account, `user-service-admin-client`, with the Client Credentials grant.

- That account holds only `manage-users` and `view-users` on `realm-management`. Not `realm-admin`, and not the master-realm admin.
- The token is cached in memory and fetched again 30 seconds before it expires. Parallel requests share one fetch.
- Timeouts: connect 2 s, read 5 s.
- The password from a registration is sent to Keycloak and never stored. `RegisterRequest.toString()` masks it, so it can't end up in a log by accident.

</details>

## Registration

Sequence diagram: [architecture.md → Registration](../architecture.md#registration).

Two systems, no shared transaction:

1. Create the user in Keycloak (Admin API). Keycloak returns the new user's id in the `Location` header.
2. Insert the profile row keyed by that id.
3. If step 2 fails, delete the Keycloak user again, then return the error.

Without step 3, the username would stay taken in Keycloak, and the user's next attempt would fail with "already exists".

| Situation | Status |
|---|---|
| Created | `201` |
| Invalid body | `400` |
| Username already in Keycloak | `409` |
| Keycloak unreachable or erroring | `503` |

<details>
<summary>When the compensation also fails</summary>

If the profile insert fails **and** the delete in step 3 fails too, the user is left in Keycloak with no profile. The service logs `ORPHANED KEYCLOAK USER <sub>` at ERROR and returns the original error. Nothing retries it, so someone has to clean it up by hand. An outbox is the planned fix.

`register` is not `@Transactional` on purpose. The Keycloak call can't take part in a database transaction, and the annotation would suggest it does.

New users get the `user` role automatically through Keycloak's default roles. They aren't logged in after registering; they log in through the normal flow.

</details>

## Deleting a profile

`DELETE /users/me` deletes the profile row, then the Keycloak user.

<details>
<summary>What can go wrong</summary>

- The two deletes are separate. The profile delete is committed before the Keycloak call runs. If the Keycloak delete then fails, the profile is gone but the Keycloak account still exists and can still log in. The call answers `503`.
- A Keycloak `404` on delete counts as success. The goal is that the user is gone.
- The user's orders in Order Service are not deleted.
- The user's current Gateway session and tokens stay valid until they expire or the user logs out.

</details>

## Configuration

<details>
<summary>Properties and schema</summary>

| Property | Value |
|---|---|
| `server.port` | `8083` |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/secure_shop?currentSchema=user_schema` |
| `spring.flyway.schemas` | `user_schema` |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `http://localhost:8081/realms/secure-shop` |
| `spring.security.oauth2.resourceserver.jwt.audiences` | `user-service` |
| `keycloak.admin.base-url` | `http://localhost:8081` |
| `keycloak.admin.realm` | `secure-shop` |
| `keycloak.admin.client-id` | `user-service-admin-client` |
| `keycloak.admin.client-secret` | `${KEYCLOAK_ADMIN_CLIENT_SECRET}` |
| `keycloak.admin.connect-timeout-ms` / `read-timeout-ms` | `2000` / `5000` |

The secret has no default. If it's missing, or still the literal `${...}` text, `KeycloakAdminProperties` stops startup and names the variable to set. Spring Boot on its own would start, then fail at the first registration.

Table `profiles`: primary key `keycloak_sub`, `username` unique, `email`, `full_name`, `address`, `phone`, timestamps.

</details>

## Tests

<details>
<summary>How the tests work</summary>

```bash
cd backend/user-service && ./mvnw verify   # needs Docker for Testcontainers
```

- Same Postgres and token setup as the other services (one container per JVM, real RS256 test tokens in `TokenValidationTest`).
- `KeycloakAdminClient` is mocked in the integration tests, so no Keycloak is needed.
- `ProfileServiceTest` covers registration order, compensation, a failing compensation, and delete order.
- `KeycloakAdminPropertiesTest` covers the startup check for the secret.
- The test secret is set with `@TestPropertySource` on `AbstractIntegrationTest`, not a test `application.properties`. A test file with that name would hide the main one on the classpath and take the resource server config with it.

</details>

## Try it

- curl: [`postman/user-service-curl.md`](../../postman/user-service-curl.md)
- Postman: **User Service** folder, then **Auth → Get Token (registered user)**
- Through the Gateway: `POST http://localhost:8090/users/register` needs no login and no CSRF token
