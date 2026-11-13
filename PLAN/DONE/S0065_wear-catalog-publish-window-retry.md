# S0065 - Watch catalog import fails on a publish-window 404 (STREAM-BANK rule 11)

**Ticket:** S0065
**Status:** Implemented
**Status note:** Implemented 2026-11-08 (queue pass pq2, static): wear and phone catalog imports carry the publish-window 404 retry (StreamBankFetchRetry) and the SB-A7 refusal, with both collections parsers and their tests; ledger refreshed for five rows, phone import decided for S4459 (parked as S0068). Not built, not tested until the release - RELEASE: phone and wear unit suites named in the ticket, check-drift, pre-release-sweep
**Complexity:** medium - a drift carry of two catalog imports, their two collections parsers and tests, on the watch and the phone; one FastMediaSorter feature (S0068) is parked by decision, not carried here
**Date:** 2026-11-08

## Captured request and migration scope

Parked from S0050 (unattended pass, 2026-11-08). Symptom: the watch's catalog import turns a 404 during a publish into a failed refresh. STREAM-BANK rule 11 says a 404 window during publish is expected and retried, cancellably.

Evidence: `wear/src/main/java/com/sza/streamsplayer/wear/domain/usecase/ImportWearStreamCatalogUseCase.kt`, `downloadCatalog`: `if (!response.isSuccessful) error("HTTP ${response.code}")`, no retry. FastMediaSorter's watch import retries through `StreamBankFetchRetry` (its `ImportWearStreamCatalogUseCase.kt` line 142), so this is a drift row, listed as DRIFT by `tools/port-ledger/check-drift.ps1`. S0050 ported `StreamBankFetchRetry` into the watch module for the artwork download, so the helper is already there. The phone import keeps its own older inline retry (three waits, 3, 6 and 12 s) against the source's four attempts ten seconds apart, also a drift row.

Needed: carry the source's import on both modules (ledger `-Refresh`), with its tests.

## Spec pass (2026-11-08, unattended queue pass pq2)

### Problem

The scope above names one import per module. Reading the source at its current hash shows that the drift is wider: each import file changed together with the collections parser it calls, and the phone file also carries a stream feature (the installed-archive stamp, FastMediaSorter S4459). Behaviour the contract requires is in the same change: the publish-window HTTP 404 retry is cancellable, and a refused collections entry keeps the stored set (STREAM-BANK 2.4, amendment 2026-10-17: "make publish-window HTTP-404 retry cancellable"; SB-A7 for the refusal).

### Drift rows (sha256 read on 2026-11-08, ledger value in brackets)

| Source (FastMediaSorter) | Destination | Disposition |
| --- | --- | --- |
| `wear/.../ImportWearStreamCatalogUseCase.kt` (6cccb3) now 4c786f | wear import | carried in full: retry and refusal |
| `wear/.../WearStreamCollectionsJsonParser.kt` (9c724f) now 93ff8a | wear parser | carried: `ParsedCollections` with `refused` |
| `wear/src/test/.../WearStreamCollectionsJsonParserTest.kt` (7925d7) now 223308 | wear parser test | carried |
| `app_v2/.../data/repository/StreamCollectionsJsonParser.kt` (aff855) now ea114b | phone parser | carried: `refused` replaces `shouldReplace` |
| `app_v2/src/test/.../data/repository/StreamCollectionsJsonParserTest.kt` (0b595c) now 94caa4 | phone parser test | carried |
| `app_v2/.../data/repository/StreamCollectionRepository.kt` (b3335c) | phone repository | in step with source; the local `shouldReplace` guard is removed so the file matches it |
| `app_v2/.../domain/usecase/streams/ImportStreamCatalogUseCase.kt` (879d70) now ce580d | phone import | partly carried: retry and refusal carried; the S4459 stamp part is decided here and parked as S0068 |
| `wear/.../ImportWearStreamCatalogMergeTest.kt`, both `StreamBankFetchRetry.kt` and its test | - | in step, no change |

### Contract boundary

Yes, the change crosses the shared stream-bank behaviour. The text already exists in the catalog (STREAM-BANK 2.4, amendment 2026-10-17): no catalog amendment is needed before this code. The contract is cited by id only.

### Research closed

- Source behaviour read for both imports: the retry is `StreamBankFetchRetry.fetch`, four attempts, ten seconds apart, cancellable; the refusal branch returns before `saveAll` or `replaceAll` on a refused entry.
- `StreamBankFetchRetry` exists in both modules with the ledger hash; no change to it.
- The S4459 classes (`StreamsSessionStore`, `StreamCatalogPublication`, `ResolveStreamCatalogOfferUseCase`) are not in this tree and are not in the ledger. Carrying them is a feature with a banner placement; it is parked as S0068 with its owner questions.
- Callers of the changed parsers: the wear import and the phone import only. `shouldReplace` is referenced only by the phone parser and its repository, which this pass aligns.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0050 (artwork delivery, which ported `StreamBankFetchRetry` into the watch); S0068 (parked: the FastMediaSorter S4459 stamp and the Streams banner, not carried here); S0003 (stream core port, the source of the phone import).
- **Owner decision - S4459 stamp and banner:** not blocking this carry. The banner placement and the watch offer are asked in S0068, and the phone import row stays decided until then.

