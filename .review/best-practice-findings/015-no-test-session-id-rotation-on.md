# 15 — No test for session id rotation on login (threat T2)

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | GEN-TEST-01 |
| Service | gateway |

**Where:** `backend/gateway/src/test/java/com/fahim/gateway/` (`AuthFlowTest`, `DevLoginTest`)

**What:** Session fixation is prevented by Spring Security changing the session id on OAuth2 login, and by `DevLoginController.startSession` calling `request.changeSessionId()`. No test asserts that the id actually changes.

**Why it matters:** A config change (e.g. a custom `sessionManagement()` block) could switch the protection off silently. Threat T2 stays `Mitigated (v1)` instead of `Verified` until a test exists.

**Fix direction:** In `DevLoginTest`, create a session first, log in, assert the `JSESSIONID` after login differs from the one before. Same for the browser login callback if the test setup allows it.

**When done:** risk register T2 → `Verified`, evidence = the test name; changelog entry.
