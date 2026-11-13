# S0040 - Report the test lab to the portfolio - equipment, test practice and contract proof (TEST-LAB 0.1)

**Status:** BlockByOtherTask
**Status note:** Blocker: S0053 - repair the DEVICE-EXCHANGE item CZ comparison and fixture so the required contract-sync check can pass; both required validators also await the release boundary.
report in the catalog, one pointer file, one registry row. No code, test or harness of the product is touched, and
no queue position is claimed (section 2.2, item 1).
**Phase:** independent of the build phases of handoff section 8; a report, not a feature
**Complexity:** hard - assessed at the spec pass: the report crosses the shared catalog boundary and its required contract check depends on another ticket's repair.
**Depends on:** S0053
**Verification level:** the report exists in the catalog in the form of `TEST-LAB` section 3, every item with a source
or `unknown`; the pointer file and the registry row; `pwsh -NoProfile -File tools/harness.ps1 spec_catalog/validate`
and `pwsh -File tools/contracts/check-sync.ps1 -CatalogRoot <catalog>` both exit 0. No device is switched on for this
ticket: the report states what the repository already records, not what a fresh run would show.

## 0. Origin

The portfolio owner ordered on 2026-10-23, through the canon session: collect from every project what it already
knows about the hardware zoo (devices, watches, machines), the emulators and virtual machines, and the way its
products are tested, and make inter-project contracts of it. Owners by platform: Android - FMS_A, Windows - FMS_Win,
web - the sza.od.ua hub. The catalog now holds the draft `TEST-LAB` 0.1 (its home is the catalog's `test-lab/`
folder; the catalog locator is in `CLAUDE.md`); catalog `RULES` section 2 records the carve-out that makes this a
contract. Cited here by id and section, never linked.

This repository's platform: Android (a phone app, a Wear OS app and a watch face; it is a flavor of the FMS_A
toolchain). Its platform owner: FMS_A (FastMediaSorter Android, repository `FastMediaSorter_mob_v2`), which owns
the `TEST-LAB-ANDROID` profile. This repository's role: reporter only. Products reporting to the same profile
(`TEST-LAB` section 2): FMS_A and Tourist_mob.

## 1. Goal

This repository files one report of what it knows, in the form of `TEST-LAB` section 3, so the platform owner can
fold it into the platform profile. Nothing more is changed: no harness, no test, no behaviour.

## 2. What to do

### 2.1 Steps

1. Read `TEST-LAB` sections 1-4 in the catalog. They are the whole instruction for the form of the report.
2. Inventory **this repository only**. Search its docs, scripts, harness, CI definitions, agent-rules file
   (`CLAUDE.md`), the `rules/contrib` record (canon repository, read only), tickets and the ticket archive
   (`PLAN/DONE/`) for devices, emulators and AVDs, virtual machines, browsers, hosts, peripherals, test services,
   test tiers and commands, "verified on hardware" language, recurring traps and the contracts whose conformance
   asks for a device. Read what you find instead of judging by file name.
3. Ask the owner in one batch for what is not written anywhere (the devices that exist only in their head: a model,
   an OS version, which product uses which). Put the unanswered questions in the report as `unknown`.
