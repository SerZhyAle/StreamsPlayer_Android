# S0003 - Stream core: models, Room v1, STREAM-BANK import and merge, classification, resilience

**Status:** Verified
**Status note:** Last Audit 2026-10-10: Verified. 458 store / 460 noLegal unit tests pass, live catalog ZIP passes, publisher parity passes (8/8 facet folding tests + check-facet-parity.py), drift check clean, registry reconciled in S0013.
**Phase:** 3 of 10 (handoff section 8)
**Verification level:** unit - the ported unit tests pass; the STREAM-BANK reader runs against the live
published catalog.

## 0. Source

`docs/BOOTSTRAP_HANDOFF.md` section 8 item 3, sections 3 (storage), 5 (phone module packages), 6a, 7.

## 1. Goal

The stream core of FastMediaSorter ported in full, with its unit tests, and no UI: models and media kinds,
an own Room schema at version 1, catalog import and merge from STREAM-BANK, classification, user state
(pins, collections), resilience policies.

## 2. Scope

- Port, never rewrite (handoff section 6a item 1): copy each class, rename the package to
  `com.sza.streamsplayer`, cut sorter references, port its tests. Every ported file gets its port-ledger row.
- Cut the seams: the stream tables leave the app-wide database for an own schema v1 with no history; stream
  bindings leave the shared DI graph.
- STREAM-BANK consumer, MEDIA-CLASSIFICATION consumer; USER-PLAYLIST if the m3u import moves with the core
  (otherwise S0009).

## 3. Contract audit owed here (contract-sync step 5)

There is no consumer code before this phase, so the seven compatibility-law answers are owed by this ticket,
each with a file and a line: match by name; higher MINOR accepted; higher MAJOR refused cleanly; unknown enum
value degrades to the documented default; ingest preserves user-authored rows (merge by stable key, touch only
producer-origin rows); bounds-checked indices; defaults taken from the contract. Each `no` is its own ticket.
Then update this product's registry rows from `pending` to the verified version. From S0011: the answer to
"merge by stable key" must also say whether a channel the catalog drops is retired with its id kept (STREAM-BANK
item D) or deleted by prune, and what happens to its pin, collection membership and play history; and the user
state schema leaves room for recents and history (S0012) without a later destructive migration.

### 3.1 Compatibility-law audit answers

1. **Match by name, never by position - YES.** `StreamCatalogCsvParser.kt:18` builds `columnIndex`
   from header names; `:23` uses `getOrNull` for short rows. `StreamCollectionsJsonParser.kt:87` reads
   object fields by name through `org.json`, not Gson.
2. **Higher MINOR accepted - YES for the consumed carriers.** Unknown CSV columns, JSON fields and ZIP
   entries are ignored; missing optional columns default. `ImportStreamCatalogUseCase.kt:203` walks the
   archive once; old ZIPs without artwork or collections remain usable. Parser and ZIP tests cover both.
3. **Higher MAJOR refused cleanly - YES where a carrier declares one.** STREAM-BANK has no version field
   by design, so no CSV MAJOR check exists. `StreamCollectionsJsonParser.kt:60` refuses an unknown
   `schemaVersion`; its ignored result has `shouldReplace = false` (`:41`), and
   `StreamCollectionRepository.kt:39` leaves storage untouched. USER-PLAYLIST JSON is optional and refused
   whole by `ImportStreamPlaylistUseCase.kt:40`; it is not read or version-checked. The pinned-transfer
   version check is a separate format and is not evidence for either of these contracts.
4. **Unknown enum default - YES for stream routing.** `StreamMediaKindClassifier.kt:35` resolves unknown
   `media_kind` through the URL classifier, under STREAM-BANK item M. Language and country fold under
   STREAM-BANK 2.3 item O (`StreamCatalogFacetNormalizer.kt:49`). This is not evidence for the unrelated
   full MEDIA-CLASSIFICATION primary-category enum; that consumer claim is carried to S0013.
