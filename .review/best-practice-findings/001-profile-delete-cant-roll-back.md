# 1 — Profile delete can't roll back

| | |
|---|---|
| Status | Open |
| Severity | MAJOR |
| Rule | GEN-DES-05 |
| Service | user-service |

**Where:** `backend/user-service/src/main/java/com/fahim/userservice/service/ProfileService.java:92-100` (`ProfileService#delete`)

**What:** `delete()` has no `@Transactional` (class has none either). `profileRepository.delete(profile)` runs in the repository's own transaction and commits immediately. Then `keycloakAdminClient.deleteUser(...)` is called. The Javadoc says the local row goes first "because it is the one we can roll back by simply failing" — but nothing rolls it back.

**Why it matters:** Keycloak down or returns 5xx → profile row is gone, Keycloak user still exists. User can still log in, but `GET /users/me` returns 404 forever and re-registering fails with 409 (username taken in Keycloak). Existing test `delete_removesProfileThenKeycloakUser` only covers the happy path with mocks, so it can't catch this.

**Decided by:** [ADR-0015](../../docs/adr/0015-no-transactional-failures-recorded-as-state.md) (Proposed, 2026-11-22): no `@Transactional`, failures recorded as state. Fixed in task `tx-profile-delete`.

**Fix direction (revised 2026-11-08, follows #30: no `@Transactional`, failures recorded as state):** Mark the profile `DELETE_PENDING` (own commit), call Keycloak `deleteUser`, then delete the row. Keycloak fails → set `DELETE_FAILED` with the error and time, return `503`; the row and the account stay, the user can retry (`deleteUser` already treats `404` as done, so a retry is safe). Keycloak succeeds, row delete fails → the row stays `DELETE_PENDING` with no login behind it; log loudly with the `sub`, the same way `register()` logs an orphan. Tests: `deleteUser` throws → row `DELETE_FAILED`, account untouched; row delete fails → `DELETE_PENDING` and the log line; happy path → row gone. Rewrite the Javadoc ("the one we can roll back by simply failing" is false). `GET /users/me` on a `DELETE_PENDING`/`DELETE_FAILED` row: decide whether it reads as `404` or shows the status.

Rejected (original direction): `@Transactional` + `flush()` before the Keycloak call. It rolls the profile back on a Keycloak failure, but holds a DB connection and the row lock for the whole Keycloak call (#2's problem) and leaves no record that a delete failed (#30).

**Understand first:**
- Why `repository.delete()` alone commits: Spring Data repository methods are `@Transactional` themselves when no outer transaction exists.
- Why `flush()` matters: without it Hibernate defers the DELETE until commit, i.e. *after* the Keycloak call.
- Remaining window after the fix: Keycloak delete succeeds, then DB commit fails → orphan profile (harmless: no one can log in as that user). Compare with the opposite order.
- Note: already listed in `project-ideas/01-oauth2-oidc/requirements.md` → "Carried over from Phase 4".
