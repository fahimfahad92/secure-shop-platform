# STRIDE — External entities

Legend, status values and how to update: [../README.md](../README.md). Diagram and IDs (`E…`, `F…`, `TB…`): [../system.md](../system.md). Scores and status per threat: [../risk-register.md](../risk-register.md).

| ID | Entity | STRIDE | Threat | Control today | Gap |
|---|---|---|---|---|---|
| T41 | E1 User | R | A user denies placing, changing or cancelling an order | Order row holds `user_sub` and timestamps | No audit trail of actions (T28, T36) |
| T42 | E11 Operator | S | Anyone on the network acts as the operator, using the default credentials | — | Same root cause as T27, T32 |

