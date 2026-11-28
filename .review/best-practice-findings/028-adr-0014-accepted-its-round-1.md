# 28 — ADR-0014 accepted with its round 1 REVISE items open

| | |
|---|---|
| Status | Open |
| Severity | MINOR |
| Rule | GEN-OTHER |
| Service | docs |

**Where:** `docs/adr/0014-accept-no-tls-local-rnd.md`

**What:** Round 1 (2026-11-06, 8.5/10) said REVISE: the ADR is missing (1) how "local only" is enforced, (2) a CI trigger that would catch the stack leaving one machine, and (3) a note on what step 3's containers change. Accepted with an override. The 2026-11-06 report was not saved in `.review/`, so re-derive the details from the review record or a fresh review when fixing.

**Why it matters:** The acceptance of T1, T37, T38 (reply integrity) and T39 rests on "local only", which nothing enforces or detects today.

**Fix direction:** Likely shape: (1) enforcement = step 4 binding published ports to `127.0.0.1` (T40 is step 3); (2) a CI check that fails when a non-dev profile names a non-localhost issuer or base URL over plain HTTP, or similar; (3) step 3 note: container-to-container traffic on the compose network is still plain HTTP, and who can sniff it. Because ADR-0014 is `Accepted`, items that change the decision go in a new ADR that supersedes it; Verification and trigger lines can be added in place.

**When done:** mark it `Done`; ADR-0014 review record → re-review row.
