# S0056 - Switch off full builds and slow tests between releases (the 5 s line)

**Ticket:** S0056
**Status:** Verified
**Complexity:** complex - confirmed 2026-10-29: multiple scripts and rules, release gate wiring, and cross-ticket verification criteria; no product code
**Date:** 2026-10-29

## Captured request and migration scope

Captured from the canon session on behalf of the portfolio owner (2026-10-29). This ticket changes no product code and deletes no test: it moves the full build and every check above 5 seconds to the release path and leaves only the fast tier between releases. It is the portfolio-wide order for every repository; this is the StreamsPlayer_mob instance (phone, Wear OS app, watch face and the `exchange-core` JVM library).

**Priority: highest - first in this repo's queue, ahead of every other open ticket.**
**Source:** owner order of 2026-10-29 for the whole portfolio, made canon in the same deploy:
TESTING_AND_QA section 2 ("Between releases the ladder stops at the checks that take 5 seconds or less"),
section 5 (the release runs the full build and every tier), the INVARIANTS working stance "Between releases,
no full build and no test longer than 5 seconds", and the `RELEASE` audit outcome in the `spec-to-audit`
skill. Canon version 2026.10.29.2 or later (the `sza` plugin carries it; `claude plugin update sza@sza-unified-rules`).

### Problem

Full builds and multi-minute test suites run while technical pieces, point fixes and small improvements are
being made - after every edit, at every phase gate, at every ticket close, and in branch CI on every push -
while the owner waits. The owner's order: the full build and full testing happen only before or during a
release. Between releases only very fast and fast tests stay; any test, suite or gate longer than 5 seconds
runs only for release actions (or when the owner names that run).

### Do

1. **Inventory every place a build or a test runs outside a release**: the agent-rules file (`CLAUDE.md` /
   `AGENTS.md`), `.claude/commands/` and `.claude/skills/` (build, spec-do, close, audit, sweep commands),
   closure facades and gate scripts (close/complete/validate wrappers, pre-commit or pre-push hooks, watch
   scripts), the task runner's child prompt, and branch CI (`.github/workflows/*` with `push` /
   `pull_request` / `schedule` triggers). List each with what it runs.
2. **Class each test, suite and gate**: *fast* (5 s or less) or *release* (above 5 s). Take the time from
   records that already exist - CI step durations, gate timing logs, dev logs, earlier reports. A check with
   no recorded time is *release* until the next release run measures it. **Do not run a slow suite or a full
   build to time it**; a check expected to be fast may be timed with one direct call.
3. **Make the class machine-readable** where the runner reads it - a test category / tag / filter, a separate
   fast suite or script, or a `-Fast` / `-Release` switch - so "run the fast checks for this area" is one
   command whose run takes 5 s or less. Split a slow suite into a fast filter where that is possible; move a
   slow test to the release tier, never delete it.
4. **Change every between-release path to the fast tier only**: agent rules, project commands and skills,
   closure facades and phase gates, hooks, and the runner's child prompt call only fast checks plus a direct
   call of what changed. Branch CI on push / pull request runs only gates of 5 s or less (docs, links, lint,
   the fast tier) - or nothing; the full build and the release tier move to the release workflow / pre-flight
   so they run against the tag's own tree.
5. **Keep the release whole**: the release pre-flight (release script, release workflow, release doc) runs the
   full build and every tier, the release tier included. Nothing that ran before may disappear from the
   release path. A release gate is never weakened by this ticket.
6. **Write it down in the agent-rules file**: the fast command, the release command, and the measured list
   (check, seconds, date, how measured), plus the rule in one line with a pointer to TESTING_AND_QA section 2.
   A slow rung skipped during a ticket is recorded in its audit as a `RELEASE` item naming the command the
   release will run, and the report says "not built, not tested until the release".

### Must not

- Delete a test, weaken a release gate, or remove a check from the release path.
- Run a full build or any check above 5 s to prove this ticket.
- Trigger a release, push a tag, or change a frozen anchor.

### Done criteria

- The inventory and the fast/release list are in the ticket, each time with its source and date.
- A grep over the agent-rules file, project commands/skills, facades, hooks and branch CI shows no full build
  and no release-tier check on a between-release path (cite the grep and its hits).
- The fast command exists and one call of it for a touched area completes in 5 s or less (cite the timed run,
  its exit code and seconds).
- The release path names the full build and the release tier (cite the lines).
- Report: what was not built and not tested by this ticket (the release run proves the release tier).

### Starting points (seen on a quick look on 2026-10-29, not run - a start for the inventory, not its result)