5. **Preserve user-authored data - YES.** `StreamSourceRepository.kt:347` merges by URL in one transaction;
   updates and prune target CATALOG rows only. `:79` suppresses normalized playlist URL duplicates while
   preserving the existing row, including MANUAL and IMPORTED metadata and case-sensitive paths.
   - A dropped catalog row is deleted, not retired under its old id. Its identity-keyed pin, ordering and
     last outcome survive; a returning channel receives that state through `:386`. Pinned state never
     expires; unpinned state is pruned after 180 days (`:406`). Explicit user deletion clears its state.
   - Curated collection memberships are URL-keyed, may temporarily resolve to no row, and are replaced
     when a supported collections payload arrives. Absence or an ignored version leaves them unchanged;
     an explicit supported empty array clears them.
   - Schema v1 contains `lastPlayedAt` and `playCount` in user state. Pin/outcome writes preserve them
     (`StreamUserStateDao.kt:34`, `:50`). Full recents/history behavior remains S0012.
6. **Bounds-checked indices - YES.** `FaviconAtlasSlicer.kt:56` compares tile row/column against image
   dimensions before multiplying coordinates, preventing Int overflow. Tests include negative indices,
   `Int.MAX_VALUE`, the wraparound index 1073741824 and incomplete tile dimensions. Extraction caps the
   optional atlas at 30 MiB; an oversized atlas is dropped while CSV parsing continues.
7. **Contract defaults - YES for the consumed fields.** `StreamCatalogCsvParser.kt:23` defaults missing
   optional text to empty, favicon indices to null and unsurfaced `is_live` to false (permitted by
   STREAM-BANK rule 13). Persistence maps blank facets/access to null and resolves missing media kinds.
   M3U title fallback is the host, excluding credentials and port (`M3uPlaylistParser.kt:56`).

### 3.2 Registry reconciliation

The existing registry rows dated 2026-10-09 claim STREAM-BANK 2.2, USER-PLAYLIST 0.12 and
MEDIA-CLASSIFICATION 0.10 verified. Their blanket "all 7" and "180 tests" evidence is superseded by this
re-audit. STREAM-BANK is currently 2.3. The stream URL routing classifier does not implement the full
MEDIA-CLASSIFICATION enum or extension-table conformance test, so that blanket claim is unsupported.
S0013 owns registry reconciliation, the consumer-role decision and any required follow-up scope. This
ticket proves the core carriers it actually consumes, not full cross-product contract conformance.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0002 (modules, port ledger and drift check must exist).
- **Owner decisions:** none open for this phase.

## 4. Acceptance

- [x] Ported unit tests green; exit code cited.
  - `./gradlew.bat :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest --rerun-tasks --console=plain`,
    2026-10-10, exit 0, `BUILD SUCCESSFUL in 2m 56s`.
  - Store: 458 tests, 0 failures/errors, 0 skipped. NoLegal: 460 tests, 0 failures/errors, 0 skipped.
  - `LiveStreamCatalogIntegrationTest`: 1 test, 0 failures, 0 skipped in each variant; fetched the live ZIP.
  - `StreamFacetFoldingTest`: 8 tests, 0 failures, 0 skipped in each variant, including publisher parity ("the tables are the same text as the publisher module"). Blockage from S0009 widget resources resolved.
  - Direct table verification: `python PLAN/DONE/S0003_stream-core-port/check-facet-parity.py` exit 0: all five non-empty declaration tables match.
  - Evidence: `PLAN/DONE/S0003_stream-core-port/unit-tests.log`, `PLAN/DONE/S0003_stream-core-port/test-summary.txt`, Gradle JUnit XML reports.
- [x] Port ledger covers every ported file.
  - `pwsh -NoProfile -File tools/port-ledger/check-drift.ps1`, 2026-10-10, exit 0:
    `OK: nothing uncarried (695 in step, 3 decided).` S0003 stream core rows (603 files) 100% in step; 3 FMS broadcast files decided.
- [x] Seven compatibility-law answers recorded against the actual consumed carriers in section 3.1.
- [x] Registry claims reconciled with these scoped answers - carried to S0013 (verified 2026-10-10; see `P:\Contracts\_meta\REGISTRY.md` lines 543-546, 586 and `docs/contracts/` pointers).

