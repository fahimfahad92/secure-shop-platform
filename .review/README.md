# Review

Review records, tracked in git so they move between devices. Decisions themselves live in [`docs/adr/`](../docs/adr/README.md), and threats in [`docs/threat-model/`](../docs/threat-model/README.md). This folder holds the reviews that produced or graded them.

| Folder | What | Index |
|---|---|---|
| [`best-practice-findings/`](best-practice-findings/README.md) | Best-practice and tech-debt findings (`#1`, `#2`, …), one file each, from `/best-practices-review`. Fixed ones in `done/` | [README](best-practice-findings/README.md) |
| [`security-findings/`](security-findings/README.md) | Security findings (`SEC-001`, …), one file each, from `/spring-api-security-review`. Each one links a threat ID. Fixed ones in `done/` | [README](security-findings/README.md) |
| [`adr/`](adr/) | ADR grading reports from `/architecture-kata-reviewer adr` | file names below |

**ADR review file names:**
- **One ADR:** `<NNNN>-round<R>-<date>.md`, e.g. `0013-round5-2026-11-08.md`. The ADR number comes first, so all rounds of one ADR sort together.
- **Several ADRs graded together:** `batch-round<R>-<date>.md`.

The score of each review also goes into the ADR's own review record and the [ADR index](../docs/adr/README.md).

Reorganised 2026-11-28. Before that, the best-practice findings were one file, `best-practices-findings.md`, and the ADR reviews were `adr-review-*.md` files directly in this folder.
