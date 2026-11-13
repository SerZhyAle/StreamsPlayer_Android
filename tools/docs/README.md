# tools/docs - the documentation gates (S0051, contracts `DOC-INTERNAL-QUALITY`, `DOC-EXTERNAL-QUALITY`)

These keep the corpus honest: every document has one record, every link resolves, the prose keeps the house style and the
family's words, and a translation whose English block moved says so. The permanent variants of the two contracts are written
once, in [DOC_QUALITY_VARIANTS.md](../../docs/design/DOC_QUALITY_VARIANTS.md).

| What | Where |
| --- | --- |
| The registry: one record per maintained document, shape 1 in `.sza-canon.json` | `docs/DOCUMENT_REGISTRY.jsonl` |
| What is deliberately no document of the registry, each with its reason | `tools/docs/registry-ignore.txt` |
| The term base: glossary entries and forbidden words | `docs/TERMBASE.json` |
| The English block each translation was rebuilt from | `docs/l10n-fingerprints.jsonl`, written only by `-Record` |

| Script | When | What it holds | Verdict line |
| --- | --- | --- | --- |
| `assert-doc-registry.ps1` | a document added, moved or removed | rules 1 and 2: the registry to the disk and the disk to the registry, roles, the stamp, `docs/README.md` mentions every record, `design_record` resolves; runs the canon's `document_registry/validate` when it is installed | `assert-doc-registry: PASS (..)` |
| `assert-doc-links.ps1` | any Markdown edit | rules 3, 6, 7 and external rule 7: relative links, anchors, images, https only, nothing embedded from outside; also the links of `site/*.html` | `assert-doc-links: PASS (..)` |
| `assert-doc-style.ps1` | any prose edit | rule 5 in Markdown, the help sources and the term base: no long dash, no three periods, the Russian letter yo | `assert-doc-style: PASS (..)` |
| `assert-translation-freshness.ps1` | release; after an English edit to see what it staled | external rule 3, items F and G: a stale, new, gone, missing, extra or untranslated block in help, policies, permission sentences, portal sections and the term base | `assert-translation-freshness: PASS (..)` |
| `assert-translation-freshness.ps1 -Record [-Doc x] [-Locale ru]` | after rebuilding a translation from the English text | clears the flag of what you rebuilt, and nothing else; refuses a block with no variant or left in English | same |
| `assert-termbase.ps1` | a term or a word retired; any prose edit | external rule 4: the term base's own shape, and no forbidden word in the help sources, the policies, the portal sections | `assert-termbase: PASS (..)` |
| `assert-docs.ps1` | release, CI (the sweep runs it) | the five above and `tools/help/generate-help-pages.ps1 -Check`, one verdict, every child run and quoted | `assert-docs: PASS (6 gates)` |
| `test-docs-gates.ps1` | release; record as a RELEASE item after editing a gate | seeds a small repository and copies of the real help and site with one defect of every kind and requires each gate to name exactly those; code spans, frozen records and re-wrapped paragraphs stay unreported | `docs-gates-selftest: PASS (run=53 skipped=0)` |

Every script ends with one verdict line, last on stdout; `0` passed, `1` inspected and defective, `2` could not verify
(`CHECK-VERDICT`). Each takes `-RepoRoot`.

Beside them, in the sweep and in CI: `tools/site/build-site.ps1 -Check` (the site equals its sources; a published help page
carries `noindex`, stays out of the sitemap and is not blocked by robots) and `tools/help/assert-help-map.ps1`. In the sweep only:
`tools/contracts/check-sync.ps1` (the catalog is not on a runner) and, also in a store release build on CI,
`tools/privacy/permissions.ps1` (it reads the merged release manifests).

## Adding a document

Add its record to `docs/DOCUMENT_REGISTRY.jsonl` before anything links to it, and its line to `docs/README.md` if it is under `docs/`.
A generated file takes `"role":"render","generated":true` and names its tool in `rendered_by`.

## Changing the English text of a page that has translations

Change the English block. `assert-translation-freshness.ps1` now reports it `STALE` in `ru` and `uk`, and the release sweep fails on
that until the translation is rebuilt from the English text and recorded with `-Record` (item G). An author who rewrites all
three languages in one edit records all three at once. The fingerprints were first recorded on 2026-11-08 from the text as it
then stood: that is the starting point of the mechanism and no statement that each block was re-read.

## Retiring a word

Add an entry to `forbidden` in `docs/TERMBASE.json`: the locales, a pattern, a reason of at least four words. Fix every hit in the same
edit. `phone/src/test/.../ExchangeTermsDictionaryTest.kt` holds the same family words over the exchange strings of the app.