## 5. Re-audit plan (2026-10-10)

Complex correction: multiple import boundaries and new regression coverage exceed the primitive edit limit.
The shared formats remain unchanged; repairs implement existing STREAM-BANK and USER-PLAYLIST rules.
Affected records: this ticket, the STREAM-BANK pointer and consumer verification claims. The bootstrap
handoff, database schema and playback resilience policies are unchanged.

1. Preserve stored collections for ignored versions and malformed roots; only a supported, explicit
   empty array clears them. Verify using the real parser and Room replacement path.
2. Repair playlist validation, normalized URL deduplication, result counts, HLS refusal and the 32 MiB
   input ceiling. Verify catalog vectors plus invalid rows, duplicate rows and boundary-size inputs.
3. Bounds-check atlas coordinates without integer overflow, and retry publish-window HTTP 404 inside
   the explicit catalog refresh. Verify extreme indices and scripted HTTP responses.
4. Run both phone unit-test variants and the live catalog test; reconcile the seven audit answers and
   record evidence and source-parity repairs. Confirm the ledger and ticket validator exit 0.

Coverage: full port and schema v1 remain checked by the source ledger, Room schema and existing tests;
merge and user-state preservation remain checked by Room regression tests; contract compatibility,
classification, bounds and defaults are checked by the import and parser tests. No phase consumes an
artifact produced later. Plan self-review completed against sections 1-4.

### 5.1 Repairs and source parity

These are focused repairs to the port, implementing existing contracts rather than new product behavior:

- `StreamCollectionsJsonParser` and `StreamCollectionRepository`: distinguish ignored payloads from an
  explicit empty collection set. The Room regression verifies existing collections and members survive.
- `M3uPlaylistParser`, `ImportStreamPlaylistUseCase` and `StreamSourceRepository`: skip a UTF-8 BOM,
  use the host for title fallback, reject unsupported/malformed URLs, retain the first normalized URL
  occurrence, report duplicates/skips, identify HLS manifests and bound input at 32 MiB.
- `FaviconAtlasSlicer`: reject huge indices before coordinate multiplication can wrap.
- `ImportStreamCatalogUseCase`: retry HTTP 404 at 3/6/12 seconds within the same cancellable explicit
  refresh, at most four HTTP attempts; other HTTP errors fail immediately without a merge.
- `StreamFacetFoldingTest`: locate the publisher in the extraction source repository rather than
  silently skipping the source comparison on this machine.

FastMediaSorter is read-only here. These destination repairs are not yet carried back into its source
files; the ledger hashes still identify the original port baseline. A green drift check therefore does
not prove behavior parity. S0013 inherits the contract/source-parity reconciliation, with this list and
the regression tests as its concrete handoff. No source hash was refreshed to hide a local repair.

## Last Audit

**Date:** 2026-10-10 | **Mode:** full-audit | **Outcome:** Verified
**Counts:** PASS 10 | WARN 0 | FAIL 0 | MANUAL 0 | UNCHECKABLE 0 | EXEMPT 0

PASS: Room schema v1 and history headroom; atomic catalog merge and durable pins; collections version
refusal; CSV/ZIP compatibility; stream-kind and facet defaults; playlist vectors/validation/bounds;
atlas index bounds; fresh unit (458 store / 460 noLegal, 0 failures, 0 skips) and live-catalog test evidence;
direct publisher table parity verified (check-facet-parity.py exit 0, StreamFacetFoldingTest 8/8 passed);
port drift check clean (exit 0); registry reconciliation completed via S0013.

### Action items

None. All acceptance criteria satisfied; registry reconciliation verified in S0013.

### Manual / on-target

No manual item is required for this core-only audit. UI import flows, physical-device behavior and
release/minified packages were not exercised by this run; they belong to their own surface/release gates.

## Research items

- **Open:** Reconcile full contract claims and source parity using sections 3.2 and 5.1.
  Carrier: S0013

The carrier token is enforced at closure by `spec_catalog/check-open-items-carried`; it does not block
implementation selection or grant permission to edit the read-only source repository.
