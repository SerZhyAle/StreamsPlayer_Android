# S0060 - Help-gate scripts vs CHECK-VERDICT 0.14 test line and CHECK-BASELINE 0.12 committed comparison

**Ticket:** S0060
**Status:** Implemented
**Status note:** 2026-10-30 (queue pass pq1, static): BASELINE-RISE comparison with the committed copy and the 0.14 self-test line written; direct gate calls exit 0/1/2 as expected (0.725 s); self-test not run (unmeasured, release). Not built, not tested until the release. RELEASE: self-test run; commit tools/help, then gate PASS; update docs/contracts/AUTOMATED-CHECKS.md gap paragraph.
**Complexity:** light - confirmed 2026-10-30 at the spec pass: two scripts and one README change in tools/help, no new file, no new public type, no contract amendment
**Date:** 2026-10-29
**Rung:** primitive spec - Problem, Approach and Done criteria below; the pipeline sections are kept because the ticket was captured as a full spec

## Captured request and migration scope

Parked from S0057 (contract sync to the catalog of 2026-10-29).

Symptom: the help-gate scripts of S0035 trail the automated-checks contract at its 2026-10-29 amendment.

1. CHECK-VERDICT 0.14 item A.1: a script that runs a fixed list of its own assertions prints a test verdict line with `run=N skipped=M`. `tools/help/test-help-gate.ps1` prints `help-gate-selftest: PASS (<n> cases)` (lines 154 and 157), a legacy spelling that stays readable during migration but is not the form of 0.14.
2. CHECK-BASELINE 0.12 item B: rule 4's comparison with the committed set binds every set baseline, including one no tool rewrites; the committed copy of the file (the copy at HEAD) may be the snapshot, and an identifier present in the working file and absent from the committed one fails the run and is named. `tools/help/assert-help-map.ps1` prints the baseline and fails stale lines but does not compare with a committed copy, so a line added in the same change absorbs a finding silently. The registry row already says there is no committed-snapshot comparison.

Evidence (2026-10-29): `pwsh -NoProfile -File tools/help/assert-help-map.ps1` ends `assert-help-map: PASS (30 surfaces, 29 rows, 1 baseline, ..)`; the two lines above come from reading the scripts. Note: `tools/help/` is not committed yet, so the HEAD copy the comparison needs exists only after the first commit that carries the file.

Needed: print the test line in the 0.14 form, add the committed-copy comparison naming the added identifiers (a hand edit committed with its reason is the update path), keep the exit codes, and re-verify the CHECK-VERDICT and CHECK-BASELINE rows at 0.14 and 0.12. Run the self-test and the gate at the release.

## 3. Open questions

All resolved on 2026-10-30 without the owner, by the records below.

- Which form does the self-test line take? **The 0.14 form of item A.1 and A.2:** `help-gate-selftest: PASS (run=N skipped=0)`. A
  failing line carries the bare deciding count, `FAIL (k)`, by item A.4; the failing case names stay on the lines above it.
- Where does the committed copy come from? **`git show HEAD:tools/help/help-map-baseline.txt`** in the judged tree, by 0.12
  item B.1 (the copy at `HEAD` may be the snapshot). A `-CommittedBaseline <path>` parameter replaces it for the self-test,
  whose fixture is not a repository.
- What happens while the file is not yet in `HEAD`? **The comparison cannot run, so the gate reports `COULD NOT VERIFY`, exit 2.**
  CHECK-VERDICT 0.12 item A: a requested check whose work did not run stays `2`. This is the honest state until the first commit
  that carries the file; the owner decides when that commit happens, and this ticket does not commit.
- Does an accepted picture set (`shot:<id>`) need the same comparison? **Yes.** Rule 3.4 binds every set line, so the self-test case
  of an accepted stale picture set now writes its line into the committed copy too - the committed hand edit is the update path.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none blocks this ticket. S0057 is the parent (the ticket it was parked from). S0058 and S0061 are neighbours that touch no file listed here.
