# Security Findings

One file per finding, from `/spring-api-security-review`. Open findings sit in this folder. A finding moves to [`done/`](done/) when it is fixed, keeping its ID and file name, and its row below stays with the link updated. Findings are never deleted.

**Rules**
- **ID and file:** `SEC-NNN`, file `SEC-NNN-<slug>.md`, numbered in order and never reused. This series is separate from the best-practice `#N` numbers.
- **Threat model:** every finding names a threat from [`docs/threat-model/risk-register.md`](../../docs/threat-model/risk-register.md) (`T15`), or adds a new one to the register in the same change. This folder lists code-level findings. The register stays the single list of threats.
- **Shape:** a header table, then Where, What, Impact (the attack and what it gains), Fix direction, Test that proves the fix. The header table has:
  - Status
  - Severity (CRITICAL / HIGH / MEDIUM / LOW / INFO)
  - OWASP (`API#:2023`, CWE)
  - Threat
  - Service

  Aim for ≤ ~40 lines.
- **Progress** is recorded in the task (`Interview-Preparation/tasks/<slug>/`), not here. A finding gets a one-line pointer.
- **Fixed:**
  1. Set Status to `Done <date> (<commit>)` in the file and in this index.
  2. Add a `**Status:** Done ...` line naming the commit, the test and the task.
  3. Move the threat to `Mitigated` / `Verified` in the register.
  4. `git mv` the file to `done/`.

| ID | Severity | OWASP | Threat | Title | Service | Status |
|---|---|---|---|---|---|---|
