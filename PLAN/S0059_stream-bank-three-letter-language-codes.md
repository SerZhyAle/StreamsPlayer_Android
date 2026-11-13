# S0059 - Three-letter language codes are not folded to their name (STREAM-BANK 2.5 item P)

**Ticket:** S0059
**Status:** BlockExternal
**Status note:** 2026-11-12 catalog round: the contract needs no change - STREAM-BANK 2.5 item P.3 stands (clarification Q in stream-catalog/README) and folding on import is optional for a consumer (item O). Waits for the owner's tables: fms-a:S4555 (three-letter lookup in both facet normalizers, P.3 rows in facet-golden.tsv, an export of the tables and vectors into the catalog); then the same fix is carried into this product's copy
**Complexity:** light - one lookup branch in the facet fold on phone and Wear, golden rows added
**Date:** 2026-10-29

## Captured request and migration scope

Parked from S0057 (contract sync to the catalog of 2026-10-29).

Symptom: a three-letter language code in a catalog cell is not folded to its language name. STREAM-BANK 2.5 item P (clarification of item O): a three-letter language code folds to the named language and the canonical entry is the name; a multi-valued `country` cell is blank. `StreamFacetFolding.resolvePiece` looks a piece up by name or alias, or by code only when the key has two letters (`languageNamesByCode`); the three-letter extension table (`ISO_639_3_EXTENSION_TABLE`, e.g. `haw hawaiian`) is consulted by name only. So `foldLanguages("haw")` falls through to the word search and the fallback `english` instead of `hawaiian`, and `rus` folds to `english` instead of `russian`: the alias table carries no three-letter code, so only `eng` lands on the right language, by way of the fallback.

Evidence (static reading, 2026-10-29): `phone/src/main/java/com/sza/streamsplayer/domain/streams/facets/StreamFacetFolding.kt` (resolvePiece, lines 46-56) and `StreamFacetVocabulary.kt` (extensionNamesByCode is private and keyed by code, languageLookup holds names and aliases). The multi-valued country half of item P already holds: `foldCountry("US,GB")` finds no code and returns blank. The same files exist in `wear/`. The catalog's vectors for item O/P are owed by the contract owner and not published yet; until then the publisher's normalizer in FastMediaSorter is the authority.

Needed: first run the port-ledger drift check for the facets files (the fix may already be a FastMediaSorter change to carry across, STREAM-BANK law of CLAUDE.md section 2), then port it to phone and wear with the golden vector rows for three-letter codes, and re-verify the STREAM-BANK registry row.



## Queue pass 2026-10-30 - research (static, no build)

- Port-ledger drift check (`tools/port-ledger/check-drift.ps1`, output in `temp/S0059/drift.txt`): no facet row is
  uncarried. `StreamFacetFolding.kt` and `StreamFacetVocabulary.kt` hash to the ledger values
  (`39ddd09d..`, `c1b3e002..`) in FastMediaSorter, so the publisher side has not changed the fold since the port.
  The check reports 142 of 798 rows uncarried, none in the facet files.
- FastMediaSorter `StreamFacetTables.kt` has no three-letter table beyond the ISO 639-1 list and the 14 named
  ISO 639-3 extensions. The bibliographic and terminological forms (`rus`/`ger`, `deu`/`deu`, `zho`/`chi`) have no
  row there, so `rus` cannot fold to `russian` from any published table.
- `StreamFacetFoldingTest` holds `the tables are the same text as the publisher module`: a local-only three-letter
  table would fail it, so the fix cannot be made in this repository alone.
- Catalog: STREAM-BANK 2.5 item P (`stream-catalog/README.md`, row 2026-10-29) states the rule. The vocabulary,
  assigned-code list, alias table and vectors for items O and P are catalog artifacts the contract owner has not
  published (`PROPOSAL-2026-10-20-language-country-vocabulary-and-vectors.md`: "the publication itself is owed").
