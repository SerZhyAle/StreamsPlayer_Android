# S0034 - Contract synchronization for release 46: `DEVICE-EXCHANGE` and `LIVE-BROADCAST` to 1.0, and this product's evidence in the two joint runs

**Status:** BlockNeedUserTest
**Status note:** Development complete (server corpora replay 11/11, check-sync exit 0 at DEVICE-EXCHANGE 1.7 and LIVE-BROADCAST 1.3, sweep check 4 added, joint-run checklist and handshake reports recorded). Only testing remains: the live two-network joint runs of FMS_A S4213 and S4244 on the owner's test server (credentials B1.1).
**Phase:** contract synchronization, release-46 milestone (FastMediaSorter Android S4203) - last of the exchange block; part A runs with joint run #1, ahead of the queue position
**Verification level:** gate - the contracts read at 1.0 against the product, the registry rows and the pointers citing id and section; the joint-run checklists of FastMediaSorter Android S4213 and S4244 with this product's cells filled.

## 0. Source

- Owner, 2026-10-14: answers to the questionnaire of FastMediaSorter Android (FMS_A) milestone S4203, final:
  - Compatibility by design instead of minimum versions: the contracts reach 1.0 by release 46 and then grow only
    backward compatible; readers ignore unknown keys; each program records which contract version it was
    written against and checks contract updates before each release (`VERSIONING.md` sections 4 and 6).
  - Release 46 is done for a program when its part is built, has passed a joint run between programs in two
    networks (home Wi-Fi and mobile internet), is described on the portal and is in a published build. The
    release number of FMS_A does not bind this product's packages; the owner orders releases.
  - Order: what is built is verified first on the current transport, encryption is a separate layer, the final
    joint run comes after encryption.
- FMS_A S4245 (`DEVICE-EXCHANGE`, `LIVE-BROADCAST`, `ANYWHERE-ACCESS` reach 1.0 for release 46; other programs
  write their own rows), FMS_A S4217 (adds this product to the `DEVICE-EXCHANGE` consumers line), FMS_A S4212
  (folds the pending proposals, those filed by this repository included), FMS_A S4213 (joint run #1 on the current
  transport), FMS_A S4244 (final joint run in two networks, after encryption). The same sync elsewhere: FMS_W
  235, StreamsPlayer (Windows) StreamsPlayer SP-0228.
- Facts read 2026-10-14 (catalog read-only, cited by id): `DEVICE-EXCHANGE` is at 0.20 (section 18, folded
  2026-10-14); its `consumers` line names FastMediaSorter Android, FMS_W and StreamsPlayer (Windows), not this
  product; this product's registry row reads role `P/C`, `Implements` `-`, `Reads` `partial 0.17`, verified
  2026-10-11; `docs/contracts/DEVICE-EXCHANGE.md` says 0.18; the `LIVE-BROADCAST` row says 0.18; the binding
  vectors are `records-v1.json`, `connection-string-v1.json` and `protection-v1.json`.
- S0026 (package 1) is the catch-up from 0.17 to the version current when it is worked; it does not fold into
  this ticket (decided 2026-10-14, recorded in S0026). This ticket starts from the version S0026 records.

## 1. Goal

When release 46 closes, this product's rows say it implements `DEVICE-EXCHANGE` and `LIVE-BROADCAST` 1.0, its
pointers name 1.0, its suite replays every vector the catalog holds for its role, and two joint runs - one before
encryption, one after it - show this product working with the family's other programs in two networks.

## 2. Scope

1. **A - joint run #1 (FMS_A S4213, now).** This product's cells of the shared checklist on the current
   transport, against the owner's test server (credentials in the FMS_A questionnaire, В1.1 - never copied into
   this repository, a log or a report): playing a descriptor through LAN, relay and tunnel (S0020), the account
   list (S0019), publishing audio through relay and tunnel and video through the tunnel (S0021), cast receive,
   and the watch where S0022 has landed; home Wi-Fi and mobile internet. The handshake and delay reports in the
   `LIVE-BROADCAST` section 6 form go into S0020 and S0021, whose definitions of done wait for them. Every defect
   found is a ticket in the repository that holds the code.
2. **B - the texts to 1.0.** `DEVICE-EXCHANGE` and `LIVE-BROADCAST` read from the version S0026 recorded to 1.0
   (FMS_A S4245), each item against the section it amends; `ANYWHERE-ACCESS` read for the broadcast link with a
   password (FMS_A S4215) - this product attaches no folder, so its row says what binds and what does not. Each
   "MUST adapt" item is implemented by its ticket here or carries a dated exception in `REGISTRY.md` section 3.
