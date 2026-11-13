# PORTAL-DOCS - pointer

| Field | Value |
| --- | --- |
| **Contracts reviewed** | `DOC-EXTERNAL-QUALITY` 0.13, `DOC-INTERNAL-QUALITY` 0.11, `SITE-STRUCTURE` 0.4 |
| **Reviewed** | 2026-11-08, S0051 (earlier: 2026-10-29, S0057; 2026-10-27, S0037; 2026-10-25, S0030) |
| **Home** | shared contracts catalog, `documentation-quality/` and `product-site/` |
| **Role here** | contributor to the shared media exchange portal and a program that links its pages; its own site holds the three privacy pages and, with the first release, the help |

`DOC-EXTERNAL-QUALITY` section 9 (items F to I) and `SITE-STRUCTURE` section 8 (items A to F) apply to this product
through the shared media exchange portal, which FastMediaSorter Android publishes in English, Russian and
Ukrainian. As a program of the portal this product (a) opens the page that explains a screen from that screen, built
through one resolver that picks the English, Russian or Ukrainian page by the app language and falls back to English
for every other language (`SITE-STRUCTURE` items C and E; `ExchangePortalLinks`, with the readable list of held
addresses in `docs/MEDIA_EXCHANGE_PORTAL.md`), (b) writes its own sections in the three languages at once
(`docs/PORTAL_SECTIONS_STREAMSPLAYER.md`, applied in the portal source by FastMediaSorter Android, never edited from
here), and (c) shows no "not available yet" mark (item F). The portal is another product's corpus, so the items about
its addresses, anchors and deployment bind its publisher. Reading these headers does not upgrade any registry cell: the
rows for this product are the canon session's to write.

`DOC-INTERNAL-QUALITY` 0.11 binds the maintained documents of this repository (`docs/`, the help sources, the site, these pointers). S0051 built what holds it: the registry `docs/DOCUMENT_REGISTRY.jsonl` with the check in both directions the canon's validator lacks (item L, `tools/docs/assert-doc-registry.ps1`), link, anchor and asset checks (`assert-doc-links.ps1`), the house style (`assert-doc-style.ps1`), and the generated documents in step (`tools/help/generate-help-pages.ps1 -Check`, `tools/site/build-site.ps1 -Check`, `tools/privacy/permissions.ps1`), all in the pre-release sweep. `DOC-EXTERNAL-QUALITY` is held for the scope above, the three hosted privacy pages and the help: translation freshness per block (`assert-translation-freshness.ps1`), the term base over the prose (`assert-termbase.ps1`), the glossary and the subject index on the help index page. The offline help pages stay governed by `CONTEXT-HELP`; the same pages published on the site follow item O. Each permanent variant is written once in `docs/design/DOC_QUALITY_VARIANTS.md` and the registry rows point at it. The gates ran one by one on 2026-11-08; the first sweep that runs them, and the first CI run, are release steps, and the registry rows move only then for what ran.
