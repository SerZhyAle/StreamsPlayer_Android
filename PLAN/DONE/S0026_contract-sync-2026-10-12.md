# S0026 - Contract synchronization - StreamsPlayer Android, round 2026-10-12

**Status:** Implemented
**Status note:** 2026-10-25: DEVICE-EXCHANGE read through 1.7 and held or recorded item by item; pointer names 1.7; exchange suites green (forced rerun, exit 0). The own-row normalization and five dated exceptions are one patch for the canon session (PLAN/DONE/S0026_contract-sync-2026-10-12/registry-sync.patch) - not applied to the catalog, so DoD items 2 and 4 are met in the catalog only after that. Handed over to S0021, S0028, S0034.
**Phase:** contract synchronization round of 2026-10-12 - `DEVICE-EXCHANGE` 0.18 to 0.19, independent of the build phases of S0017-S0024
**Verification level:** gate - the contract read against the product, the registry row and the pointer citing id and section, per the Definition of done below.
**Owner:** the StreamsPlayer Android session of this repository, on the portfolio owner's order of 2026-10-12; the text below is the catalog session's specification, placed here verbatim.

# Contract synchronization spec - StreamsPlayer Android - `DEVICE-EXCHANGE` to 0.19

Written 2026-10-12 by the catalog session, after the portfolio owner accepted
`PROPOSAL-2026-10-12-resource-pin-protection.md` (FMS_W, local specification 216) and the contract owner
folded it into `DEVICE-EXCHANGE` 0.19 (section 17, items AG to AL). For the project session that owns
StreamsPlayer Android: it files this as its own ticket, edits its own registry rows and its own code, and
nobody does either on its behalf. This spec covers one contract - `DEVICE-EXCHANGE`; from the canon repo
(`sza-universal-rules`), `pwsh -File tools/contract-lag.ps1 -Product "StreamsPlayer Android"` lists
everything else that is behind or partial for this product (two icon contracts are, today - out of this
spec's scope).

## What is owed

| Contract | Surface | Synchronized with | Current | State | Verified |
| --- | --- | --- | --- | --- | --- |
| `DEVICE-EXCHANGE` | StreamsPlayer Android | partial 0.17 | 0.19 | partial | 2026-10-11 |

## `DEVICE-EXCHANGE` - partial 0.17 to 0.19  (StreamsPlayer Android)

Home: domain `device-exchange/`, status draft. Read the domain README at the current version, then the
documents it names. The log entries below are the map, not the whole change: read sections 16 and 17 in
full, each item against the section it amends. The row says `partial`: close the gap to 0.19, or list each
missing rule as a dated exception.

| Date | Version | Kind | What changed | Owed |
| --- | --- | --- | --- | --- |
| 2026-10-10, owner decision | 0.18 | additive | Section 16, items Y to AF, folding `PROPOSAL-2026-10-10-exchange-connection-string.md` (FMS_W, asks 1-6) and `PROPOSAL-2026-10-10-connection-string-entries-and-leave.md` (FastMediaSorter Android, asks 1-9) with the owner's answers to both (ticket S4153). Y - the `FMSEXCH1` connection string, its `.fmsexch` file and QR, WHATWG form-urlencoded canonical bytes, recognition by content, the `fp` trust rule (new section 5.5). Z - a consumer or receiver is complete without a produce .. | Review - adopt it, or record a dated exception saying why not |
| 2026-10-12, owner decision | 0.19 | breaking (draft) | Section 17, items AG to AL, folding `PROPOSAL-2026-10-12-resource-pin-protection.md` (FMS_W, local specification 216) with the portfolio owner's acceptance of 2026-10-12 and the owner's decisions on the fields, mechanism names and version transition the proposal left open. AG - `protection` on a resource record (`pin-v1`), the `pin-v1` device capability declared at `enroll`, the server's access gate by capability, `protection-unsupported`, legacy unprotected records unchanged .. | MUST adapt - an implementation on the older shape does something wrong against this one |

## What the two folds oblige for this product

This product's profile, from its own registry note of 2026-10-11: receiver and broadcaster
(`product` `streamsplayer-android`); the `FMSEXCH1` codec (S0017) and outsider receiver playback (S0020)
are implemented and tested at 0.17; the broadcaster (S0021), quick join (S0019) and watch enrollment
(S0022) are pending. Against that profile:

- **0.18 (section 16, items Y to AF).** The implemented 0.17 surfaces are re-read against the fold: the
  connection string's canonical bytes and trust rule (item Y, section 5.5), the receiver completeness
  (item Z), the entries and the quick join (items AA and AD, pending S0019), one outgoing entry for the
  broadcaster (item AC, pending S0021), receiving from any deployment a descriptor names (item AF - the
  S0020 work already matches its shape). Adopt each, or record a dated exception per item.
- **0.19 (section 17, items AG to AL).** Nothing to build today: this product attaches no `sftp-share`
  resource and publishes none, and a broadcast never carries `protection` (6.3 is untouched). What binds it
  as a directory reader and a future client: a protected resource is listed locked with the deny explicit,
  never hidden, never an error (section 9 rule 10); the server hands it no `access` for such a resource,
  because it has not declared `pin-v1` (17 item AG); it never imports one unlocked (17 item AJ). The day a
  version of this product attaches resources, it implements the `pin-v1` capability or keeps the explicit
  deny - one of the two, never neither.
- **The registry row itself.** This product's `DEVICE-EXCHANGE` row is unreadable by
  `tools/contract-lag.ps1`: `Implements` says `-` while `Reads` says `partial 0.17`, so the program cannot
  decide the synchronized version and drops the row from every report. With role P/C the synchronized
  version belongs in `Implements`. Normalize the cells in this catch-up run, in the format of
  `REGISTRY.md` section 2 - this product's own row to write.

## Definition of done

1. `DEVICE-EXCHANGE` is read at 0.19 and cited by id and section, never by catalog path.
2. The implementation matches it, or a dated exception (reason, `until` date) is in `REGISTRY.md` section 3.
3. The product's own suite runs the catalog's conformance vectors at the version the registry names; the
   command and its exit code are cited. Where the fold's evidence does not exist yet
   (`vectors/protection-v1.json`, the negative-access run), the row says so rather than claiming it.
4. The product's own rows in `REGISTRY.md` section 2 carry `Implements`, `Reads`, `Verified` (today) and a
   one-line note, in the cell format of that section, one contract id per row.
5. Each `docs/contracts/DEVICE-EXCHANGE.md` pointer names 0.19.
6. No other product's row and no contract document is edited. A needed contract change is a proposal
   beside the contract (`PROPOSAL-<date>-<topic>.md`), and the catalog moves before the code.

## S4203 decisions 2026-10-14

S4203 is the release-46 device-exchange milestone of FastMediaSorter Android (FMS_A). Its owner's answers of
2026-10-14 ask this product for a release-46 contract sync: `DEVICE-EXCHANGE` from 0.17 to the current version
and then to 1.0 (FMS_A S4245), the version recorded, the registry row, and this product's evidence in the two
joint runs. That work is S0034.

**This ticket does not fold into S0034** (decided 2026-10-14 when S0034 was filed). Reasons:

- This ticket stands in package 1, the next release, and package 1 carries the exchange client (there is no
  feature gate): a release that carries a contract's code checks that contract's updates first (`VERSIONING.md`
  section 6). S0034 closes only after the final joint run and 1.0, the last steps of the milestone - folding
  this ticket into it would tie package 1 to the whole milestone, and the package choice is the owner's.
- The registry row is unreadable by `tools/contract-lag.ps1` today; normalizing it does not need to wait.
- The repository's pattern is one ticket per contract-sync round (S0013, this ticket); S0034 is the release-46
  round.

What changes here:

- **The target is the version current when this ticket is worked**, not 0.19 (owner: each program checks
  contract updates before each release). On 2026-10-14 that is 0.20: section 18, the bounds of a receiver
  declaration and the unanswered enroll, folded 2026-10-14 - it touches the receiver declaration of S0020 item 7.
  Definition-of-done items 1 and 5 read "the current version" for "0.19".
- **Hand-over.** S0034 starts from the version this ticket records in the registry row and the pointer.

## Result 2026-10-25

**Target version: 1.7**, the version current when this ticket was worked (the owner's rule of 2026-10-14 above; the
text of the round named 0.19). `DEVICE-EXCHANGE` was read at 1.7 and cited by id, section and item.

### Item map

State words: **held** (the code and a suite or a ticket that is Implemented carry it), **n/a** (a role this product
does not take), **exception** (a dated section 3 row in the proposal, below).

| Fold | Item (section) | State | Carrier and evidence |
| --- | --- | --- | --- |
| 0.18 | Y (5.5) the `FMSEXCH1` text, canonical bytes, trust rule | held | S0017; `ExchangeConnectionStringVectorsTest` replays `vectors/connection-string-v1.json` (104) and `vectors/fmsw-224/connection-string-v1.json` (117), 221 cases, both flavors |
| 0.18 | AR (5.5) `i`, the invitation | held | validated and discarded by the sign-in-only client; the `i` cases are part of the replay |
| 0.18 | BN, BO (5.5) sign-in surface, clarifications | held | S0019 / S0017; hostile-input tests: refused with one message and no connection |
| 0.18 | Z (3, 9) complete without a producer | held | `ExchangeAsk2DependencyTest` |
| 0.18 | AA, AB (9) entries; removing an entry revokes its device | held | S0018; `ExchangeAccountEntryLifecycleTest` (stable `deviceId`, identity, revoke order with the server up and down) |
| 0.18 | AD (5.2) the quick join | held | S0019 (Implemented) |
| 0.18 | AE (5.5) a client may write a string | n/a | "may": not built, no deviation |
| 0.18 | AC (6.1, 9) roles declared at `enroll`; one outgoing entry | held / exception | `broadcaster` and `receiver` are declared at `enroll`; the outgoing entry is items DS to DU |
| 0.18 | AF, BK (7.7, 9) any deployment a descriptor names, no entry for it | held | S0020; `DeviceExchangeTrustRuleTest`, `BroadcastPlaybackResolverTest`, the emulator observations of S0020 |
| 0.20 | AM (6.1, 7.2) the bounds of an `enroll` | held for the receiver declaration | phone: `ExchangeReceiverDeclaration.toJson` writes `modes` (3) and `transports` (4) and omits `plays` (12 pairs exceed 8); watch: `WearExchangeAccountRepository.enrollment` writes 1 mode by 3 transports and the 3 `plays` pairs, inside the bound; roles are 2 entries on both. The bounds of the text members (`login`, `password`, `deviceName`..) were not re-audited here |
| 0.19 | AG (6.1, 7.2) `protection`, `pin-v1` | held | no `capabilities` member, so no `pin-v1`; a record with `protection` keeps `isLocked`, yields no `access`, descriptor or root; an unknown `scheme` stays locked |
| 0.19 | AH, AI, BG (the producer's boundary, PIN release and keeping) | n/a | this product publishes no resource and asks for no PIN |
| 0.19 | AJ (3, 9 point 10) the explicit deny | held / exception | no attach, no import, no `access`, a schema 2 Drive wrapper is refused; the per-record locked row is not drawn - exception 1 |
| 0.19 | AK (10) alignment | n/a | no contract change owed by this product |
| 0.19 | AL (11) vectors and the publication gate | held / not claimed | `vectors/protection-v1.json` replayed client side (`DeviceExchangeProtectionVectorsTest`, 3 tests); the negative-access run needs a producer and is not claimed |
| 1.6 | DS, DT, DU (29) | exception | the per-broadcast switch `publishToExchange`, off by default; no server line on the broadcast surface - exception 2 |
| 1.7 | DV (30) `verifiers` | exception | not sent - exception 3 |
| 1.0 | server corpora (11 rung 2, BS) | exception | none replayed - exception 4 |
| 1.0 | section 27 and `vectors/e2e-v1.json` | exception | no sealing layer (S0032 is a Draft) - exception 5 |

### Fresh verification

Run on 2026-10-25 from the repository root under the Build.Main lock (a stale lock of a dead session was reclaimed
by the harness). `<catalog-root>` is the catalog locator of `CLAUDE.md`.

| Command | Exit | Observed |
| --- | --- | --- |
| `.\gradlew.bat :exchange-core:test --rerun :phone:testStoreDebugUnitTest --rerun --tests "com.sza.streamsplayer.data.exchange.*" --tests "com.sza.streamsplayer.domain.model.ExchangeConnectionString*" --tests "com.sza.streamsplayer.ui.exchange.*" :phone:testNoLegalDebugUnitTest --rerun <same three filters> --console=plain` | 0 | BUILD SUCCESSFUL in 1m 13s; the three test tasks executed (not UP-TO-DATE). exchange-core 17 tests; phone 495 tests per flavor; zero failures, errors and skips. Per flavor: `ExchangeConnectionStringVectorsTest` 221, `DeviceExchangeRecordVectorsTest` 7, `DeviceExchangeProtectionVectorsTest` 3. Extract: `PLAN/DONE/S0026_contract-sync-2026-10-12/attachments/rerun-tests-extract.txt` |
| `.\gradlew.bat :exchange-core:test :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest :wear:testStoreDebugUnitTest :wear:testNoLegalDebugUnitTest --console=plain` | 0 | BUILD SUCCESSFUL in 2m 12s. The wear tasks executed: 266 tests per flavor, zero failures. The exchange-core and phone tasks were UP-TO-DATE, so for them the earlier execution stands (result XML of 00:57: phone 1053 store / 1055 noLegal tests, exchange-core 17, zero failures, errors and skips) and the forced rerun above is the fresh proof. Extract: `PLAN/DONE/S0026_contract-sync-2026-10-12/attachments/closing-tests-extract.txt` |
| `pwsh -NoProfile -File tools/contracts/check-sync.ps1 -CatalogRoot <catalog-root>` (reworked in this pass to run every block and print every finding; before it stopped at the first) | **1** | four findings, all of the `RULE-ADOPTION` family, which moved to 0.13 after the 2026-10-23 round: `REPO-STAMP` and `HARNESS-PROFILE` pointer not naming 0.13, and the two own rows still at 0.12 (S0041). Everything about this contract passes: `INFO: 18 of 20 pointer versions match` (the two misses are those), `INFO: 15 own adoption rows read, 2 finding(s) above`, `PASS: 3 required USER-PLAYLIST vectors match`, `PASS: 4 DEVICE-EXCHANGE vectors match current catalog bytes`. Output: `attachments/check-sync.txt` |
| `.\gradlew.bat :exchange-core:test :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest :wear:testStoreDebugUnitTest :wear:testNoLegalDebugUnitTest :phone:assembleStoreDebug :wear:assembleStoreDebug --console=plain`, after the `productVersion` change below | 0 | BUILD SUCCESSFUL in 4m 45s, 110 tasks executed. exchange-core 17; phone 1054 store / 1056 noLegal (one new test each); wear 266 per flavor; zero failures, errors and skips; the new test `hello carries the product version bound to the client and no literal` is in the result XML. The two debug assemblies compile the Hilt graph of both applications. Output: `attachments/productversion-build-extract.txt` |
| `pwsh -NoProfile -File <sza plugin>/tools/contract-lag.ps1 -CatalogRoot <scratch copy> -Product "StreamsPlayer Android"`, once on the catalog registry and once with the patch applied | 0 | (output in `attachments/contract-lag-before-after.txt`) before: `DEVICE-EXCHANGE` under "unparsed rows" (rows 15, unparsed 4); after: `DEVICE-EXCHANGE partial synced 1.7 current 1.7 verified 2026-10-25` (partial 1, unparsed 3) |
| `patch -p1 --binary -i registry-sync.patch` on a scratch copy of the catalog registry | 0 | applies cleanly; the result is byte-identical to the proposed file; the catalog file was not touched |

Nothing ran on a physical device or against a real exchange server for this round.

### The registry update is a patch, not an edit

`CLAUDE.md` section 6 sends catalog changes to the canon session as an exact diff with the reason, and S0020 and S0022
did the same. `PLAN/DONE/S0026_contract-sync-2026-10-12/registry-sync.patch` holds the whole change - the own row
(`Implements` `partial 1.7`, `Reads` `partial 1.7`, `Verified` 2026-10-25, a note) and five dated section 3
exceptions, each `until` 2026-12-31 - and `registry-sync-reason.txt` beside it says why. It supersedes the
`DEVICE-EXCHANGE` hunk of the registry proposal recorded in S0022 (same base row, its watch evidence carried over); the
`DEVICE-EXCHANGE` hunk of S0020's `registry-evidence.patch` has a base row that no longer matches the catalog.

### Found on the way, and what was done with it

- **Fixed here - the device version the server sees.** `hello` (twice: `DeviceExchangeAccountClient.helloEnvelope` and the revoke path of `ExchangeAccountManager`) and the phone's `enroll` (`ExchangeViewModel`) sent the literal `"2.61"` as `productVersion`, so the directory of other devices (6.1) showed a version this product never had. They now send the app's `BuildConfig.VERSION_NAME`: a new `ExchangeProductVersion` in `:exchange-core` (the core is Android-free), bound by `ExchangeModule` (phone) and `WearExchangeModule` (watch); the unit tests that build the client by hand keep a default. Regression test: `hello carries the product version bound to the client and no literal`.
- **Fixed here - `tools/contracts/check-sync.ps1` stopped at its first failure**, hiding every later block behind one unrelated finding. It now runs every block, prints every finding, and exits 1 when there is any.
- **Fixed here - journal paths.** Ten specs that live in `PLAN/DONE/` (S0008, S0009, S0010, S0012, S0016-S0019, S0023 and this one) were recorded in the journal under `PLAN/`; `spec_catalog/update -File` repointed them. `spec_catalog/validate`: 9 OK, 0 WARN, 0 FAIL (it was 1 FAIL for S0023 and a warning for the others).
- **Filed as S0041:** `REPO-STAMP` and `HARNESS-PROFILE` 0.12 to 0.13 (the four `check-sync.ps1` findings), the `LIVE-BROADCAST`, `STREAM-BANK` and `RULE-DELIVERY` rows that `contract-lag.ps1` still cannot read, and the two rows never verified.

## Research items

- **Open:** item DT against canon invariant 11 - the per-broadcast `publishToExchange` opt-in (off by default) or a
  broadcast that reaches the account's server by itself with no switch. Owner decision; if the opt-in stays, a
  proposal beside the contract (the catalog moves first). Carrier: S0021
- **Open:** item AJ - draw a locked row for a protected resource in the directory list, or ask the catalog whether a
  product with neither resource role owes only the aggregate notice. Carrier: S0028
- **Open:** the canon session applies `registry-sync.patch` (DoD items 2 and 4 are met in the catalog only then).
  Carrier: S0034

## Acceptance

- [x] DoD 1 - `DEVICE-EXCHANGE` read at 1.7 and cited by id, section and item (item map above; the pointer).
- [ ] DoD 2 - each gap is a dated section 3 exception: written (five rows, `until` 2026-12-31) and proposed; they stand
  in the catalog once the canon session applies the patch.
- [x] DoD 3 - the product's suite runs the catalog's vectors (`connection-string-v1`, `fmsw-224/connection-string-v1`,
  `records-v1`, `protection-v1`); commands and exit codes are in "Fresh verification". The negative-access run and the
  server corpora are said not to exist yet, not claimed.
- [ ] DoD 4 - the own row carries `Implements`, `Reads`, `Verified` and a note in the section 2 format: written in the
  patch (`contract-lag.ps1` reads it as `partial 1.7`); in the catalog once the patch is applied.
- [x] DoD 5 - `docs/contracts/DEVICE-EXCHANGE.md` names 1.7.
- [x] DoD 6 - no other product's row and no contract document was edited; the catalog was not edited at all.

## Last Audit

**Date:** 2026-10-25 | **Mode:** contract read against code, suites and vendored vectors | **Outcome:** Implemented (the repository side is complete; the catalog side waits for the canon session)
**Counts:** PASS 4 | WARN 2 | FAIL 0 | MANUAL 0 | UNCHECKABLE 0 | EXEMPT 0

1. PASS: `DEVICE-EXCHANGE` read at 1.7; every item of the 0.18 and 0.19 folds and of 1.6 and 1.7 is held, not
   applicable or a written exception (item map).
2. PASS: forced rerun of the exchange suites, exit 0 - 495 phone tests per flavor and 17 exchange-core tests, zero
   failures; the wear suites, 266 per flavor, exit 0.
3. PASS: the four vendored vectors are byte-identical to the catalog's; the pointer names 1.7.
4. PASS: the registry patch applies cleanly and turns the row from unparsed into `partial 1.7` for `contract-lag.ps1`.
5. WARN: `tools/contracts/check-sync.ps1` exits 1 on `REPO-STAMP` and `HARNESS-PROFILE` 0.13 (another contract, filed as S0041); every
   finding about this contract passes.
6. WARN: DoD items 2 and 4 are met in the catalog only after the canon session applies the patch; items DS to DU, DV,
   AJ's locked row, the server corpora and section 27 are open debt with carriers (research items above).

## Hand-over

- **Canon session:** `registry-sync.patch` and `registry-sync-reason.txt`; do not apply the S0022 proposal's
  `DEVICE-EXCHANGE` hunk as well.
- **S0021:** items DS to DU and DV (hand-over section in that ticket). **S0028:** the locked row of item AJ and the link
  password with DV (hand-over section there). **S0034:** starts from 1.7, the version recorded in the pointer and the
  patched row; it owns the server corpora and the joint runs.