- **Owner question on the first commit:** none in this ticket - the `COULD NOT VERIFY` state until that commit is recorded in section 3 above and in the final report of the pass.
- **Canon-session item:** none. The two contracts already carry the amendments this ticket implements (CHECK-VERDICT 0.14 item A, CHECK-BASELINE 0.12 item B, dated 2026-10-29).

## 4. Definition of done

- `tools/help/test-help-gate.ps1` prints `help-gate-selftest: PASS (run=N skipped=0)` on a pass and `help-gate-selftest: FAIL (k)` on a failure; no `cases` spelling remains in the file or in `tools/help/README.md`.
- `tools/help/assert-help-map.ps1` takes the committed copy of the baseline and fails with `BASELINE-RISE` for each identifier in the working file and absent from it; a missing committed copy is `COULD NOT VERIFY` with exit 2; exit codes 0, 1 and 2 are otherwise unchanged.
- The self-test has two new cases (an uncommitted baseline line is `BASELINE-RISE`; a missing committed copy is `COULD NOT VERIFY`), and the accepted-picture case passes with its line committed.
- Fast checks in section 6 pass in this run with their exit codes recorded; the self-test and the gate in the committed state are RELEASE items.

## 5. Shared-contract boundary

Conformance only. CHECK-VERDICT 0.14 item A and CHECK-BASELINE 0.12 item B are already in the catalog (`P:\Contracts\automated-checks\README.md`, amendment of 2026-10-29). No exit code, verdict word, baseline file shape or catalog file changes, and no catalog file is written from this ticket. The pointer paragraph in `docs/contracts/AUTOMATED-CHECKS.md` is updated at release, after the self-test has run (RELEASE item), so this ticket stays at three files changed.

## 6. Plan

Coverage: item A.1 of the test line maps to phase 1; item B.1 (the committed comparison, its absence and its self-test cases) maps to phases 2 and 3; the README and the pointer paragraph map to phase 4 and the release items.

- [x] Phase 1 - `tools/help/test-help-gate.ps1`: the verdict lines become `PASS (run=$cases skipped=0)` and `FAIL ($($failures.Count))`. Check: `grep -n "run=\$cases skipped=0" tools/help/test-help-gate.ps1` finds one line and `grep -n "cases)" tools/help/test-help-gate.ps1` finds none.
- [x] Phase 2 - `tools/help/assert-help-map.ps1`: a `-CommittedBaseline` parameter; without it, `git -C $Root show HEAD:tools/help/help-map-baseline.txt` (a failure is `COULD NOT VERIFY`, exit 2); a `BASELINE-RISE` finding for each working-file identifier absent from the committed copy; the header lists the new kind and exit 2. Check: `grep -n "BASELINE-RISE" tools/help/assert-help-map.ps1` finds the finding and the header line.
- [x] Phase 3 - `tools/help/test-help-gate.ps1`: the fixture writes its committed copy beside it; `Invoke-Gate` passes it; the picture case commits its line; the two new cases are added. Check: `grep -n "BASELINE-RISE\|no-such-committed" tools/help/test-help-gate.ps1` finds both new cases.
- [x] Phase 4 - `tools/help/README.md`: the two rows name the committed comparison and the new verdict forms (`run=20 skipped=0`). Check: `grep -n "run=20 skipped=0" tools/help/README.md` finds one line.
- [x] Phase 5 - direct call (fast tier, measured at 5 s or less): `pwsh -NoProfile -File tools/help/assert-help-map.ps1 -CommittedBaseline <copy of the working baseline>` exits 0 and ends `assert-help-map: PASS`; with a committed copy that lacks one working line it exits 1 and names `BASELINE-RISE`; without the parameter in this repository it exits 2 and ends `COULD NOT VERIFY`; with a missing copy file it exits 2. Check: the four exit codes and the last lines.

## Last Audit

**Date:** 2026-10-30 | **Mode:** queue pass pq1 (between releases, static checks and direct calls only) | **Verdict:** Implemented - not built, not tested until the release