4. Write the report as `<catalog>/test-lab/PROPOSAL-<YYYY-MM-DD>-streamsplayer-mob-inventory.md` in the form of
   section 3 (parts A-E). Status line: `proposed`. Every item names its source (a repository file, or "owner's
   statement") or says `unknown`. Say what you did not verify.
5. No secrets in the report: no serial numbers, tokens, passwords, private addresses or account names - an alias
   and a role (`TEST-LAB` rule 2 of section 4).
6. Add a pointer file `docs/contracts/TEST-LAB.md` (a few lines: id, version, home in the catalog, this
   repository's role) and one adoption row for `TEST-LAB` in the registry (`Implements -` while the report is
   missing, `0.1` and the date once it is filed). `CLAUDE.md` names the catalog once, already.
7. Do not change any code, test or harness. A finding that needs a change becomes its own ticket.

### 2.2 Notes specific to this repository

1. **Queue placement.** This ticket was filed without a queue position and is not scheduled: it is not in
   `PLAN/RELEASE_QUEUE.md`, and the owner places it or leaves it out. The harness projects every `Draft` record that
   stands in neither plan file into the current package on the next journal write (it did so for S0039), so the row
   may appear in package 1 on its own after any later catalog write; the owner then moves it to the `--` bucket or
   removes it. A report is not release work.
2. **Pointer and registry row need the repository's own check taught first.** `tools/contracts/check-sync.ps1`
   throws "Unexpected or duplicate own row" on a registry row it has no binding for. Step 6 therefore includes one
   binding (`test-lab` -> `TEST-LAB`, with its pointer) in that script, in the same edit as the row. This is the
   contract check of this repository, not the product's code, a test or the harness; S0039 phase P0 teaches the same
   script a domain for `EXCHANGE-MARK`, so whichever ticket lands first adds the shape and the other adds its line.
   If the owner reads step 7 as forbidding even that edit, the row and the pointer wait and the report is filed alone.
3. **Two surfaces, one platform.** The phone app and the Wear OS app (and the watch face) are both Android, so the
   one report covers all three and says in part A which device or emulator proves which module.
4. **A report, not a re-run.** The repository records its emulator and device evidence in tickets and in
   `docs/S0007_WEAR_VERIFICATION.md`. The report cites those records and states, per `TEST-LAB` section 4 rule 3,
   what it did not re-verify; it does not start an emulator to confirm them.
5. **Related tickets.** S0004 and S0007 (the device acceptances still open, which part B2 and C2 must name),
   S0025 (the audit rig and its owner decision 3 on the canonical emulator levels and physical devices; the answer,
   once given, feeds part A), S0035 (emulator walk matrix), S0039 (parallel campaign ticket, see item 2), and
   S0013, S0026 and S0037 (contract-sync runs whose registry normalisation the new row follows).

## 3. Known sources (found when the ticket was written)

- `docs/S0007_WEAR_VERIFICATION.md` - Wear emulator `Wear_OS_Small_Round` (API 37, 384 px at 320 dpi), what it showed, the two device acceptances still unchecked
- `PLAN/DONE/S0007_wear-app-and-pairing.md` - the same emulator pass as a ticket; no paired phone and watch, no physical Wi-Fi listener yet
- `PLAN/DONE/S0002_gradle-skeleton.md`, `PLAN/DONE/S0002_gradle-skeleton/evidence.md` - phone emulator (API 35) and Wear emulator (API 37), installs read back with `dumpsys package`
- `PLAN/DONE/S0010_release-pipeline-dry-run.md` - install and in-place update on a generic x86_64 phone emulator (API 36); decision 4.5 names Android TV and big screens as the expected main use
- `PLAN/DONE/S0018_exchange-account-entries.md`, `PLAN/DONE/S0019_exchange-quick-join.md` - an instrumented `AndroidKeyStore` test and a walkthrough on a `Pixel_9` AVD (API 35), D-pad checked
- `PLAN/DONE/S0004_phone-shell.md`, `docs/PHONE_SHELL_ICON_MAP.md` - device evidence from host-side adb and Python drivers; live TalkBack, a physical gamepad and a TV remote remain manual
- `docs/contracts/INPUT-PARITY.md` - conformance note: emulator evidence only; physical TalkBack, gamepad, TV-remote and paired-watch checks open
- `PLAN/DONE/S0011_android-product-model.md` - the 2026-10-10 resolution that names the gap: no physical Windows listener in this workspace
- `PLAN/S0025_deep-dynamic-audit.md` section 7 - host-side adb measurement rig, disposable emulator image with a clean snapshot, exit codes 0 / 1 / 2; owner decision 3 (canonical emulator levels and devices) is open
- `PLAN/S0035_in-app-help-f1-and-help-button.md` - emulator walk matrix with `adb` screenshots and `uiautomator dump`; F1 only with a hardware keyboard
- `PLAN/S0008_watch-face.md` - the watch face installed on the API 37 Wear emulator with `adb install -r`; watch-face minimum level 36
- `.github/workflows/android-build.yml`, `tools/ci/` - the CI runner (`ubuntu-latest`) and the build launcher; a search found no emulator or device job
- `phone/src/test/resources/robolectric.properties`, `phone/src/androidTest/` - unit tier on Robolectric SDK 34; the one instrumented test class found; `gradle/libs.versions.toml` holds the platform levels (phone 26, Wear 28, watch face 36)
- `CLAUDE.md` section 5, and the gitignored `temp/` (run logs and the Python/adb drivers behind the records above; absent on another machine) - SDK location and build command; a search found no committed adb wrapper

## 4. Done when

- The report exists in the catalog, in the form of section 3, with sources or `unknown` on every item.
- The pointer file and the registry row exist.
- No file in this repository restates the catalog text; nothing was changed in code, tests or harness.
- The repository's own ticket validators run clean (cite the command and its exit code):
  `pwsh -NoProfile -File tools/harness.ps1 spec_catalog/validate` and, once the row exists,
  `pwsh -File tools/contracts/check-sync.ps1 -CatalogRoot <catalog>`.
- The owner is told which questions are still open.

## 5. Resume and audit, 2026-10-29

The report was filed on 2026-10-23 and accepted into `TEST-LAB-ANDROID` 0.2 on 2026-10-29. Its dated decision
records what the platform owner took and which equipment and practice questions remain. The report deliberately
records unknowns: those questions are inputs for the platform owner and S0025, not missing answers needed to file it.
The pointer, index row, registry adoption row and `test-lab` binding in the contract check exist. The report is a
point-in-time record, not a new device run. No document registry exists here yet (S0051); the affected records are
the TEST-LAB report, pointer, pointer index and catalog adoption row. Other documentation is unchanged by this pass.

This is a complex lifecycle: the report, pointer, registry and check cross more than three files and a shared
contract boundary. No catalog format or contract behaviour was changed in this pass. The next gate is blocked:
S0053 owns the existing item CZ table comparison defect in the required contract check. A direct comparison using
the check's first-table extraction returned exit 1: expected the catalog table and vendored table to match; actual
32 versus 35 lines. S0053 must repair the comparison and its fixture before this ticket can prove its stated gate.

## Last Audit

**Date:** 2026-10-29 | **Mode:** static | **Outcome:** Broken
**Counts:** PASS 3 | WARN 0 | FAIL 1 | MANUAL 0 | UNCHECKABLE 0 | EXEMPT 1 | RELEASE 2

### Action items
1. PASS - report headings and acceptance decision: `rg -n '^(Status:|## [A-E]\.|## Decision)'` on the report; exit 0; expected sections and decision | actual sections A-E and accepted status.
2. PASS - pointer and index: `rg -n 'TEST-LAB' docs/contracts/TEST-LAB.md docs/contracts/README.md`; exit 0; expected pointer and index | actual both present.
3. PASS - adoption and binding: `rg -n 'TEST-LAB.*StreamsPlayer Android|test-lab.*TEST-LAB'` on the catalog registry and `tools/contracts/check-sync.ps1`; exit 0; expected own row and binding | actual both present.
4. FAIL - item CZ comparison: direct extraction of the first catalog table using the existing check's predicates compared it with the vendored file; exit 1; expected equal | actual 32 catalog lines versus 35 vendored lines. `tools/contracts/check-sync.ps1`, S0053 item 3.
5. EXEMPT - physical-device and emulator runs: the order asks for a report of recorded evidence only.
6. RELEASE - `pwsh -NoProfile -File tools/harness.ps1 spec_catalog/validate`: unmeasured between-release command, not run. Expected exit 0; actual not run.
7. RELEASE - `pwsh -NoProfile -File tools/contracts/check-sync.ps1 -CatalogRoot <catalog locator from CLAUDE.md>`: unmeasured between-release command, not run. Expected exit 0; actual not run; the direct CZ comparison above demonstrates one blocking mismatch.

The release sweep does not currently call either command; run both explicitly at the release boundary. No app build or
test was run for this documentation-only pass. No S0040 probe was found under the app source roots (`rg -n 'S0040'
phone/src wear/src watchface/src exchange-core/src`, exit 1; expected no matches | actual no matches).
