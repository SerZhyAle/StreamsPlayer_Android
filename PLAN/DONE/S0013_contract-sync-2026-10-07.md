# S0013 - Contract synchronization - StreamsPlayer Android, round 2026-10-07

**Status:** Verified
**Phase:** contract synchronization round of 2026-10-07 - independent of the build phases of handoff section 8
**Verification level:** gate - registry rows and pointers read against the current contract versions, the conformance command and its exit code cited per the Definition of done below.
**Owner:** the StreamsPlayer Android session of this repository, on the portfolio owner's order of 2026-10-07; the text below is the canon session's specification, placed here verbatim.

Written by the canon session on 2026-10-07, on the owner's order, from the shared contracts catalog's registry and
the contracts' document logs. Hand it to the session that owns this product: that session files it as its own
ticket, edits its own registry rows and its own code, and nobody does either on its behalf. Contracts are cited
by id and section, never linked; the catalog moves before the code (CONTRACTS.md).

**What happened.** On 2026-10-07 the owner sealed every contract in the catalog (only the contract's owner edits it;
every other product files a `PROPOSAL-<date>-<topic>.md`) and had all open proposals accepted. Each accepted
proposal raised the version of the contract it changed and left a dated document-log row. This spec lists
what that asks of this product. A proposal that still needs the owner's choice is **not accepted yet** - where a
log row below says so, the question is open and the text does not bind on that point.

Registry surfaces covered: StreamsPlayer Android.

## In short - what this round asks of this product

No code change is asked of StreamsPlayer Android by this round. Its rows move:

- `PACKAGE-VERSIONING` 0.3 added the three StreamsPlayer Android families to the band table and named
  `StreamsPlayer Android` in `consumers`; the contract is at 0.4 now (the 0.4 step is a Windows rendering and
  does not concern this product). Move your `Implements` to the current version and note the date.
- `ICON-SET` 0.27, `INSTALL-TRUST` 1.2, `UPDATE-MANIFEST` 0.11 and `DEVICE-EXCHANGE` / `LIVE-BROADCAST` are
  listed below only where a row of this product exists; read each against the product and say so in the row.
- Most of this product's rows are `pending` - nobody has read the contract against it since the catalog took
  its shape. Fill them or keep them `pending` with a note naming what is missing.

## Contracts changed on 2026-10-07

| Contract | Surface | Synchronized with | Current | Verified |
| --- | --- | --- | --- | --- |
| `HARNESS-PROFILE` | StreamsPlayer Android | 0.10 | 0.11 | 2026-10-02 |
| `REPO-LAYOUT` | StreamsPlayer Android | 0.10 | 0.11 | 2026-10-02 |
| `REPO-STAMP` | StreamsPlayer Android | 0.11 | 0.12 | 2026-10-02 |
| `RULE-DELIVERY` | StreamsPlayer Android | 0.11 | 0.12 | 2026-10-02 |

### `HARNESS-PROFILE` - 0.10 to 0.11

Home: domain `rule-adoption/`, status draft, owner sza-unified-rules. Read the domain README at the current version, then the documents it names.

| Date | Version | Kind | What changed | Owed |
| --- | --- | --- | --- | --- |
| 2026-10-07 (this round) | 0.11 (`HARNESS-PROFILE`) | additive | Rule 2: `paths.changelog` is derived from the stamp rather than fixed against it - the stamp's `ledgerFile`, else `dev/CHANGELOG.md`, under `ledgerShape` `2`; empty under `4` and `none`; an explicit profile value wins; no stamp means the shipped default. An empty value means the repository keeps no dev-log and the tools that need one say so and create nothing. The derivation under `ledgerShape` `1` and `3` is **not decided** and the default stays as it was. Corrects a premise of fact in the 2026-10-02 decision: the harness does read `paths.changelog` (`devlog/add_to_dev_log.ps1` writes it, `spec_catalog/complete.ps1` checks it), so the contradiction is not merely latent in a repository that .. | Review - adopt it, or record a dated exception saying why not |