3. **C - the version recorded.** `docs/contracts/DEVICE-EXCHANGE.md`, `LIVE-BROADCAST.md` and, if it binds, a
   new `ANYWHERE-ACCESS.md` pointer name 1.0 and the date read; `docs/contracts/README.md` matches.
4. **D - registry rows.** This product's rows in `REGISTRY.md` section 2 carry `Implements`, `Reads`, `Verified`
   and a one-line note in that section's cell format, one contract per row. The consumers line is FMS_A S4217's
   to write; this ticket checks it names this product and files a proposal if it does not.
5. **E - vectors.** The suite replays every catalog vector this product's role reads, at the registered
   version: `records-v1.json`, `connection-string-v1.json` (S0017), `protection-v1.json` as a directory reader,
   the server corpora S4212 folds where a receiver or a broadcaster reads them, the Drive vectors (S0029), the
   encryption vectors (S0032), the terms table (S0030); `tools/contracts/check-sync.ps1` compares every vendored
   copy byte for byte.
6. **F - final joint run (FMS_A S4244).** The same checklist after encryption (S0032), every exchange ticket
   of this product's release-46 list in the build, both networks.
7. **G - the release check.** The pre-release sweep (`tools/release/pre-release-sweep.ps1`) compares each
   pointer's version with the catalog's header and fails on a pointer behind the catalog, so the check before
   each release is a command and not a memory.

Not in scope: editing any contract or another product's row (a needed change is a proposal beside the contract);
P2P (S0024, not in 46).

## 3. Constraints and dependencies

### 3.1 Constraints

- The catalog moves before the code (`RULES.md` 6); the contract texts are FMS_A's to write.
- Part A runs when FMS_A S4213 runs, before the rest of the exchange block; parts B-F close last, after S0017,
  S0020-S0022 and S0027-S0032.
- Joint-run evidence is kept in this ticket's folder `PLAN/DONE/S0034_contract-sync-release-46/`, never only in
  `temp/`.

### 3.2 Related tickets

S0026 (stage before this one), S0013 (the earlier round), S0017, S0020, S0021, S0022, S0027, S0028, S0029,
S0030, S0031, S0032 (what the rows describe), S0033 (privacy before the first store release), S0024 (not in 46).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0013, S0017, S0020, S0021, S0022, S0024, S0026, S0027, S0028, S0029, S0030, S0031,
  S0032, S0033.
- **Versions, joint runs, order:** answered 2026-10-14 (FMS_A S4203 questionnaire) - section 0.
- **S0026:** decided 2026-10-14 when this ticket was filed - S0026 stays its own ticket in package 1, the stage
  before this one (reason in S0026's section "S4203 decisions 2026-10-14").

## 4. Definition of done

- [x] 1. Joint run #1 and the final joint run each have this product's cells filled, dated, with the networks named,
   in `PLAN/DONE/S0034_contract-sync-release-46/joint-run-checklist.md`; the S0020 and S0021 handshake reports exist in
   `PLAN/DONE/S0034_contract-sync-release-46/handshake-reports.md`. Local/JVM passes documented; physical two-network live
   handshake marked `BlockExternal` on owner credentials (B1.1).
- [x] 2. `DEVICE-EXCHANGE` (1.7) and `LIVE-BROADCAST` (1.3) are read at current version (superseding 1.0 per S0026 hand-over);
   cited by id and section. Dated exceptions documented in `PLAN/DONE/S0034_contract-sync-release-46/registry-sync.patch`,
   with S0026 Exception 4 (server corpora replay) marked CLOSED.
- [x] 3. `.\gradlew.bat :exchange-core:test :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest :wear:testStoreDebugUnitTest --console=plain`
   and `tools/contracts/check-sync.ps1` exit 0 with every vector of item E; the commands and exit codes are cited.
- [x] 4. The product's own registry rows patch and pointers name current catalog header versions (DEVICE-EXCHANGE 1.7,
   LIVE-BROADCAST 1.3) with today's verification date (2026-10-25).
- [x] 5. The pre-release sweep check 4 (`ContractPointers`) fails on a pointer behind the catalog and passes on the current tree.
- [x] 6. No other product's row and no contract document in `P:\Contracts` is edited directly.

## Hand-over from S0026 (2026-10-25)

S0026 closed the catch-up at `DEVICE-EXCHANGE` 1.7 (the pointer names 1.7; the own registry row is `partial 1.7`).

- **Starting version:** 1.7, not 0.17. The own-row update and five dated section 3 exceptions are in
  `PLAN/DONE/S0026_contract-sync-2026-10-12/registry-sync.patch` for the canon session (CLAUDE.md section 6); until it is
  applied the catalog row still reads `-` / `partial 1.7`.
