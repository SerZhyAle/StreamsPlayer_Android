# S0037 - recommendations for the catalog owners, 2026-10-27

Filed by the StreamsPlayer Android session of this repository. Each item is a **recommendation**, not a waiver:
the local deviation, where there is one, is already an exception in the registry proposal
(`registry-sync.patch`). The catalog is not edited from this repository; the canon session
`sza-unified-rules-dc` places these as `PROPOSAL-<date>-<topic>.md` beside the contract, or answers them in the
registry text. Evidence is in `audit.md`.

| # | Contract | Suggested wording or change | Evidence | Compatibility consequence |
| --- | --- | --- | --- | --- |
| R1 | `REGISTRY` section 2 | State how a not-applicable contract spells `Verified`: `Implements` and `Reads` as `-` and the date of the declaration, with the note "not applicable, declared". The catalog already does this for the sza.od.ua hub rows of 2026-10-21. | S0041 research item; `MEDIA-CLASSIFICATION` row of this product | none; makes the `pending` of a settled scope go away |
| R2 | `SETTINGS-NAVIGATION` section 1-2 | Add an Android and Wear OS paragraph: a settings surface that is a panel inside another screen (here the broadcast settings inside Broadcast Control) becomes a destination of the product's settings page; on a phone in portrait the rail is a reachable menu (section 2 rule 6); on Wear a single-column list of pages satisfies the rail. | S0036 permanent variant V2; `fragment_settings_broadcast.xml` | clarification only |
| R3 | `DOC-INTERNAL-QUALITY` section 2 and the canon checker | Define "maintained technical corpus", and say how generated files (`site/*.html`, bundled help pages) take the `render` role: the checker in plugin 2026.1026.x accepts only the documents map as `render`. | audit section 8 | additive |
| R4 | `INPUT-PARITY` section 4 | Add a rotary-crown column for Wear OS (scroll, volume, confirm) or say the crown is system-owned input outside the table. | `RotarySeek.kt`, `WearListColumn.kt:339-350`; the volume action is declared and never called | additive |
| R5 | `DEVICE-EXCHANGE` item CZ | The one table of messages is split into three tables (CZ, DD, DR) in the README; a consumer that vendors "the table" gets the first one only. Publish a generated machine-readable messages file (code, kind, step, three texts) from the owner, or say which tables a consumer vendors. | `tools/contracts/check-sync.ps1` cut; `messages-item-cz.md` drift | additive |
| R6 | `DEVICE-EXCHANGE` item DW (1.8) | Owner question, not a change: should a phone or watch producer prepare a broadcast before its start? It is a MAY here; implementing it needs the pending link UX. | audit section 6 | none |
| R7 | `LIVE-BROADCAST` section 3 and amendment 13 | Write the receiver defaults of a missing optional member: the `targetLatencyMs` of an RTSP and an HTTP audio endpoint, the `transport` and `mode` of an endpoint that omits them, and what a consumer that does not use `keyframeIntervalMs` does. | audit section 9, answer 7 | additive |
| R8 | registry hygiene | This product has three registry proposals waiting for the canon session that touch the same row: S0026 (`DEVICE-EXCHANGE`, applies cleanly), S0034 (same row and an exception, does not apply: line endings) and this one. Please apply S0026's, then this one; S0034's only extra is the closing of "Exception 4" (server corpora replay). | `registry-sync-reason.txt` | none |