### `REPO-LAYOUT` - 0.10 to 0.11

Home: domain `rule-adoption/`, status draft, owner sza-unified-rules. Read the domain README at the current version, then the documents it names.

| Date | Version | Kind | What changed | Owed |
| --- | --- | --- | --- | --- |
| 2026-10-07 (this round) | 0.11 (`REPO-LAYOUT`) | additive | Rule 3 gains a third exception to the type-prefix requirement: the release package files that section 8 of the canon's `RELEASE_AND_DISTRIBUTION` names, under the default names `PLAN/RELEASE_QUEUE.md`, `PLAN/RELEASE_READY.md`, `PLAN/RELEASE_QUEUE_DONE.md` or under whatever names the repository writes into its agent-rules file. Closes the item the spec-scheme proposal left open and the third bullet of CyrFlip's seconding (`PROPOSAL-2026-09-23-own-spec-scheme.md`, `PROPOSAL-2026-09-25-cyrflip-rule-adoption.md` section 1). Documents what the canon prose already allows; no gate checks the prefix, so no reader changes. Not covered: a free-form backlog file such as CyrFlip's `Contract_Conformance_ .. | Review - adopt it, or record a dated exception saying why not |

### `REPO-STAMP` - 0.11 to 0.12

Home: domain `rule-adoption/`, status draft, owner sza-unified-rules. Read the domain README at the current version, then the documents it names.

| Date | Version | Kind | What changed | Owed |
| --- | --- | --- | --- | --- |
| 2026-10-07 (this round) | 0.12 (`REPO-STAMP`) | breaking | Rule 4 writes the meaning of an absent or unparseable `canon.adoptedOn`, which 0.10 and 0.11 left open: with `canon.reconciledOn` also absent or unparseable the age is unknown, and an unknown age is read as past the 180-day window, so a differing digest is an error - never an age of zero. An absent date draws no finding of its own. Oldest-consumer test: the reader that shipped before reads such a stamp as age zero, so it silently judges a warning where this says error - hence `breaking`, although no key changes and no stamp has to change (the adoption run always writes `adoptedOn`); only a reader moves, and section 6 says so. Closes the first of the three items the stamp-defaults proposal le .. | MUST adapt - an implementation on the older shape does something wrong against this one |

### `RULE-DELIVERY` - 0.11 to 0.12

Home: domain `rule-adoption/`, status draft, owner sza-unified-rules. Read the domain README at the current version, then the documents it names.

| Date | Version | Kind | What changed | Owed |
| --- | --- | --- | --- | --- |
| 2026-10-07 (this round) | 0.12 (`RULE-DELIVERY`) | breaking | Rule 5 carries the same reading at the rung where it takes effect: where neither `canon.reconciledOn` nor `canon.adoptedOn` is present and parseable, the age is unknown and read as past the window, so a differing digest is an error. The ladder was silent on that case and the canon's reader filled the silence with age zero (`PROPOSAL-2026-09-23-stamp-defaults.md`). Kind and effect as in the `REPO-STAMP` row above: a reader changes, a stamp does not. | MUST adapt - an implementation on the older shape does something wrong against this one |

## Never verified (`pending`)

- `ICON-EXTERNAL` (StreamsPlayer Android), current 0.11
- `ICON-RENDER` (StreamsPlayer Android), current 0.17
- `ICON-SET` (StreamsPlayer Android), current 0.27
- `INPUT-PARITY` (StreamsPlayer Android), current 0.3
- `LIVE-BROADCAST` (StreamsPlayer Android), current 0.17
- `MEDIA-CLASSIFICATION` (StreamsPlayer Android), current 0.10
- `PACKAGE-VERSIONING` (StreamsPlayer Android), current 0.4
- `STREAM-BANK` (StreamsPlayer Android), current 2.2
- `USER-PLAYLIST` (StreamsPlayer Android), current 0.12

## Definition of done

1. Each contract above is read at its current version and cited by id and section, never by catalog path.
2. The implementation matches it, or a dated exception (reason, `until` date) is in REGISTRY section 3. A
   deviation that nothing records is a violation (CONTRACTS.md section 4).
