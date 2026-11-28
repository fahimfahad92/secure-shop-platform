# 18 — User and Order Service `anyRequest()` defaults fail open for new endpoints (ADR-0003)

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | SB-SEC |
| Service | user, order |

**Where:** `backend/user-service/.../config/SecurityConfig.java`, `backend/order-service/.../config/SecurityConfig.java`

**What:** User Service: after `POST /users/register`, the rule is `anyRequest().authenticated()`, so any token with the `user-service` audience reaches any endpoint. Order Service: `anyRequest()` needs `orders:write` + `user`, but `orders:read`/`orders:write` are realm default client scopes and `default-roles-secure-shop` includes `user`, so every logged-in user's token passes it.

**Why it matters:** Safe today because every existing endpoint has its own rule and acts on the caller's `sub`. A new endpoint without its own rule would be open to every logged-in user. Only Product fails closed (`anyRequest()` needs `admin`). Found in round 3: rounds 2 and the defense wrongly treated Order as fail-closed.

**Fix direction:** User: explicit matchers for `GET`, `PUT`, `DELETE /users/me` (`authenticated()`), then `anyRequest().denyAll()`. Order: explicit matchers for `POST /orders`, `GET /orders`, `GET`, `PUT`, `DELETE /orders/{id}` with the current scope + role rules, then `anyRequest().denyAll()`. Add `deleteMyProfile_noToken_returns401` to `UserControllerSecurityTest`, and in both services a test that an unmapped path with a valid token is refused. Removing the order scopes from the realm defaults is step 4 (ADR-0011, T17), not this finding.

**When done:** ADR-0003 → move items 1 and 5 of "Scheduled hardening" into Decision outcome / Verification; drop the "Until step 2, User and Order Service fail open" residual risk; correct the "Today only Product fails closed" sentence.