## Plan

1. **Wear.** Copy the source import, its parser and the parser test with the package renamed; the source's CATALOG_URL stays as it is (it names the publishing repository). Check: `diff` against the source shows only the package and import lines.
2. **Phone parser and repository.** Copy the source parser and its test with the package renamed. Remove the `shouldReplace` guard from `replaceAll`. Check: `diff` of the repository against the source is empty.
3. **Phone import.** Copy the source import with the package renamed, then remove the S4459 pieces: the `StreamsSessionStore` and `PublishedStreamCatalog` imports and `sessionStore` parameter, the `recordInstalledStamp` call and function, the stamp computed in the fetch, and the `stamp` field of `CatalogPayload`. The resulting diff against the source must show only those pieces. Check: grep finds none of `sessionStore`, `stamp`, `StreamsSessionStore`, `PublishedStreamCatalog` in the file.
4. **Ledger.** `record-port.ps1 -Refresh` for the five carried rows (wear import, wear parser, wear parser test, phone parser, phone parser test). `decide-drift.ps1` for the phone import row, pinned to ce580d, naming S0068. The phone import row is not refreshed, because its destination is not the source version.
5. **Static audit** of the four changed areas, with grep and diff. No Gradle build or test runs between releases (the repository's rule for Android).

## Acceptance

- The wear import retries a 404 through `StreamBankFetchRetry` and returns a failure only after the fourth attempt; a refused collections entry keeps the stored set.
- The phone import does the same; its S4459 part is decided and parked, not silent.
- Both collections parsers return `refused` and the phone repository no longer carries a local guard.
- Ledger: the five rows carry the source hash and `carriedOn`; the phone import row carries a decision pinned to its current source version.

## RELEASE items

Not built, not tested until the release. The release runs:

- `./gradlew :phone:testDebugUnitTest --tests "*StreamCollectionsJsonParserTest" --tests "*ImportStreamCatalogUseCaseTest"`
- `./gradlew :wear:testDebugUnitTest --tests "*WearStreamCollectionsJsonParserTest" --tests "*ImportWearStreamCatalogMergeTest"`
- `pwsh -NoProfile -File tools/port-ledger/check-drift.ps1` at the start of the release: the five carried rows and the decided phone import row must not be listed as uncarried.
- `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` with the Gradle suites above.
- The phone 404 test (`ImportStreamCatalogUseCaseTest`) now waits the source's ten seconds in real time (was three): the time is accepted for the release run and not a fast-tier test.

## Last Audit

**Date:** 26-11-08 16:45 | **Mode:** static | **Outcome:** Partial (static evidence only; build and tests are RELEASE items)
**Counts:** PASS 9 | WARN 0 | FAIL 0 | MANUAL 0 | UNCHECKABLE 0 | EXEMPT 0 | RELEASE 5

### Action items

None open in the tree. The five RELEASE items above are the remaining rungs.

### Evidence (static, this run)

- Copies against the source: the six files (wear import, wear parser, wear parser test, phone parser, phone parser test, phone repository) match the FastMediaSorter source after the package rename; `diff` gives 0 differing lines for each.
- Phone import against the source: `diff` shows only the S4459 pieces (the `StreamsSessionStore` and `PublishedStreamCatalog` imports, the `sessionStore` parameter, the `recordInstalledStamp` call and function, the stamp in the fetch, `CatalogPayload.stamp`) and the header comment now naming four payloads instead of five.
- Leftovers: grep for `shouldReplace|PUBLISH_RETRY_DELAYS_MS|sessionStore|recordInstalledStamp|PublishedStreamCatalog` over `phone/src` and `wear/src` finds nothing. The `REFUSED` constants that remain are the carried ones.
- Dead weight and hygiene: no unused import in the seven touched files, no long dash in them.
- Probe invariant: no `S0065` or `S0068` string in `phone/src` or `wear/src`; the ticket is not in the needs-manual-test status.
- Ledger: `record-port.ps1 -Refresh` exit 0 for the five carried rows (`carriedOn` 2026-11-08); `decide-drift.ps1` exit 0 for the phone import row, pinned to ce580d.
- Contract: STREAM-BANK 2.4 (amendment 2026-10-17) names the publish-window HTTP 404 retry and the collections refusal (`P:\Contracts\stream-catalog\README.md`); the text is already in the catalog, so no amendment is owed before this code.
- Symbols used by the carried code exist: `DeliveryAssets.STREAM_CATALOG_URL`, `StreamBankFetchRetry.fetch` with the trailing `attempt` lambda (both modules), `WearStreamCollectionRepository.saveAll(List)`, `warnUnlessCancellation` (wear), `rethrowIfCancellation` (phone). Hilt constructor of the phone import is unchanged, so no DI change.
- Ship-together: no user-facing text was changed; the feature-to-site surfaces are not touched.

### Manual / on-target

None. The watch's publish-window behaviour is covered by the release run, not by a device step in this ticket.
