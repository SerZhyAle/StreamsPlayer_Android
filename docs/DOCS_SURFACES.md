# DOCS_SURFACES - the ship-together manifest

Every surface a user of StreamsPlayer for Android reads, with its locales, its one source of truth and the tool that
renders it. A user-facing change lands in every row it touches, every locale of that row, in one edit (canon
invariant 17, the `feature-to-site` skill). A rendered surface is regenerated from its source, never hand-edited.
Written 2026-11-08 (S0045 P8); no surface list existed before it.

Locale codes are ISO 639-1 everywhere (`uk` for Ukrainian); `ua` is only the language-switch key of the site pages.

## Surfaces

| Surface | Source of truth | Rendered to (never hand-edited) | Locales | State |
| --- | --- | --- | --- | --- |
| Phone app strings | `phone/src/main/res/values/strings.xml` and `values-ru/`, `values-uk/` | the phone packages | en, ru, uk | live; one set for both variants (`store`, `noLegal` carry no strings of their own) |
| Wear OS app strings | `wear/src/main/res/values/strings.xml` and `values-ru/`, `values-uk/` | the Wear OS packages | en, ru, uk | live; one set for both variants |
| Watch face strings | `watchface/src/main/res/values/strings.xml` and `values-ru/`, `values-uk/` | the watch face package | en, ru, uk | live; `watchface/src/debug/res/` is debug-only and ships in no release |
| In-app help pages | `docs/help/content/` (one JSON per help id, en, ru and uk in each); the glossary of the index page from `docs/TERMBASE.json` | `phone/src/main/assets/help/{en,ru,uk}/` and `img/` by `tools/help/generate-help-pages.ps1` and `capture-help-screenshots.ps1` | en, ru, uk; other interface languages open the English pages | live; the watch's **Help** row opens the phone page about the watch app; the index page carries the subject index and the terms, rendered from those sources |
| Help map | `phone/src/main/assets/help/help-map.json` | - (read by the app and the gate) | language-neutral | live; gate `tools/help/assert-help-map.ps1`, accepted debt `tools/help/help-map-baseline.txt` |
| README | `README.md` | - | en | live; English only, the site landing carries the ru and uk text (`feature-to-site` step 5) |
| Ledger | `CHANGELOG.md`, section `[Unreleased]` | - | en | live; the one ledger of this repository, read by hand at release |
| Privacy policy | `docs/PRIVACY_POLICY.md`, `-ru.md`, `-uk.md`; the permissions table inside them from the merged store release manifests and `docs/privacy/permission-purposes.json` by `tools/privacy/permissions.ps1` | `site/privacy.html`, `privacy-ru.html`, `privacy-uk.html` by `tools/site/build-site.ps1` | en, ru, uk | live |
| Site index files | `tools/site/build-site.ps1` | `site/sitemap.xml`, `site/robots.txt` | language-neutral | live; list the privacy pages |
| Site landing | the landing copy deck (S0045 P1) and `tools/site/build-site.ps1` | `site/index.html`, `index-ru.html`, `index-uk.html` | en, ru, uk | written by S0045 P2; enters `site/` in the commit after the Play listing answers 200 (S0045 P9) |
| Help on the site | `docs/help/content/`, the same help generator as the bundled copy | `site/help/{en,ru,uk}/` | en, ru, uk | written by S0045 P5; published with the first release, addresses permanent (`CONTEXT-HELP` rule 15); each page carries `noindex`, stays out of `sitemap.xml` and is not blocked by `robots.txt` (`DOC-EXTERNAL-QUALITY` item O) |
| Social card | the application icon on the family's plain background | the 1200x630 Open Graph image; the 1280x640 repository preview is cut from it | language-neutral | written by S0045 P6 |
| Play Data safety answers | `docs/play/PLAY_DATA_SAFETY.md` | the Play Console Data safety form, filled by the owner | en (the Console form) | live; says what the privacy policy says |
| Play listing texts and graphics | `docs/play/PLAY_LISTING.md`, `-ru.md`, `-uk.md` (title, short and full description), written from `POSITIONING.md`; screenshots and the feature graphic come with the demo (S0014 P6) | the Play Console listing, submitted at release | en, ru, uk | text live (S0014 P3); gate `tools/promotion/assert-promotion.ps1` holds the limits, the pillar order and the forbidden terms |
| Positioning source | `POSITIONING.md` (the sentence, the pillars, the worksheet, the topics, the search terms) | every surface of this manifest that states what the product is | en, with the sentence and the pillar terms in ru and uk | live (S0014 P1) |
| Media exchange portal sections | `docs/PORTAL_SECTIONS_STREAMSPLAYER.md` | the shared portal, applied by FastMediaSorter Android in its own source | en, ru, uk | live; never edited in the portal from here |
| Portal links from the app | `docs/MEDIA_EXCHANGE_PORTAL.md` (the readable list of the held addresses) | the in-app links of the exchange screens | en, ru, uk pages, English fallback | live |
| Third-party notices | `tools/third-party-notices/` | `THIRD-PARTY-NOTICES.txt` inside every package | en | live; regenerated when a bundled component changes, carries no feature text |
| GitHub repository metadata | `POSITIONING.md` section 5 (the description is its sentence, the topics its `Topics:` line) | description, homepage, topics, social preview of the repository | en | written by S0046 with S0014 P4 once the repository exists |
| Hub card, family map row, sibling footers and documentation | the catalog's `SITE-FAMILY-MAP` and the other repositories' own sources | sza.od.ua and the sibling products' pages | each site's own locales | not edited from this repository; requested and checked from outside by S0048 |

## Checks

| Check | Command | When |
| --- | --- | --- |
| Help map, pages, links, pictures | `pwsh -NoProfile -File tools/help/assert-help-map.ps1` | after any help change; the pre-release sweep |
| Site pages equal their sources, no request outside the host, published help pages out of the sitemap with `noindex` | `pwsh -NoProfile -File tools/site/build-site.ps1 -Check` | before the privacy URL is given to the store; the pre-release sweep |
| Permissions table equals the merged manifests | `pwsh -NoProfile -File tools/privacy/permissions.ps1 -Build` | release |
| Every surface above that lives in `docs/` or the help: the registry, links, the house style, stale translations, forbidden words, help pages equal their sources | `pwsh -NoProfile -File tools/docs/assert-docs.ps1` ([tools/docs/README.md](../tools/docs/README.md) names each gate) | the pre-release sweep and CI; each gate alone after an edit in its area |
| Promotion sources: no other product's name and no piracy word in a published source or a discoverability field, the listing limits and pillar order, the topics, the one contact | `pwsh -NoProfile -File tools/promotion/assert-promotion.ps1` ([tools/promotion/README.md](../tools/promotion/README.md)) | the pre-release sweep; after any edit of a README, a string resource, the listing or the positioning source |
| Tense and promise words | the `feature-to-site` tense grep over the touched files | every edit of a surface above |
