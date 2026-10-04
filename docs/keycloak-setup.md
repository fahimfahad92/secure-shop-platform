# Keycloak setup

Two ways to get the `secure-shop` realm:

| Path | Time | When |
|---|---|---|
| [A. Import the committed realm](#a-import-the-committed-realm) | 2 minutes | Normal use. Everything is preconfigured |
| [B. Build it by hand](#b-build-it-by-hand) | About 30 minutes | To see how each piece works, or to set up a different Keycloak |

Both end with the same realm. Each step in path B is collapsed; open them one at a time.

## What you end up with

| Kind | Name | Purpose |
|---|---|---|
| Realm | `secure-shop` | Everything below lives here |
| Realm role | `user` | Every user. Given automatically through default roles |
| Realm role | `admin` | Can change the product catalog |
| Client scope | `orders:read`, `orders:write` | Permission to read / change orders |
| Client scope | `secure-shop-audience` | Adds `order-service`, `product-service`, `user-service` to `aud` |
| Client | `secure-shop-gateway` | Gateway browser login. Authorization Code + PKCE |
| Client | `secure-shop-test-client` | Dev login, Postman, curl. Password grant. Dev only |
| Client | `user-service-admin-client` | User Service's service account for the Admin API |
| User | `testuser` / `test` | Normal user |
| User | `adminuser` / `test` | Normal user plus `admin` |

Keycloak runs at `http://localhost:8081`. The admin console login is `admin` / `admin` (set in `docker/docker-compose.yml`, local only).

## A. Import the committed realm

The realm file `docker/keycloak/secure-shop-realm.json` is loaded on Keycloak's first start. Client secrets in it are placeholders that Keycloak fills from environment variables.

1. Create your secrets file:
   ```bash
   cp docker/.env.example docker/.env
   ```
2. Put a random value after each of the three variables. Any string works for a fresh realm:
   ```bash
   openssl rand -hex 32
   ```
3. Start Postgres and Keycloak:
   ```bash
   docker compose -f docker/docker-compose.yml up -d
   ```
   Compose refuses to start if any of the three secrets is empty.
4. Open `http://localhost:8081`, log in, switch to the `secure-shop` realm, and check that **Clients** lists the three clients above.

Use the same three values when you start the Gateway and User Service, and in Postman's `clientSecret` variable. See [development.md](development.md).

<details>
<summary>Re-importing after the file changes</summary>

The import is skipped when the realm already exists in `docker/keycloak-data/`. To load a changed file:

```bash
docker compose -f docker/docker-compose.yml stop keycloak
rm -rf docker/keycloak-data
docker compose -f docker/docker-compose.yml up -d keycloak
```

This also deletes every user created through `/users/register`. `testuser` and `adminuser` come back with the same ids, so their `sub` (and the orders and profiles tied to it) still match.

</details>

## B. Build it by hand

All steps are in the Keycloak 26 admin console.

<details>
<summary>0. Start from an empty Keycloak</summary>

The compose file always imports the committed realm on a fresh start, so start Keycloak as in path A, then delete the imported realm:

1. Start it: steps 1 to 3 of path A.
2. In the console, switch to `secure-shop`, go to **Realm settings** → **Action** menu (top right) → **Delete**, and confirm.

The secrets in `docker/.env` don't matter for path B; Keycloak generates new ones in step 4. Pointing the services at another Keycloak instead works the same way, as long as it runs on port 8081 or you change the `issuer-uri` and `keycloak.admin.base-url` properties.

</details>

### 1. Realm

<details>
<summary>1.1 Create the realm</summary>

1. Log in to `http://localhost:8081` as `admin` / `admin`.
2. Open the realm list at the top of the left menu (**Manage realms** in newer versions) and click **Create realm**.
3. Realm name: `secure-shop`.
4. Enabled: **On**.
5. Click **Create**.

Make sure `secure-shop` is the selected realm (top of the left menu) for every step below. The `master` realm is only for administering Keycloak itself.

</details>

### 2. Roles

<details>
<summary>2.1 Create the user and admin roles</summary>

1. Go to **Realm roles** → **Create role**.
2. Role name: `user`. Description: `Every registered user`.
3. Click **Save**.
4. Go back to **Realm roles** → **Create role**.
5. Role name: `admin`. Description: `Can change the product catalog`.
6. Click **Save**.

</details>

<details>
<summary>2.2 Give every user the user role automatically</summary>

1. Go to **Realm settings** → **User registration** tab.
2. Under **Default roles**, click **Assign role**.
3. Change the filter dropdown to **Filter by realm roles**.
4. Tick `user` and click **Assign**.

`user` is now part of `default-roles-secure-shop`. Every user created from now on, including through `/users/register`, gets it.

</details>

### 3. Client scopes

<details>
<summary>3.1 Create orders:read and orders:write</summary>

1. Go to **Client scopes** → **Create client scope**.
2. Fill in:

   | Field | Value |
   |---|---|
   | Name | `orders:read` |
   | Type | **None** (attached to clients by hand in step 4) |
   | Protocol | OpenID Connect |
   | Include in token scope | **On** |

3. Click **Save**.
4. Repeat for `orders:write`.

"Include in token scope" puts the name into the token's `scope` claim, which is what Order Service checks.

</details>

<details>
<summary>3.2 Create secure-shop-audience</summary>

1. Go to **Client scopes** → **Create client scope**.
2. Fill in:

   | Field | Value |
   |---|---|
   | Name | `secure-shop-audience` |
   | Type | **None** |
   | Protocol | OpenID Connect |
   | Include in token scope | **Off** (this scope adds a claim, it isn't a permission) |

3. Click **Save**.

</details>

<details>
<summary>3.3 Add one audience mapper per service</summary>

1. Open `secure-shop-audience` → **Mappers** tab → **Configure a new mapper** → **Audience**.
2. Fill in:

   | Field | Value |
   |---|---|
   | Name | `aud-order-service` |
   | Included Client Audience | *(leave empty)* |
   | Included Custom Audience | `order-service` |
   | Add to ID token | Off |
   | Add to access token | **On** |
   | Add to token introspection | On |

3. Click **Save**.
4. Go back to the scope's **Mappers** tab and click **Add mapper** → **By configuration** → **Audience**. Repeat for:
   - `aud-product-service` with custom audience `product-service`
   - `aud-user-service` with custom audience `user-service`

</details>

### 4. Clients

<details>
<summary>4.1 secure-shop-gateway (browser login)</summary>

1. Go to **Clients** → **Create client**.
2. **General settings**:
   - Client type: OpenID Connect
   - Client ID: `secure-shop-gateway`
   - Name: `API Gateway (BFF)`
   - Click **Next**.
3. **Capability config**:
   - Client authentication: **On**
   - Authorization: Off
   - Authentication flow: tick **only** Standard flow. Untick Direct access grants.
   - PKCE Method: **S256**
   - Click **Next**.
4. **Login settings**:

   | Field | Value |
   |---|---|
   | Root URL | `http://localhost:8090` |
   | Valid redirect URIs | `http://localhost:8090/login/oauth2/code/keycloak` |
   | Valid post logout redirect URIs | `http://localhost:8090/` |
   | Web origins | `http://localhost:8090` |

5. Click **Save**.
6. Open the **Credentials** tab and copy the **Client secret**. This is `KEYCLOAK_GATEWAY_CLIENT_SECRET`.
7. Open the **Client scopes** tab → **Add client scope**, tick `orders:read`, `orders:write` and `secure-shop-audience`, click **Add** → **Default**.

The redirect URI is Spring Security's default for a registration named `keycloak`. If you change the Gateway's port, change it here too.

</details>

<details>
<summary>4.2 secure-shop-test-client (Postman, curl, dev login)</summary>

1. Go to **Clients** → **Create client**.
2. **General settings**:
   - Client type: OpenID Connect
   - Client ID: `secure-shop-test-client`
   - Name: `Test client - Postman/curl only`
   - Click **Next**.
3. **Capability config**:
   - Client authentication: **On**
   - Authentication flow: tick **only** Direct access grants. Standard flow, Implicit flow and Service accounts roles stay unticked.
   - Click **Next**.
4. **Login settings**: leave everything empty and click **Save**.
5. **Credentials** tab: copy the secret. This is `KEYCLOAK_TEST_CLIENT_SECRET`.
6. **Client scopes** tab → **Add client scope**: tick `orders:read`, `orders:write`, `secure-shop-audience` → **Add** → **Default**.

Direct access grants means "send a username and password straight to the token endpoint". Fine for local testing. Never enable it on a client used by a real app.

</details>

<details>
<summary>4.3 user-service-admin-client (service account)</summary>

1. Go to **Clients** → **Create client**.
2. **General settings**: Client ID `user-service-admin-client`, then **Next**.
3. **Capability config**:
   - Client authentication: **On**
   - Authentication flow: untick Standard flow and Direct access grants, tick **only** Service accounts roles.
   - Click **Next**, then **Save**.
4. **Credentials** tab: copy the secret. This is `KEYCLOAK_ADMIN_CLIENT_SECRET`.
5. Open the **Service accounts roles** tab → **Assign role**.
6. Change the filter dropdown to **Filter by clients**.
7. Search `realm-management`, tick `manage-users` and `view-users`, click **Assign**.

Don't assign `realm-admin`. User Service only needs to create and delete users.

</details>

### 5. Users

<details>
<summary>5.1 Create testuser</summary>

1. Go to **Users** → **Create new user**.
2. Fill in:

   | Field | Value |
   |---|---|
   | Email verified | **On** |
   | Username | `testuser` |
   | Email | `testuser@example.com` |
   | First name | `Test` |
   | Last name | `User` |

3. Click **Create**.
4. Open the **Credentials** tab → **Set password**.
5. Password: `test`, confirm it, set **Temporary** to **Off**, click **Save**, then confirm.
6. Open **Role mapping** and check that `default-roles-secure-shop` is listed. That's where `user` comes from.

</details>

<details>
<summary>5.2 Create adminuser</summary>

1. Repeat 5.1 with username `adminuser`, email `adminuser@example.com`, first name `Admin`.
2. Open **Role mapping** → **Assign role**.
3. Filter by realm roles, tick `admin`, click **Assign**.

</details>

### 6. Connect the services

<details>
<summary>6.1 Put the three secrets where the apps read them</summary>

Write the secrets you copied in step 4 into `docker/.env`:

```bash
KEYCLOAK_GATEWAY_CLIENT_SECRET=<from 4.1>
KEYCLOAK_TEST_CLIENT_SECRET=<from 4.2>
KEYCLOAK_ADMIN_CLIENT_SECRET=<from 4.3>
```

Load them into the shell that starts the Gateway and User Service (`set -a; . docker/.env; set +a`), or set them as environment variables in your IDE. Restart the IDE after changing system environment variables; it only sees the ones that existed when it started.

</details>

### 7. Check the tokens

<details>
<summary>7.1 Get a token and look inside it</summary>

In the console: **Clients** → `secure-shop-test-client` → **Client scopes** tab → **Evaluate** sub-tab → pick user `testuser` → **Generated access token**.

Or with curl:

```bash
curl -s -X POST http://localhost:8081/realms/secure-shop/protocol/openid-connect/token \
  -d grant_type=password -d client_id=secure-shop-test-client \
  -d client_secret=<secret> -d username=testuser -d password=test
```

Paste the `access_token` into [jwt.io](https://jwt.io) or decode the middle part with `base64 -d`. Only do that with local dev tokens.

| Claim | `testuser` | `adminuser` |
|---|---|---|
| `azp` | `secure-shop-test-client` | same |
| `scope` | contains `orders:read orders:write` | same |
| `aud` | contains `order-service`, `product-service`, `user-service` | same |
| `realm_access.roles` | contains `user` | contains `user` and `admin` |

</details>

<details>
<summary>7.2 If something is missing or failing</summary>

| Symptom | Usual cause |
|---|---|
| `aud` or `scope` missing an entry | Scope added as **Optional** instead of **Default** on the client, or **Add to access token** off on a mapper |
| Change doesn't show up | You're looking at an old token. Tokens never change after they're issued. Get a new one |
| `401 invalid_client` / `unauthorized_client` | Wrong client secret, or the grant isn't enabled on the client |
| `400 invalid_grant` | Wrong username or password, or the user has a pending required action |
| Browser login: "Invalid parameter: redirect_uri" | Valid redirect URIs on `secure-shop-gateway` doesn't match exactly |
| Browser login: PKCE error | PKCE Method not `S256` on the client, or the Gateway isn't sending PKCE |
| Logout: "Invalid redirect uri" | Valid post logout redirect URIs doesn't match `http://localhost:8090/` exactly |
| Registration returns `503` | User Service can't get a token: check `KEYCLOAK_ADMIN_CLIENT_SECRET` and the service account roles |

</details>

## Saving changes back to the realm file

Path A only loads what's in `docker/keycloak/secure-shop-realm.json`. If you change the realm in the console and want to keep it:

<details>
<summary>Export and clean up the file</summary>

1. Go to **Realm settings** → **Action** menu (top right) → **Partial export**.
2. Turn on **Include groups and roles** and **Include clients**. Click **Export**.
3. Replace `docker/keycloak/secure-shop-realm.json` with the downloaded file.
4. Before committing, fix two things the export changes:
   - Client secrets come out masked as `**********`. Put the placeholders back: `${KEYCLOAK_GATEWAY_CLIENT_SECRET}`, `${KEYCLOAK_TEST_CLIENT_SECRET}`, `${KEYCLOAK_ADMIN_CLIENT_SECRET}`.
   - A partial export has no users. Copy the `testuser` and `adminuser` entries (with their fixed ids) back into `users` from the previous version of the file.
5. Test it: wipe `docker/keycloak-data/`, start Keycloak, and run the checks in step 7.

Never commit a real secret. If one gets committed, rotate it in Keycloak; removing it from the file doesn't remove it from git history.

</details>

<details>
<summary>Differences between path B and the committed file</summary>

- In the committed file, `orders:read` and `orders:write` are realm **default** client scopes, so every client gets them, including `user-service-admin-client`. Path B attaches them only to the two user-facing clients, which is tighter. Both work with the services as they are.
- Users you create by hand get new random ids. The seeded users in the committed file have fixed ids. With a fresh database that doesn't matter; with existing orders or profiles, their `sub` won't match the new users.

</details>
