# S0041 - Contract synchronization - StreamsPlayer Android, round 2026-10-25

**Ticket:** S0041
**Status:** Implemented
**Status note:** 2026-10-27: REPO-STAMP / HARNESS-PROFILE 0.13 read, no edit to the stamp or profile; RULE-DELIVERY and STREAM-BANK rows normalized; the four rows are one patch for the canon session after S0026's and S0037's (PLAN/S0041_contract-sync-2026-10-25/registry-sync.patch), not applied; catalog reader vectors replay PASS 44; check-sync then keeps only the item CZ table (S0053)
**Date:** 2026-10-25
**Phase:** contract synchronization - `HARNESS-PROFILE` and `REPO-STAMP` 0.12 to 0.13, the registry rows the catalog program cannot read, and the two rows never verified; independent of the build phases of S0017-S0024
**Verification level:** gate - each contract read against the product, the registry rows and pointers per the Definition of done below, and `tools/contracts/check-sync.ps1` exiting 0.
**Owner:** the StreamsPlayer Android session of this repository. Filed by the S0026 session on 2026-10-25 from the output of the catalog program `contract-lag.ps1 -Product "StreamsPlayer Android" -EmitSpec`, run against the catalog of that day; the part under "Contract synchronization spec" is that output, kept as it was written; what this repository adds is above it and under "Local additions".

## Captured request and migration scope

S0026 (DEVICE-EXCHANGE to 1.7) found, while running the repository's own check, work that does not belong to its one contract and that the owner asked to be filed rather than left:

- `tools/contracts/check-sync.ps1` exits 1 on `REPO-STAMP pointer does not name current version 0.13`: the `RULE-ADOPTION` pointer was last reviewed at 0.12 (S0013, 2026-10-23), and the catalog published 0.13 for `REPO-STAMP` and `HARNESS-PROFILE` the same day. The script stops at its first failure, so every later block is hidden behind it.
- Three own rows (`LIVE-BROADCAST`, `STREAM-BANK`, `RULE-DELIVERY`) have the shape the catalog program calls unreadable: `-` in `Implements` and `partial <version>` in `Reads`. S0026 normalizes the `DEVICE-EXCHANGE` row only.
- `INPUT-PARITY` and `MEDIA-CLASSIFICATION` are `pending` ("never verified").

Registry edits are proposals to the canon session (`CLAUDE.md` section 6), as S0020, S0022 and S0026 did: the patch and its reason go beside this ticket.

## What changes here

