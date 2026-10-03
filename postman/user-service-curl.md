# user-service — curl requests

Assumes Postgres + Keycloak are up (`docker compose -f docker/docker-compose.yml up -d`) and the
service is running on `localhost:8083`:

```bash
export KEYCLOAK_ADMIN_CLIENT_SECRET=<user-service-admin-client secret>
cd backend/user-service && ./mvnw spring-boot:run
```

`POST /users/register` is the only public endpoint — a new user has no token yet. Everything else is
`/users/me`, resolved from the token's `sub`.

## Register

```bash
curl -i -X POST http://localhost:8083/users/register \
  -H "Content-Type: application/json" \
  -d '{"username":"janedoe","email":"janedoe@example.com","password":"correct-horse-battery","fullName":"Jane Doe","address":"12 Example Road","phone":"0170000000"}'
```

`201` with the new profile, including the `keycloakSub` that Keycloak assigned. Two writes happened:
a Keycloak user (credentials) and a local profile row keyed by that `sub`.

## Log in as the new user

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/secure-shop/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=secure-shop-test-client" \
  -d "client_secret=<client secret>" \
  -d "username=janedoe" \
  -d "password=correct-horse-battery" | jq -r .access_token)
```

## Get My Profile

```bash
curl http://localhost:8083/users/me -H "Authorization: Bearer $TOKEN"
```

## Update My Profile

```bash
curl -X PUT http://localhost:8083/users/me \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Jane R. Doe","address":"99 Other Street","phone":"0180000000"}'
```

## Update My Profile — username and email are ignored

```bash
curl -X PUT http://localhost:8083/users/me \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Jane R. Doe","username":"someone-else","email":"someone-else@example.com"}'
```

The response still shows the original `username` and `email`. `UpdateProfileRequest` has no fields
for them, so they are not bindable — identity is Keycloak's to change, not a profile update's.

## Delete My Profile

```bash
curl -i -X DELETE http://localhost:8083/users/me -H "Authorization: Bearer $TOKEN"
```

`204`. Removes the profile row *and* the Keycloak user. The access token stays technically valid
until it expires, but `/users/me` answers `404` from that point since the profile is gone.

## Duplicate Registration (409)

```bash
curl -i -X POST http://localhost:8083/users/register \
  -H "Content-Type: application/json" \
  -d '{"username":"janedoe","email":"janedoe@example.com","password":"correct-horse-battery"}'
```

Keycloak rejects the duplicate with `409`, which surfaces unchanged. No local profile row is written.

## Validation Error (400)

```bash
curl -i -X POST http://localhost:8083/users/register \
  -H "Content-Type: application/json" \
  -d '{"username":"a","email":"not-an-email","password":"short"}'
```

Fails Bean Validation before Keycloak is called at all.

## No Token (401)

```bash
curl -i http://localhost:8083/users/me
```

## Keycloak Down (503)

Stop the Keycloak container, then register. The service cannot create the credential-holding user,
so it reports the dependency failure rather than writing a profile with no account behind it.

```bash
curl -i -X POST http://localhost:8083/users/register \
  -H "Content-Type: application/json" \
  -d '{"username":"someone","email":"someone@example.com","password":"correct-horse-battery"}'
```