Agent rules (`CLAUDE.md`; `AGENTS.md` is three lines that delegate to it). This repository carries no `.claude/` directory and no `scripts/` directory, so there are no project commands, skills, hooks or closure facade to change - the between-release paths are the lines below and the tickets:
- Section 5, "Repository layout": the audit rig bullet says to run `tools/audit/run-selftests.ps1` before any session; the Gradle bullet says "Build with `./gradlew`" and nothing about when; the in-app help bullet names `tools/help/` and its gate `assert-help-map.ps1`, "which the pre-release sweep runs".
- There is no fast command and no release command named in the agent-rules file yet (Do, item 6).
- Open tickets carry their own full-suite done criteria: `PLAN/S0021_*` (line 120), `S0027_*` (line 149), `S0028_*` (line 94) and `S0032_*` (line 93) each require `.\gradlew.bat :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest` (the last three add `:wear:testDebugUnitTest`) to exit 0; `S0035_*` (lines 364 and 410) already uses filtered `--tests` runs. Under the new rule those criteria become a fast-tier check plus a `RELEASE` item naming the suite the release will run. `PLAN/TICKET_TEMPLATE.md` carries no verification step.

Gate and self-test scripts that can run between releases:
- `tools/audit/run-selftests.ps1` (three steps: the coverage gate's fixtures, the rig's fixture suite, the coverage gate on the real pair), `tools/audit/test-coverage-check.ps1`, `tools/audit/coverage_check.ps1`.
- `tools/port-ledger/test-port-ledger.ps1` ("after editing these scripts"), `tools/help/test-help-gate.ps1`, `tools/help/assert-help-map.ps1`, `tools/ci/test-launcher.ps1` (CLI doubles, no network).
- Gradle: `phone/build.gradle.kts` makes every `Test` task depend on `:exchange-core:test` (line 60) and forks the phone suite every 75 classes with a 1536 MB heap, so one phone test class pulls the library suite along - the place to look for a fast filter. `build-logic/` has its own unit tests under `build-logic/convention/src/test/`.

The release path that must stay whole (do not weaken):
- `tools/release/pre-release-sweep.ps1`: port-ledger drift, compliance, secrets-leak check, the five release builds (`:watchface:assembleRelease`, `:wear:assembleStoreRelease`, `:wear:assembleNoLegalRelease`, `:phone:assembleStoreRelease`, `:phone:assembleNoLegalRelease`), signature and version checks, the verdict file. `tools/port-ledger/check-drift.ps1` ("start of every release") belongs to it. `-SkipBuild` exists there; keep it a release-time option only.
- Builds also run on GitHub Actions on request: `./a.ps1 r` / `./a.ps1 d` dispatch `.github/workflows/android-build.yml` through `tools/ci/build-packages.ps1`.

Branch CI: none that builds or tests on push. `.github/workflows/android-build.yml` is `workflow_dispatch` only; `.github/workflows/pages.yml` runs on push to `main` for `site/**` and publishes pages without building or testing the app. The grep for `push:`, `pull_request:` and `schedule:` over `.github/workflows/` therefore shows `pages.yml` only; record that result in the inventory and keep it that way.

Times already on record: none found in `docs/` or the tool READMEs on a quick look (the audit README mentions about 2 s of adb round trip per tree read and a 5 s sampling interval, which are not suite timings). Per Do, item 2, every Gradle suite and every self-test above is `release` until a recorded time says otherwise; a script expected to be fast (`test-coverage-check.ps1`, `test-port-ledger.ps1`, `assert-help-map.ps1`) may be timed with one direct call.

The unattended runner is the canon's portfolio queue runner; this repository has no runner prompt of its own to edit (confirm in the inventory). A runner may hold the head ticket when this one is picked up; it takes effect from the next pick.

## Spec pass and tactical plan - 2026-10-29

### Owner inputs (Approval gate)

- **Related tickets:** S0021, S0027, S0028 and S0032 carry prospective Gradle suite criteria affected by this order; S0051 owns the document registry. None blocks S0056.
- **Scope:** The owner's portfolio order captured above applies to this repository; no product or UI change is ordered.

Shared-contract boundary: no wire format, product behaviour or catalog contract is changed. The existing
CHECK-VERDICT vocabulary governs release checks. There are no required open research items or owner decisions.
The document registry does not exist yet (PORTAL-DOCS, S0051); this task affects agent rules and tool documentation,
not product surfaces. Historical evidence in other tickets is not rewritten.

Inventory, inspected 2026-10-29: `CLAUDE.md` requires the audit self-tests each session; the help and port-ledger
READMEs require their gates after changes. `README.md` gives build/test commands; `tools/ci/build-packages.ps1`
builds and tests on explicit `workflow_dispatch`, and `tools/release/pre-release-sweep.ps1` builds release variants
and runs drift, help, compliance, secrets and pointer checks. `pages.yml` publishes on site changes, without an app
build; `android-build.yml` is dispatch-only. No local `.claude` commands or skills, task-runner prompt, hook, or
closure facade was found. Open S0021, S0027, S0028 and S0032 require full Gradle suites; S0035's filtered runs are
historical evidence, not prospective done criteria. The help gate has three accepted baseline entries. The contract
sync check has known findings tracked in S0041 and S0053. `PLAN/DONE/S0026_contract-sync-2026-10-12.md` records
the full exchange/phone/wear suite at 132 s and a filtered phone/core suite at 73 s on 2026-10-25; both are release.
The privacy manifest helper and runtime-notices inventory also invoke Gradle; the site workflow's pre-push
manifest-build instruction and notices regeneration now defer that work to release. The release sweep checks the
privacy tables after its release builds. Other unmeasured suites and self-tests are release until measured. A direct
`pwsh -NoProfile -File tools/help/assert-help-map.ps1` call on 2026-10-29 exited 0 in 0.739 s and printed
`assert-help-map: PASS (30 surfaces, 27 rows, 3 baseline, 30 pages, 8 picture sets checked, subject=...)`.