- **Target:** the version current when this ticket is worked, per contract (owner's rule of 2026-10-14, S0026).
- **The pointer first.** Read `REPO-STAMP` and `HARNESS-PROFILE` at the current version and update `docs/contracts/RULE-ADOPTION.md`; then `check-sync.ps1` can reach its registry and vector blocks. A new version of a contract this repository's own tooling reads (the stamp, the profile, the reader) may oblige a change to `.sza-canon.json` or `.sza-profile.json` - those are written by the adopt-canon run, never by hand, and the digest re-sync (warning SZA-CANON03) stays that run's.
- **Rows.** Normalize the three unreadable rows in the proposal (`Implements` carries the synchronized version for a producer or both, `Reads` for a pure consumer, `partial <version>` where the implementation is partial, one contract id per row). Do not rewrite the notes: they carry the evidence of S0013 and S0020.
- **Pending rows.** `INPUT-PARITY` waits for the physical gamepad and watch pass (S0004, S0007); `MEDIA-CLASSIFICATION` is the honest not-applicable record the catalog asked for (S0013) and may stay `pending` with that note, or the catalog is asked how a non-applicable row spells `Verified` (a proposal beside the contract).
- **Out of scope:** `DEVICE-EXCHANGE` (S0026 and its patch), `LIVE-BROADCAST` evidence (S0020, S0021, S0028), the unified-UI contracts (S0036), `STREAM-BANK` rules 9 and 12 (a carrier is still to be filed before 2026-12-31).

# Contract synchronization spec - StreamsPlayer Android

Generated 2026-10-25 by tools/contract-lag.ps1 from the registry and the contracts' document logs.
For the project session that owns the product: it files this as its own ticket, edits its own registry
rows and its own code, and nobody does either on its behalf. Surfaces covered: StreamsPlayer Android.

The log entries below are matched by version and by the contract ids a row names; a row that names none is
shown for every contract in its domain. Treat each list as a map, not as the whole change.

## What is owed

| Contract | Surface | Synchronized with | Current | State | Verified |
| --- | --- | --- | --- | --- | --- |
| `HARNESS-PROFILE` | StreamsPlayer Android | 0.12 | 0.13 | behind | 2026-10-23 |
| `REPO-STAMP` | StreamsPlayer Android | 0.12 | 0.13 | behind | 2026-10-23 |

## `HARNESS-PROFILE` - 0.12 to 0.13  (StreamsPlayer Android)

Home: domain `rule-adoption/`, status draft. Read the domain README at the current version, then the documents it names.

| Date | Version | Kind | What changed | Owed |
| --- | --- | --- | --- | --- |
| 2026-10-23 | REPO-STAMP 0.13, HARNESS-PROFILE 0.13 | additive | Define optional legacy-preserving carriers and dispatch, document-to-wire mapping and staged required-carrier migration. Add generated reader vectors; real delivery remains a separate evidence requirement. No existing payload key becomes required. | Review - adopt it, or record a dated exception saying why not |

## `REPO-STAMP` - 0.12 to 0.13  (StreamsPlayer Android)

Home: domain `rule-adoption/`, status draft. Read the domain README at the current version, then the documents it names.

| Date | Version | Kind | What changed | Owed |
| --- | --- | --- | --- | --- |
| 2026-10-23 | REPO-STAMP 0.13, HARNESS-PROFILE 0.13 | additive | Define optional legacy-preserving carriers and dispatch, document-to-wire mapping and staged required-carrier migration. Add generated reader vectors; real delivery remains a separate evidence requirement. No existing payload key becomes required. | Review - adopt it, or record a dated exception saying why not |

## Never verified (`pending`)

Nobody has read these contracts against the product since the catalog took its shape. Read each at its
current version and fill the row; there is no log range to show because no starting version is recorded.

- `INPUT-PARITY` (StreamsPlayer Android), current 0.4
- `MEDIA-CLASSIFICATION` (StreamsPlayer Android), current 0.12

## Rows the program cannot read

`Implements` / `Reads` must be one version, `partial <version>` or `-` (REGISTRY section 2). Move the
detail into `Notes` and split multi-contract rows:

- `DEVICE-EXCHANGE` (StreamsPlayer Android), registry line 607: `partial 1.7`
- `LIVE-BROADCAST` (StreamsPlayer Android), registry line 606: `partial 1.3`
- `RULE-DELIVERY` (StreamsPlayer Android), registry line 618: `partial 0.12`
- `STREAM-BANK` (StreamsPlayer Android), registry line 605: `partial 2.4`

## Definition of done

1. Each contract above is read at its current version and cited by id and section, never by catalog path.
2. The implementation matches it, or the project declares a justified exception in its own REGISTRY section 3 row
   without prior owner approval: clause, scope, evidence, impact, compensating behavior and review condition.
   Temporary debt has an `until` date and remediation ticket; a permanent supported variant has a linked design record.
3. The product's own suite runs the catalog's conformance vectors at the current version; the command and
   its exit code are cited.
4. The product's own rows in REGISTRY section 2 carry `Implements`, `Reads`, `Verified` (today) and a one-line
   note, in the cell format of that section, one contract id per row.
5. Each `docs/contracts/<ID>.md` pointer names the current version.
6. The project may recommend corrections and additions at any point. It amends contracts it owns; for another owner,
   it files `PROPOSAL-<date>-<topic>.md` with evidence and compatibility consequences, retaining an exception where needed.
   Only its own implementation rows advance after fresh proof; another product's rows remain untouched.

## Local additions to the Definition of done

8. `pwsh -NoProfile -File tools/contracts/check-sync.ps1 -CatalogRoot <catalog locator of CLAUDE.md>` exits 0, or the remaining failure is named with its owner.
9. The `DEVICE-EXCHANGE` row listed above as unreadable is the S0026 proposal's; its application by the canon session is checked here, not redone.

## Research items

- **Open:** how a non-applicable contract spells `Verified` (the `MEDIA-CLASSIFICATION` row is `pending` on purpose) -
  a question for the catalog, answered by a proposal beside the contract if the owner wants the row to read as settled.
  Carrier: S0041
  Update 2026-10-27: answered by S0037's recommendation R1 (`PLAN/S0037_contract-sync-20261017/proposals.md`): `-` / `-`
  and the date of the declaration, note "not applicable, declared"; S0037's registry patch already spells the row so.
  Nothing further is filed here. Closes with the canon decision on R1.

## Result (run of 2026-10-27)

**What was done.** Each contract of the spec was read at its current version (`REPO-STAMP` 0.13 section 2 and the
2026-10-23 amendment, `HARNESS-PROFILE` 0.13 section 3 and the amendment, `RULE-DELIVERY` 0.12 section 5, `STREAM-BANK`
2.4 sections 2-4) and set against the tree.

- **Stamp and profile: no edit.** Both 0.13 texts are additive: the optional carriers (`schemaVersion`, `version`) are
  absent or legacy `"1"` = wire 1.0, valid indefinitely. `.sza-canon.json` is written by the adopt-canon run only, and
  that run also owns the digest re-sync (SZA-CANON03); `.sza-profile.json` keeps its legacy `"version": "1"`.
- **Pointer.** `docs/contracts/RULE-ADOPTION.md` already named 0.13 (S0037's pass). Re-reviewed: the review line is
  now 2026-10-27 / S0041, the stale "no generated vectors yet" and "reader lag is carried by S0037/S0041" sentences are
  replaced by what is true now. The index header in `docs/contracts/README.md` names S0041.
- **Rows.** `REPO-STAMP` and `HARNESS-PROFILE` to 0.13; `RULE-DELIVERY` from `-` / `partial 0.12` to `-` / `0.12` (the
  reader exception that made it partial is closed by S0037's patch and the catalog's vectors pass); `STREAM-BANK` from
  `-` / `partial 2.4` to `partial 2.4` / `partial 2.4` (the program reads `partial` from `Implements` only). One patch,
  four rows, earlier notes kept after "Earlier:": `PLAN/S0041_contract-sync-2026-10-25/registry-sync.patch`, with
  `registry-sync-reason.txt`. It sits on top of S0026's and S0037's patches (order in the reason file) and was verified
  on a scratch copy of the live registry: three `patch --binary` runs, exit 0, byte-identical to the proposed file.
  **The catalog was not edited.**
- **Pending rows.** `INPUT-PARITY` and `MEDIA-CLASSIFICATION` are dated by S0037's patch (code-reading evidence, and
  not applicable, declared). Nothing is added here.
- **Conformance vectors.** The catalog's generated reader vectors (`rule-adoption/vectors/`, 0.13) replayed against the
  installed plugin 2026.1026.3: `pwsh -NoProfile -File tools/tests/contract-reader.tests.ps1 -SourceRoot <copy of the
  plugin> -CatalogRoot <catalog>` exit 0, `contract-reader-tests: PASS (run=44 skipped=0)`; the eight source hashes of
  the manifest equal the installed files. The copy is needed because the script puts its scratch folder beside itself,
  i.e. inside the plugin cache.
- **`check-sync.ps1`.** Live catalog: exit 1, six findings (four registry cells, two item CZ). With the three patches
  applied to a scratch catalog: exit 1, two findings, both the vendored `DEVICE-EXCHANGE` item CZ table and its
  PROVENANCE hash - owner S0053, not a registry matter. `contract-lag` for this product: unparsed 4 to 0, behind 2 to 0,
  pending 2 to 0.
- **STREAM-BANK fresh proof.** `gradlew :phone:testStoreDebugUnitTest :wear:testStoreDebugUnitTest` filtered to the
  stream-bank classes, under the `Build.Main` lock: exit 0, phone 44 tests, Wear 19, zero failures, errors and skips.

**The seven compatibility-law answers (step 5), for the one contract of this ticket whose reader lives in this tree,
`STREAM-BANK`.** The stamp and profile have no reader here (the plugin's reader is replayed above), so they are not
applicable.

1. By name, not position: yes - `StreamCatalogCsvParser.kt:16-26` (lower-cased header map, `getOrNull`); `STREAM-BANK`
   document 03 section 3.1 requires it.
2. Higher MINOR accepted, unknown additions skipped: yes - extra columns are tolerated (`StreamCatalogCsvParser.kt:16`);
   unknown ZIP entries are skipped (`ImportStreamCatalogUseCaseTest`, "unknown entries are skipped and artwork comes from
   the same ZIP").
3. Higher MAJOR refused cleanly: `streams.csv` has no version carrier (additive-only, `STREAM-BANK` document 03 section 1.1);
   `collections.json` with an unknown `schemaVersion` is ignored whole with one log line, never partially
   (`StreamCollectionsJsonParser.kt:51-62`).
4. Unknown enum value to the documented default: yes - unknown topics fold to `General` (`StreamTopicFolding`, vector
   `stream-bank/topic-fold-v1.tsv`, run today); the closed category, language and country folds are
   `StreamCatalogFacetNormalizer` (tests run today).
5. User-authored data preserved: yes - rows are stamped `sourceOrigin = "CATALOG"` on import
   (`ImportStreamCatalogUseCase.kt:110`) and the prune deletes only `sourceOrigin = 'CATALOG'`
   (`StreamSourceDao.kt:238`).
6. Indexes bounds-checked: yes at the parser (`getOrNull`, `StreamCatalogCsvParser.kt:25`; a negative or non-numeric
   `favicon_index` becomes null, `:55`). The atlas tile lookup past the sheet was not re-read in this run.
7. Defaults written in the contract: yes - blank cells decode to `""`, `false` or null as `STREAM-BANK` document 03
   section 2 says; a blank `is_live` is read as `false`, which section 2.3 there allows for a consumer that does not
   surface or filter on it (the field is parsed and used nowhere else).

No answer is "no", so this run opens no ticket. Behaviour at a contract boundary was not changed.

**Observations and recommendations (not waivers).**

- `contract-reader.tests.ps1` builds its scratch folder from `$PSScriptRoot` (`../../temp/..`). Run against an installed
  plugin it writes into the plugin cache, so a consumer replay has to copy the plugin first. A `-ScratchRoot` parameter
  would remove the copy. For the canon session.
- `tools/harness.ps1 spec_catalog/validate` exits 2: Journal-to-FS fails because the journal still points S0031 at
  `PLAN/S0031_exchange-connection-diagnosis.md` while the file is in `PLAN/DONE/`. The same failure was recorded by
  S0037 and is not mine to mutate (another ticket's record); it is why this ticket's journal row is updated only if the
  harness accepts it (see the status note).

## Acceptance criteria

- [x] 1. Each contract of the spec is read at its current version and cited by id and section, never by catalog path.
- [x] 2. The implementation matches, or the exception stands: `STREAM-BANK` rules 9 and 12 stay the section 3
      exception (carrier S0050, until 2026-12-31); the `REPO-STAMP` and `RULE-DELIVERY` reader exceptions are closed in
      S0037's patch and S0041's evidence supports closing them (vectors PASS 44, probes of S0037).
- [x] 3. The catalog's conformance vectors run at the current version, command and exit code cited (Result).
- [ ] 4. The product's rows carry `Implements`, `Reads`, `Verified` (2026-10-27) and a one-line note in the section 2
      cell format. Written as a proposal for the canon session (CLAUDE.md section 6); closes when it is applied.
- [x] 5. Each `docs/contracts/<ID>.md` pointer touched names the current version (`RULE-ADOPTION.md`: `REPO-STAMP`
      0.13, `HARNESS-PROFILE` 0.13, `REPO-LAYOUT` 0.11, `RULE-DELIVERY` 0.12; `STREAM-BANK.md` 2.4 unchanged).
- [x] 6. Recommendations are recorded (Result, last list; `proposals.md` R1 of S0037 for the research item); no
      other owner's row or contract was touched.
- [ ] 8. `check-sync.ps1` exits 0: not yet. With the three patches it names one remaining defect, the vendored item CZ
      table, owner S0053.
- [ ] 9. The `DEVICE-EXCHANGE` row of S0026's patch: checked here, not redone. The live registry still has
      `partial 1.7`, so the patch is still pending in the catalog; it is the first link of the chain verified above.

## Last Audit

**Date:** 2026-10-27 | **Mode:** contract synchronization | **Evidence:** `PLAN/S0041_contract-sync-2026-10-25/`

**Proven by fresh commands (2026-10-27):** the reader-vector replay (exit 0, PASS 44); the real repository read by the
installed plugin (`check-compliance.ps1`: exit 1, one error SZA-VER03 [S0047], five warnings, no REPO-STAMP refusal);
the real profile loaded by the installed profile reader (exit 0, `version` `1`); the stamp digest equal to the digest of
the canon it names (recomputed with plugin 2026.1017.1); `check-sync.ps1` and `contract-lag.ps1` on the live and on the
proposed catalog; the focused `STREAM-BANK` unit run (exit 0, 44 + 19 tests); the patch chain on a scratch registry.

**Findings:** the four rows are behind or unparsed in the live catalog only because the three patches are not applied;
one defect outlives them (item CZ table, S0053).

**Unverified:** real delivery and an installed-consumer replay of a deployment (the canon's); the next adopt-canon run
emitting `"schemaVersion": "1.0"` into the stamp (not run here - it owns the digest re-sync too); any device, store or
release behaviour. No release, tag, upload or deployment is implied.

### Manual / on-target

- [ ] MANUAL: the canon session applies `registry-sync.patch` of S0026, S0037 and S0041, in that order, to
      `_meta/REGISTRY.md`, then reruns `tools/contracts/check-sync.ps1`; only the item CZ finding should remain.
