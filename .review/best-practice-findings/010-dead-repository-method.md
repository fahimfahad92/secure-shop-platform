# 10 — Dead repository method

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | GEN-SMELL-10 |
| Service | user-service |

**Where:** `backend/user-service/src/main/java/com/fahim/userservice/repository/ProfileRepository.java:8`

**What:** `existsByUsername(String)` is declared and never called (uniqueness is enforced by Keycloak's 409 and the DB `UNIQUE` constraint).

**Fix direction:** Delete it.