**Proven by fresh commands (2026-10-30):**

- `pwsh -NoProfile -File tools/release/check-syntax.ps1` - expected: exit 0, `release-syntax: PASS` | actual: `release-syntax: PASS (...pre-release-sweep.ps1)`, exit 0, 0.300 s wall time.
- PowerShell parser over `tools/help/assert-help-map.ps1` and `tools/help/test-help-gate.ps1` - expected: 0 parse errors | actual: 0 and 0, both files.
- `grep -c '^    Assert-Case' tools/help/test-help-gate.ps1` - expected: 20 cases, the verdict line `run=$cases` | actual: 20; the verdict lines at lines 170 and 173 are `FAIL ($($failures.Count))` and `PASS (run=$cases skipped=0)`; no `cases)` string remains.
- `pwsh -NoProfile -File tools/help/assert-help-map.ps1` (no committed copy; `tools/help/` not in HEAD) - expected: exit 2, `COULD NOT VERIFY` | actual: `assert-help-map: COULD NOT VERIFY (no committed copy of tools/help/help-map-baseline.txt at HEAD - ...)`, exit 2.
- `cp tools/help/help-map-baseline.txt /tmp/s0060-same.txt && pwsh -NoProfile -File tools/help/assert-help-map.ps1 -CommittedBaseline /tmp/s0060-same.txt` (committed copy equal to the working baseline) - expected: exit 0, `PASS` | actual: `assert-help-map: PASS (30 surfaces, 29 rows, 1 baseline, 33 pages, 8 picture sets checked, ...)`, exit 0; wall time 0.725 s.
- `grep -v "^ExchangeDiagnosisDialog" tools/help/help-map-baseline.txt > /tmp/s0060-without.txt && pwsh -NoProfile -File tools/help/assert-help-map.ps1 -CommittedBaseline /tmp/s0060-without.txt` (committed copy without `ExchangeDiagnosisDialog`) - expected: exit 1, `BASELINE-RISE` named | actual: `FAIL BASELINE-RISE 'ExchangeDiagnosisDialog' is not in the committed baseline ...`, `assert-help-map: FAIL (1 finding(s))`, exit 1.
- `pwsh -NoProfile -File tools/help/assert-help-map.ps1 -CommittedBaseline /tmp/s0060-none.txt` (no such file) - expected: exit 2 | actual: `COULD NOT VERIFY (committed baseline not found: ...)`, exit 2.

**Not run here:** `tools/help/test-help-gate.ps1` (self-test). Its wall time is unmeasured, so by the between-releases rule it runs at release. Its expected verdict is `help-gate-selftest: PASS (run=20 skipped=0)`; the two new cases and the reworked picture case are checked by reading only.

**Scope and hygiene:** three files changed (`assert-help-map.ps1`, `test-help-gate.ps1`, `tools/help/README.md`); no new file, no catalog file, no contract text. No probe added. No dead code: the old `cases` spelling is gone.

**RELEASE items (not built, not tested until the release):**

- RELEASE: `pwsh -NoProfile -File tools/help/test-help-gate.ps1` - expected `help-gate-selftest: PASS (run=20 skipped=0)`, exit 0. The pre-release sweep matches `^help-gate-selftest: PASS \(`, which the new form still satisfies.
- RELEASE: commit `tools/help/` (the owner decides when); after that `pwsh -NoProfile -File tools/help/assert-help-map.ps1` - expected `assert-help-map: PASS`, exit 0. Until then the gate is `COULD NOT VERIFY`, exit 2, and the pre-release sweep's help line will not read PASS.
- RELEASE: update the gap paragraph of `docs/contracts/AUTOMATED-CHECKS.md` once the self-test has run - it still says S0060 carries both gaps.
- RELEASE: the accepted-baseline header in `tools/help/help-map-baseline.txt` still names `CHECK-BASELINE 0.11`; the contract is 0.12. Comment only, outside this ticket's three files; fix it with the next edit of that file.