3. The product's own suite runs the catalog's conformance vectors at the current version where the contract has
   any; the command and its exit code are cited. Where it has none, the row says so.
4. The product's own rows in REGISTRY section 2 carry `Implements`, `Reads`, `Verified` (today) and a one-line
   note, in the cell format of that section, one contract id per row. A row that cannot be verified honestly
   stays `pending` with a note naming what is missing.
5. Each `docs/contracts/<ID>.md` pointer names the current version.
6. **No contract document is edited by this product, drafts included - every contract is sealed.** A needed
   change, or a disagreement with an accepted text, is a `PROPOSAL-<date>-<topic>.md` beside the contract in
   its domain folder; the owner decides. Another product's registry rows are not edited either.

## Acceptance

- [x] Every item of the "Definition of done" section above is met, and the check is recorded in the closing audit of 2026-10-23 below (contracts read at current versions and cited by id/section; deviations under dated section 3 exceptions with named carriers; vectors matched byte-for-byte; all 14 own rows carry `Implements`/`Reads`/`Verified` 2026-10-23; all 19 pointers name current versions; no contract document and no other product's row edited).

## Last Audit

**Date:** 2026-10-23 | **Mode:** closing synchronization | **Outcome:** Verified - the ticket closes

The 2026-10-19 closing pass (pointers, probe evidence under `temp/S0013-S0026/`) is completed here against the
catalog of 2026-10-23. The deviations that remain are recorded as dated REGISTRY section 3 exceptions with named
carriers, exactly as the Definition of done provides.

### What closed since the 2026-10-10 re-audit

1. **Topic carry done.** The owner accepted the reconciliation proposal on 2026-10-17 (STREAM-BANK 2.4: preserve
   the General topic law, repair the source first). The source normalizer was repaired and the repair is carried:
   `StreamTopicFolding` folds unknown nonblank topics to `General` in this tree and in the source tree, held by
   the repo vector `stream-bank/topic-fold-v1.tsv` and its tests. The topic half of the STREAM-BANK exception is
   closed; the exception narrows to rules 9/12 artwork, which still has no feature carrier.
2. **MEDIA-CLASSIFICATION scope settled.** The scope proposal was accepted 2026-10-17 (contract 0.11): the
   product is outside primary-category classification until it implements that boundary; the registry row is the
   honest not-applicable record the amendment asks for, no Implements/Reads version claimed. 0.12 adds `.jxl`
   and names the Windows product - nothing owed here.
3. **ICON-SET registry index repaired by the owner** (REGISTRY section 1 reads 0.36, matching the domain header;
   the 2026-10-10 finding is resolved).
4. **Unknown-age reader still undelivered.** Probes re-run on the installed plugin 2026.1021.2: unknown age
   (missing or invalid dates) with a differing digest still WARNs where REPO-STAMP 0.12 rule 4 and
   RULE-DELIVERY 0.12 rule 5 require an error; the equal-digest control half is now correct. Both dependency
   exceptions are renewed until 2026-12-31, carried by S0037 and the canon delivery proposal.
5. **Canon digest lag and archived-spec warning.** The stamp reconciled 2026-10-17 against canon 2026.10.17.1;
   the installed canon is 2026.10.21.2 (WARN SZA-CANON03, 0 errors). The adopt-canon re-sync owns the newer
   digest; a contract sync does not hand-write the stamp. SZA-CTR02 warns that the archived `PLAN/DONE/S0003`
   spec names the catalog by path; the archived record is not rewritten by this ticket.
6. **Suite blocker fixed.** The `StreamSourceCatalogMergeTest` / `StreamCollectionsJsonParserTest` failures
   (also seen by the 2026-10-19 pass) were test pollution: `ExchangeViewModel.fetchDirectory` killed its
   coroutine when the directory client answered a `refused` envelope with a bare `IllegalStateException` that
   neither of its catch clauses took, and the uncaught exception surfaced in a later, order-dependent test.
   `DeviceExchangeDirectoryClient.list` now maps a `refused` envelope to `DeviceExchangeRefusedException` (the
   account client's pattern, reason preserved) and a regression test replays it against the fake server; the
   suites are green again.
7. **Later-round catch-up named, not duplicated.** LIVE-BROADCAST read at 1.3 (item R producer password gap
   carried by S0028/S0032/S0037, a release blocker for the producer); DEVICE-EXCHANGE read through 1.7 at
   changelog/amendment level (1.6 section 29 and 1.7 section 30 bind producer/server surfaces that land with
   S0021); ICON-SET 0.36, ICON-RENDER 0.24 and INPUT-PARITY 0.4 read, with unified-UI adoption carried by
   S0036/S0037. S0026 keeps the connection-string corpus replay and the `Implements`-cell normalization; S0034
   keeps the joint runs.

### Fresh verification

Commands ran on 2026-10-23. `<catalog-root>` is resolved using CLAUDE.md's catalog locator; Gradle ran under the
Build.Main lock.

| Command | Exit | Observed output / scope |
| --- | --- | --- |
| `.\gradlew.bat :build-logic:convention:test :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest --console=plain` | 0 | BUILD SUCCESSFUL; 932 store / 934 noLegal phone tests and 24 build-logic tests, zero failures/errors/skips, including the new directory-refusal regression test. Live catalog ZIP and topic-fold vector tests included. |
| `pwsh -NoProfile -File tools/contracts/check-sync.ps1 -CatalogRoot <catalog-root>` | 0 | PASS: 19 pointer versions match domain headers; 14 own adoption rows unique with current version cells; 3 required USER-PLAYLIST vectors and 1 DEVICE-EXCHANGE vector match current catalog bytes. |
| Installed plugin 2026.1021.2 `tools/check-compliance.ps1 -RepoRoot .` | 0 | 0 errors, 2 warnings: SZA-CANON03 (digest one re-sync behind canon 2026.10.21.2) and SZA-CTR02 (archived DONE/S0003 path mention) - both recorded above. |
| Same reader, isolated probes [attachments/reader-probes-20261023/](../S0013_contract-sync-2026-10-07/attachments/reader-probes-20261023/) | 0 (canon03 stays WARN) | Unknown age + differing digest: WARN SZA-CANON03 where the contract requires error - exceptions renewed (each probe's 4 layout FAILs are the bare isolation dir's missing LICENSE/README/CHANGELOG/agent file; only the SZA-CANON03 line carries this verdict). Equal digest + invalid dates: no SZA-CANON03 finding (delivered half). |
| `pwsh -NoProfile -File tools/harness.ps1 spec_catalog/validate` | 0 | 8 OK, 1 WARN, 0 FAIL. The warning is the pre-existing missing-spec hygiene debt of S0009/S0010/S0012/S0016/S0018/S0019, unrelated to this round. |
| `pwsh -NoProfile -File tools/harness.ps1 spec_catalog/release-queue -Validate` | 0 | 39 tickets, no drift. |
| `pwsh -NoProfile -File tools/port-ledger/check-drift.ps1` | 1 | 122 of 721 ledger rows changed in the source since port; carries and their decisions are release-time work. No source-parity claim is made by this ticket. |

Durable evidence: `PLAN/S0013_contract-sync-2026-10-07/attachments/` holds the Gradle log, the check-sync,
drift and reader-probe outputs above. No physical-device, broadcast-handshake or release test ran.

### Handoff

- S0026 (open): connection-string corpus replay at the current version, `Implements`-cell normalization, its own
  current-version verification; S0034 (open): joint runs and server corpus replay; S0037 (open): the 2026-10-17
  round implementation catch-up, including the carriers named in the renewed reader exceptions.
- Artwork rules 9/12 (STREAM-BANK): a feature carrier must be filed before the 2026-12-31 exception date.
- The canon digest re-sync (adopt-canon run) and the reader delivery remain with the canon session and S0037.
- The S0004 source-carry donor patch stays prepared; S0004's own closure owns it.
