# AUTOMATED-CHECKS - pointer

| Field | Value |
| --- | --- |
| **Contracts reviewed** | `CHECK-VERDICT` 0.14, `CHECK-BASELINE` 0.12 |
| **Reviewed** | 2026-10-29, S0057 (earlier: 2026-10-25, S0035) |
| **Home** | shared contracts catalog, `automated-checks/` |
| **Role here** | consumer, for the checks this repository writes itself |

Adopted by the owner's decision of 2026-10-25 (S0035 section 3.3, item 4) because the help gate of `CONTEXT-HELP`
rule 19 needs both. `CHECK-VERDICT` sections 2 (exit codes 0/1/2/3, the last stdout line is the verdict line) and
`CHECK-BASELINE` section 3 (a set baseline that only falls) apply to `tools/help/assert-help-map.ps1`, and `CHECK-VERDICT`
to the documentation gates in `tools/docs/` written by S0051, whose aggregator `assert-docs.ps1` quotes its children and
follows the rule 2.14 precedence. S0051 aligned `tools/contracts/check-sync.ps1` on 2026-11-08 (exit 2 and a verdict line). The
script already in this repository that predates the adoption and is not aligned (`tools/port-ledger/`) keeps its own
verdict lines until a ticket aligns it; nothing here claims it conforms. S0025 aligned `tools/audit/` on
2026-11-08: codes 0/1/2 only (no advisory, no lock wait), its two self-tests print the 0.14 item A test verdict line
(`run=N skipped=M`), and `run-selftests.ps1` quotes each step and follows the rule 2.14 precedence.
`CHECK-PLACEMENT` and `BUILD-EVIDENCE` are not adopted.

The 2026-10-29 amendment (`CHECK-VERDICT` 0.14 item A, `CHECK-BASELINE` 0.12 item B) was read on 2026-10-29 (S0057). Two
gaps, both carried by S0060: the help-gate self-test `tools/help/test-help-gate.ps1` prints `PASS (<n> cases)`, the legacy
spelling, where item A.1 asks for a test verdict line with `run=N skipped=M`; and `tools/help/assert-help-map.ps1` does not
compare `help-map-baseline.txt` with its committed copy (item B.1). Exit codes, verdict words and the baseline shape are
unchanged, so the adoption stays `partial`.