Coverage and topology: inventory and classification cover Do 1-2 and all done criteria; the measured help gate is
the area-specific fast command, while Android/Gradle has no measured fast form. The release sweep consumes the
existing scripts and Gradle tasks and retains its existing builds and checks (Do 3-5 and Must not). Rules and
prospective ticket criteria consume the resulting commands (Do 4, 6). Historical evidence and product surfaces are
out of scope; no test is deleted. No phase consumes an artifact produced later.

1. [x] Add release-tier suite and self-test execution to the pre-release sweep, preserving existing gates; add a
   fast syntax check for that script and verify the named commands and failure handling by static inspection.
   Files: `tools/release/pre-release-sweep.ps1`, `tools/release/check-syntax.ps1`, `tools/release/test-check-syntax.ps1`.
2. [x] Update agent and tool rules to name the measured fast command and release path, and revise prospective
   full-suite criteria in S0021, S0027, S0028 and S0032 to record the suites as RELEASE items. Verify with grep.
   Files: `CLAUDE.md`, `tools/audit/README.md`, `tools/port-ledger/README.md`, `tools/help/README.md`,
   `tools/third-party-notices/README.md`, `tools/privacy/permissions.ps1`, `.github/workflows/pages.yml`, those specs.
3. [x] Audit the inventory, release coverage, fast measurement and probe invariant; record omitted slow proof as
   RELEASE items. Verify by the declared static checks. Files: this spec.

Pre-implementation blockers: [x] scope and owner order resolved; [x] existing release commands and gate inputs
located; [x] no shared-contract amendment required. Self-review: every Do and Must not item maps above, and every
consumed script and Gradle module exists. The release sweep itself cannot be executed in this ticket because it
builds and runs unmeasured suites; its fresh end-to-end proof is a RELEASE item.

Phase-boundary audits, 2026-10-29: the release script retains drift, help, compliance, secrets, contract pointers,
five builds and signing checks; it adds self-tests, Gradle suites and the permissions check. The suite path records
nonzero exit, missing expected task executions or missing success output as failure. The syntax gate's fixture
self-test exercises success, syntax failure and missing input. No P0 or P1 finding was identified by static
inspection. The rules and four prospective criteria point slow work at release; historical evidence is unchanged.
The document registry is absent (S0051); agent and tool documents are affected, product surfaces unchanged.

## Last Audit

**Date:** 2026-10-29 | **Mode:** static | **Outcome:** Verified
**Counts:** PASS 7 | WARN 0 | FAIL 0 | MANUAL 0 | UNCHECKABLE 0 | EXEMPT 1 | RELEASE 1

1. PASS - `pwsh -NoProfile -File tools/release/check-syntax.ps1`, exit 0, 0.307 s; expected PASS on the changed
   release script | actual `release-syntax: PASS` naming that script.
2. PASS - `pwsh -NoProfile -File tools/release/test-check-syntax.ps1`, exit 0, 1.015 s; expected all three
   outcome cases | actual `release-syntax-selftest: PASS (3 cases)`.
3. PASS - `pwsh -NoProfile -File tools/help/assert-help-map.ps1`, exit 0, 0.739 s; expected help-map PASS |
   actual 30 surfaces, 27 rows, 3 accepted baseline entries, 30 pages and 8 picture sets.
4. PASS - `rg -n 'release-syntax|ReleaseSyntaxSelfTests|AuditSelfTests|PortLedgerSelfTests|HelpGateSelfTests|LauncherSelfTests|BuildLogicTests|AllUnitTests|Permissions|assembleRelease|assembleStoreRelease|assembleNoLegalRelease|Record-Check' tools/release/pre-release-sweep.ps1 CLAUDE.md`, exit 0; expected full release builds and suite checks plus failure propagation | actual named checks, five build tasks and `Record-Check` failure paths.
5. PASS - `rg -n '^\s*(push|pull_request|schedule|workflow_dispatch):' .github/workflows`, exit 0; expected
   site push and explicit dispatch only | actual `pages.yml` push and dispatch, `android-build.yml` dispatch.
6. PASS - `rg -n 'S0056' phone/src wear/src watchface/src exchange-core/src`, exit 1; expected no probes |
   actual no matches.
7. PASS - `pwsh -NoProfile -File tools/release/check-syntax.ps1 -Path tools/privacy/permissions.ps1`, exit 0,
   0.261 s; expected parse PASS | actual `release-syntax: PASS` naming the privacy helper.
8. EXEMPT - User-facing product behaviour and shared wire contracts are unchanged by this tooling task.
9. RELEASE - `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1`: full builds, all Gradle unit suites,
   the unmeasured tool self-tests and the release pre-flight integration were not run between releases. The
   release must run that command against its candidate and inspect the written verdict.

