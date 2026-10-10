# Threat Model

What can go wrong in secure-shop-platform, how likely and how bad it is, and which step fixes it. A living model: it changes with the system, and fixed threats stay in it with their status updated.

| | |
|---|---|
| System | v1: Gateway (BFF), Order, Product and User services, Keycloak, Postgres |
| Model reflects code at | `main` @ `3afe73f` (2026-10-10) |
| Method | Data flow diagram → STRIDE per element → DREAD scoring → risk register |
| Status | Phases 1–3 drafted: system, STRIDE (42 threats), DREAD + "Fixed in" for all. 12 High, all assigned. Next: Phase 5 ADRs (Phase 4, defense in depth, is stretch) |

Related: [../architecture.md](../architecture.md) for how the system works, [../adr/](../adr/) for why it works that way.

## Files

| File | Holds | Changes when |
|---|---|---|
| [system.md](system.md) | Scope-level picture: elements `E…`, data flow diagram, flows `F…`, trust boundaries `TB…`, assets | An element, flow or boundary is added, changed or removed |
| [stride/](stride/) | One file per element group: the threats `T…`, the control that stands today, the gap | A threat is found, or a fix changes a control or closes a gap |
| [risk-register.md](risk-register.md) | One row per threat: DREAD scores, the step that fixes it, status, evidence | Scoring, planning, and every status change |
| [changelog.md](changelog.md) | One entry per model update | Every update |

STRIDE files: [gateway](stride/gateway.md) · [product-service](stride/product-service.md) · [order-service](stride/order-service.md) · [user-service](stride/user-service.md) · [keycloak](stride/keycloak.md) · [data-stores](stride/data-stores.md) · [data-flows](stride/data-flows.md) · [external-entities](stride/external-entities.md). A new element (e.g. the Next.js frontend in step 6) gets its own file.

## Scope and assumptions

**In scope:** the four services, Keycloak, Postgres, every flow between them, and the browser/Postman as clients.

**Out of scope for now:** CI/CD (comes with step 3, #04), container images (step 3 builds them, step 4 hardens them), the Next.js frontend (step 6), the developer machine itself.

**Assumptions:**

1. **Model the intended deployment, flag where local differs.** The intent: only the Gateway is reachable from clients; services, Postgres and Keycloak's admin side are internal. Today everything runs on one machine over plain HTTP, and several internal ports are reachable from the local network (see `TB7`). Both are modelled; where they differ, the threat says so.
2. **No TLS anywhere** today (documented gap). Every flow is plain HTTP or plain JDBC.
3. **One instance of everything.** Gateway sessions live in memory; restarting the Gateway logs everyone out.
4. **Keycloak runs in `start-dev` mode** with its own H2 database under `docker/keycloak-data/`.

## How to read it

- **Elements** (`E1`…) are the boxes: processes, data stores, external parties.
- **Flows** (`F1`…) are the arrows: who sends what to whom.
- **Trust boundaries** (`TB1`…) are the lines where the level of trust changes.
- **Threats** (`T1`…) sit on an element or on a flow that crosses a boundary.
- **STRIDE:** **S**poofing, **T**ampering, **R**epudiation, **I**nformation disclosure, **D**enial of service, **E**levation of privilege. Processes are checked for all six; data stores for T, I, D (and R where they hold logs); flows for T, I, D; external entities for S and R. Only threats that apply are listed.
- **In a STRIDE table**, "Control today" names the code or test that stands now; "Gap" is what's missing.
- **Steps** (the "Fixed in" column): 2 = review findings, 3 = DevSecOps pipeline (#04), 4 = container and app hardening (#02), 5 = service-to-service security (#03), 6 = v2 (frontend, resilience, API hardening), or **Accepted** with an ADR.

## DREAD scoring

Each threat scores 1–3 on five dimensions, total 5–15:

| Dimension | 1 | 2 | 3 |
|---|---|---|---|
| **D**amage | Minor | Significant data loss | Full breach |
| **R**eproducibility | Needs specific conditions | Reproducible with effort | Always |
| **E**xploitability | Expert | Some skill | Unskilled / scripted |
| **A**ffected users | One user | A group | All users |
| **D**iscoverability | Hidden | Needs source access | Public knowledge |

Bands: **12–15 High**, **8–11 Medium**, **5–7 Low**. Two scores per threat: **initial** (as found) and **residual** (after its fix). A partial fix shows as a lower residual score, not as closed.

## Maintaining the model

### Rules

- **IDs are permanent.** `T…`, `E…`, `F…`, `TB…` are never renumbered or reused. New ones take the next free number, whichever file they land in. Commits, ADRs and review notes can point at `T26` forever.
- **Rows are never deleted.** A fixed threat stays, with its status changed. A removed element's threats become `Obsolete`.
- **Two places, two jobs.** The STRIDE files describe the system **now**: when a fix lands, update *Control today* (name the code and the test) and clear *Gap*. The risk register tracks the **work**: status, scores, step, evidence.
- **Every update gets a changelog entry**, and the header's "Model reflects code at" moves to the commit the update describes.

### Status lifecycle (risk register)

| Status | Meaning | Evidence required |
|---|---|---|
| `Open` | Found, not being worked on | A "Fixed in" step, or `Accepted` planned |
| `In progress` | Its step is under way | Branch name |
| `Mitigated` | Fix merged | PR number and residual DREAD |
| `Verified` | A test proves the fix holds | Test name (`Class.method`) |
| `Accepted` | Not fixing it, on purpose | ADR link and review date |
| `Obsolete` | The element no longer exists | What removed it |

Two variants mark controls that existed before the model did: `Mitigated (v1)` (control in code, no test yet) and `Verified (v1)` (control in code and proven by a named test). Their evidence is the code or test instead of a PR, and they aren't DREAD-scored.

`Open` → `In progress` → `Mitigated` → `Verified` is the normal path. A threat can move to another step only with a one-line reason in its row.

### When to update

| Trigger | What to do |
|---|---|
| **Start of every step**, before code | Model the change: new elements, flows, boundaries and their threats (e.g. #03's `reserve-stock` endpoint and new client secret; step 3's containers and networks; step 6's frontend). The design gets threat-modelled before it's built |
| **End of every step** (part of done) | Every threat assigned to the step is `Mitigated`/`Verified`, or moved with a reason. STRIDE files updated. Threats the fix itself introduced are added |
| **A design-level finding** (code review, best-practices review, scanner result that reveals a design flaw) | Add a threat with its source in the changelog. Plain code-level scanner hits (a CVE in a library) stay in the pipeline's baseline, not here |
| **An ADR changes a boundary or flow** | Update `system.md`, link the ADR from the affected threats |
| **An incident or surprise** | Add it, even if already fixed, then mark it `Verified` |

### Checks

- **PR checklist line:** *Does this change add or alter an element, flow or trust boundary? Then update `docs/threat-model/`.*
- **CI (step 3, #04 Phase 4b):** the register is linted together with the ADR log: IDs unique, every High has a "Fixed in" step or an `Accepted` ADR, every `Mitigated`/`Verified` row has a PR or a test, every `Accepted` row links an ADR with a review date, every `T…` in a STRIDE file has a register row and vice versa.
