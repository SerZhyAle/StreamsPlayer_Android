# docs - index

Every document here has a record in [DOCUMENT_REGISTRY.jsonl](DOCUMENT_REGISTRY.jsonl), with its product area, its update triggers and
its role (`source`, or `render` for what a tool regenerates); `tools/docs/assert-doc-registry.ps1` holds the two in step and fails a
document this index does not mention ([tools/docs/README.md](../tools/docs/README.md)).

## Product and process

- [BOOTSTRAP_HANDOFF.md](BOOTSTRAP_HANDOFF.md) - the starting brief of 2026-10-02: product definition, owner
  decisions, extraction source map, section 6a (staying in step with FastMediaSorter). Written and amended on
  2026-10-02 by the FastMediaSorter session of its ticket S4053 (the amendment on the owner's instruction), before
  this repository's first commit; not edited from this repository's sessions, frozen in the registry. Its open items now live as
  tickets in `PLAN/`.
- [contracts/](contracts/README.md) - one pointer per shared contract this product produces, consumes or adopts.
- [DOCS_SURFACES.md](DOCS_SURFACES.md) - S0045 the ship-together manifest: every user-facing surface, its locales, its one
  source of truth and the tool that renders it.
- [design/DOC_QUALITY_VARIANTS.md](design/DOC_QUALITY_VARIANTS.md) - S0051 the permanent variants of the documentation contracts; the
  registry rows point at its headings. [design/UI_CONTRACT_CHECKS.md](design/UI_CONTRACT_CHECKS.md) and
  [design/UI_SIZE_PROFILE.md](design/UI_SIZE_PROFILE.md) - S0036 the unified UI checks and the size profile.
- [port-ledger.jsonl](port-ledger.jsonl) - S0002 the port ledger: source path in FastMediaSorter, source SHA-256, destination, date
  ([tools/port-ledger/README.md](../tools/port-ledger/README.md)).
- [audit/findings.jsonl](audit/findings.jsonl) - S0025 the findings ledger of the dynamic audit, one finding per line:
  scene, property, the measured number, rank, budget, reproduction command and run folder - numbers and references only;
  the run folders stay in `temp/S0025/` ([tools/audit/README.md](../tools/audit/README.md)).
- [S0007_WEAR_VERIFICATION.md](S0007_WEAR_VERIFICATION.md) - S0007 the Wear OS verification record.
- [PHONE_SHELL_ICON_MAP.md](PHONE_SHELL_ICON_MAP.md) - S0004 catalog and radio glyph meanings, first-screen conformance and measured theme/state corrections.
- [DRIVE_OAUTH_CLIENT.md](DRIVE_OAUTH_CLIENT.md) - S0029 owner steps: registering the Android OAuth client for the Google Drive announcements in the family Cloud project; no secret is written there.

## What a user reads

- [help/content/](help/content/) - S0035 the sources of the in-app help pages, one JSON per help id in EN/RU/UK; `tools/help/generate-help-pages.ps1` writes the pages bundled in the phone app from them ([tools/help/README.md](../tools/help/README.md)).
- [TERMBASE.json](TERMBASE.json) - S0051 the term base: the glossary entries of the help index and the words the prose may not use;
  `tools/docs/assert-termbase.ps1` holds the help sources, the policies and the portal sections to it.
- [l10n-fingerprints.jsonl](l10n-fingerprints.jsonl) - S0051 the English block each Russian and Ukrainian block was rebuilt from;
  `tools/docs/assert-translation-freshness.ps1` flags a translation whose English block moved.
- [PRIVACY_POLICY.md](PRIVACY_POLICY.md), [-ru](PRIVACY_POLICY-ru.md), [-uk](PRIVACY_POLICY-uk.md) - S0033 the privacy
  policy in EN/RU/UK, the source of the hosted pages under `site/`. Its permissions table is generated from the merged
  store release manifests by `tools/privacy/permissions.ps1` (sentences in `privacy/permission-purposes.json`); the
  pages are rendered by `tools/site/build-site.ps1`, never hand-edited.
- [play/PLAY_DATA_SAFETY.md](play/PLAY_DATA_SAFETY.md) - S0033 the Play Console Data safety answers, flow by flow, and the
  account-deletion answer; the source the form is filled from, kept beside the listing source of S0014.
- [play/PLAY_LISTING.md](play/PLAY_LISTING.md), [-ru](play/PLAY_LISTING-ru.md), [-uk](play/PLAY_LISTING-uk.md) - S0014 the Play
  listing source: title, short description and full description in EN/RU/UK, written from the positioning source
  [POSITIONING.md](../POSITIONING.md); the Play Console fields are filled from it and checked against it.
- [MEDIA_EXCHANGE_PORTAL.md](MEDIA_EXCHANGE_PORTAL.md) - shared media exchange and broadcasting portal,
  page links in EN/RU/UK and the phone app entry point.
- [PORTAL_SECTIONS_STREAMSPLAYER.md](PORTAL_SECTIONS_STREAMSPLAYER.md) - S0030 this product's sections of that portal in EN/RU/UK, handed to
  its publisher, never edited in the portal from here.
- [assets/exchange-mark/](assets/exchange-mark/) - S0039 the home of the exchange server's mark in the documents: files
  of the `EXCHANGE-MARK` artwork copied byte for byte under their catalog names (`mark-24.png`), never redrawn or
  re-exported, each shown at its own pixel size.

Tickets and the release package plan live in `PLAN/` (overlay B spec home), not under `docs/`.