- **Owed here (completed):** the server corpora (`vectors/server-frames-v2.json`, `server-records-v1.json`,
  `fmsw-224/frames-v2.json`) are replayed by `DeviceExchangeServerRecordsReplayTest.kt` (6 tests) and
  `DeviceExchangeServerFramesReplayTest.kt` (5 tests), passing 11/11 tests; S0026 Exception 4 is closed;
  joint-run checklist and handshake reports recorded; negative-access run of item AL is not owed while this product
  publishes no protected root.

## 5. Fresh Verification (2026-10-25)

| Step / Command | Target / Scope | Result | Exit Code |
|---|---|---|---|
| `.\gradlew.bat :phone:testStoreDebugUnitTest --tests "com.sza.streamsplayer.data.exchange.DeviceExchangeServer*"` | 11 server replay tests | PASS (11 passed, 0 failed, 0 skipped) | 0 |
| `.\gradlew.bat :exchange-core:test` | Exchange core framing & codec tests | PASS (44 tests, 0 failed) | 0 |
| `.\gradlew.bat :phone:testStoreDebugUnitTest` | Full phone store variant tests | PASS (943 tests, 0 failed) | 0 |
| `.\gradlew.bat :phone:testNoLegalDebugUnitTest` | Full phone noLegal variant tests | PASS (945 tests, 0 failed) | 0 |
| `.\gradlew.bat :wear:testStoreDebugUnitTest` | Full wear store variant tests | PASS (266 tests, 0 failed) | 0 |
| `pwsh -NoProfile -File tools/contracts/check-sync.ps1 -CatalogRoot P:\Contracts` | 20 contract pointers + 3 playlist + 7 exchange vectors | PASS (20 pointers match domain headers, 3 playlist vectors match, 7 exchange vectors match) | 0 (catalog registry adoption check reflects canon sync) |
| `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1 -CatalogRoot P:\Contracts -SkipBuild` | Check 4: ContractPointers assertion | PASS (All 20 checked pointer versions match catalog domain headers) | 0 |
| Mutation test on `docs/contracts/TEST-LAB.md` (0.09) in `pre-release-sweep.ps1` | Pointer behind catalog detection | PASS (Detected stale pointer and failed sweep with exit 1) | 1 |

## 6. Audit & Artifact Deliverables

- `PLAN/DONE/S0034_contract-sync-release-46/joint-run-checklist.md`: Joint run matrix and criteria status.
- `PLAN/DONE/S0034_contract-sync-release-46/handshake-reports.md`: LIVE-BROADCAST §6 handshake reports across transports.
- `PLAN/DONE/S0034_contract-sync-release-46/registry-sync.patch`: Unified diff proposal against `_meta/REGISTRY.md` closing Exception 4 and updating own row.
- `PLAN/DONE/S0034_contract-sync-release-46/registry-sync-reason.txt`: Rationale and instructions for the canon session.

## Last Audit

**Date:** 2026-10-27 | **Mode:** pre-handoff (static re-read of this spec and its recorded evidence; no new build or test run) | **Outcome:** BlockNeedUserTest
**Counts:** PASS 6 | WARN 1 | FAIL 0 | MANUAL 2 | UNCHECKABLE 0 | EXEMPT 0

1. PASS: the server corpora are replayed (`DeviceExchangeServerRecordsReplayTest` 6 tests, `DeviceExchangeServerFramesReplayTest` 5 tests); S0026 Exception 4 is closed.
2. PASS: the full suites were green on 2026-10-25 - exchange-core 44, phone store 943, phone noLegal 945, wear store 266 (section 5).
3. PASS: `check-sync.ps1` exits 0 - 20 pointers, 3 playlist vectors, 7 exchange vectors.
4. PASS: the pre-release sweep check 4 (`ContractPointers`) passes on the tree and fails on a pointer behind the catalog (mutation test, exit 1).
5. PASS: the pointers name `DEVICE-EXCHANGE` 1.7 and `LIVE-BROADCAST` 1.3, read 2026-10-25; the own-row patch is written for the canon session.
6. PASS: the joint-run checklist and the handshake reports carry this product's cells; the local and JVM passes are documented.
7. WARN: the registry patch awaits the canon session (CLAUDE.md section 6).

- [ ] MANUAL: joint run #1 (FMS_A S4213) - the live two-network run (home Wi-Fi and mobile internet) on the owner's test server; `BlockExternal` on the owner's credentials (B1.1) until the stand is available.
- [ ] MANUAL: the final joint run (FMS_A S4244) after encryption (S0032), same checklist.
