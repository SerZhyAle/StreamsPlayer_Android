# STREAM-BANK - pointer

| Field | Value |
| --- | --- |
| **Contracts reviewed** | `STREAM-BANK` 2.5 |
| **Reviewed** | 2026-10-29, S0057 (earlier: 2026-10-27, S0037; 2026-10-19, S0013/S0026) |
| **Home** | shared contracts catalog, `stream-catalog/` |
| **Role here** | consumer on phone and Wear |

Sections 2-4 and the 2026-10-17 amendment apply. S0013 carries the source-owned General topic fold and generated topic vectors to phone and Wear; blank remains blank and aliases remain canonical. Core import/storage/bounds tests remain scoped evidence. Artwork-manifest/on-demand delivery (rules 9/12) is an explicit exception until 2026-12-31, carried by S0050 (filed by S0037). The manifest reader, the delivery and the render order are in the tree on phone and watch since 2026-11-08 (phone: the source's client, offered after a catalog update; watch: fetched by the watch itself on its catalog triggers, nothing over the Data Layer), not built or run until the release, and the exception stands until the producer's vectors and that run are in. Source parity review is release-time work. Full artwork, physical-device and release conformance is not claimed. 2.5 (2026-10-29, item P, read in S0057) states that a three-letter language code folds to the named language and a multi-valued `country` cell is blank. The country half holds (`StreamFacetFolding.foldCountry` finds no code in a list); the language half does not: only two-letter codes are looked up by code, so `rus` folds to `english` (S0059). The catalog's vectors and vocabulary for items O and P are owed by the contract owner and are not published, so there was nothing to replay.
