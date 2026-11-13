# S0051 - Documentation quality: document registry, link and style gates, freshness and glossary

**Ticket:** S0051
**Status:** Implemented
**Status note:** Implemented 2026-11-08 (unattended queue pass): registry, link, style, freshness and term gates with a self-test (run=53), help -Check, subject index and glossary, wired into the sweep and CI; each gate ran alone, exit 0. Not built, not tested until the release: sweep, capture, permissions -Build, first CI run. Catalog: canon session applies the S0057 then the S0051 patch. Known reds in Last Audit.
**Complexity:** hard - confirmed by the plan pass of 2026-11-08: six corpus-wide gates with a self-test, a generator check mode, two generated help sections, release and CI wiring and shared-contract conformance. The publication decision is settled (S0045 D2 a).
**Date:** 2026-10-27
**Phase:** documentation quality - independent of the build phases of handoff section 8
**Verification level:** gate - the registry and the gates run in the pre-release sweep with a verdict line, each with a real negative case.
**Owner:** the StreamsPlayer Android session of this repository.

## Captured request and migration scope

The S0037 audit (`PLAN/S0037_contract-sync-20261017/audit.md`, section 8) found that this repository keeps a
documentation corpus (`docs/`, `docs/help/`, `site/`, the privacy pages, `docs/contracts/` pointers) without
the interface `DOC-INTERNAL-QUALITY` 0.10 and `DOC-EXTERNAL-QUALITY` 0.12 define, and that the catalog holds no
row for either. This ticket closes the gaps or records permanent variants.

## 1. Gaps to close (each from the audit)

1. **Document registry** (internal rules 1-2): a registry of the maintained documents with topic, product area, update triggers and role `source|render`, and reverse coverage both ways. Default path `docs/DOCUMENT_REGISTRY.jsonl`; declare it in `.sza-canon.json` through the adopt-canon run (`docRegistryShape` / `docRegistryFile` are null today). Use the plugin's checker and cover the missing checks identified in the spec pass below; generated files (`site/*.html`, `phone/src/main/assets/help/**`) take the `render` role.
2. **Link, anchor and house-style gates for `.md`** (rules 3, 5), with the verdict line of `CHECK-VERDICT`.
3. **Generated documents in step** (rule 4): a `-Check` mode for `tools/help/generate-help-pages.ps1` (fixed date), and `tools/site/build-site.ps1 -Check`, `tools/privacy/permissions.ps1` and `tools/contracts/check-sync.ps1` wired into `tools/release/pre-release-sweep.ps1` and CI (the sweep keeps its own copy of the pointer bindings: one table).
4. **Freshness of translations** (external rule 3, section 9 G): a mechanism that marks a localized page stale when its English block changed (fingerprint per block or paired commits), for help, policies and the portal sections.
5. **Term base for prose and a glossary** (external rules 1, 4), extending the exchange-term check beyond the app strings to the help sources, the policies and `docs/PORTAL_SECTIONS_STREAMSPLAYER.md`. A subject index if the help is published (S0045 decision D2).
6. **Screenshots fresh** (external rule 6): the diff of a regeneration against the committed set (S0035's rung 2).
7. `docs/README.md` complete; pointer `docs/contracts/PORTAL-DOCS.md` kept true.

## 2. Owner decisions needed (ask once, in question-with-options form)

- Resolved: `PLAN/` is tracked by the recorded owner decision; the conflicting ignore rule needs reconciliation.
- Resolved 2026-11-08 (owner answer recorded in S0045 D2, option (a)): the help is published on the site as `help/{en,ru,uk}/` with the first release; the addresses are permanent. This fixes the public surface and the addresses.
- Resolved: the 25-page threshold counts primary-locale documents, not locale variants, per `DOC-EXTERNAL-QUALITY` 0.13.

### Spec pass - 2026-10-29

- **Rung:** complex; the work creates files, spans more than three existing files and requires a publication decision.
- **Shared-contract boundary:** yes. This ticket implements documentation-quality contracts and describes the shared
  portal contribution; it does not order a change to their shared rules or a wire format. The catalog owns its
  registry rows and any amendment: no product session edits the catalog. Contract changes, if needed, precede the
  corresponding implementation. The live headers are `DOC-INTERNAL-QUALITY` 0.11 and `DOC-EXTERNAL-QUALITY` 0.13;
  the pointer and the original scope above still record the older review and require reconciliation in this ticket.
- **PLAN decision resolved:** `CLAUDE.md` section 4 records the owner's decision that `PLAN/` is tracked. The
  `PLAN/` ignore rule conflicts with that decision; `git ls-files -- PLAN/spec-catalog.jsonl` confirms that at
  least the journal is tracked. Do not infer a new owner decision from the ignore rule. Reconcile the rule and
  registry coverage with the recorded decision in the plan, without silently exposing or deleting local records.
  If any maintained corpus remains partly tracked, `DOC-INTERNAL-QUALITY` 0.11, amendment 2026-10-29 item K, requires
  disjoint tracked and local registries.
- **Page-count question resolved:** `DOC-EXTERNAL-QUALITY` 0.13, amendment 2026-10-29 item N, counts primary-locale documents,
  not locale variants, and excludes pages published from `CONTEXT-HELP` sources. The portal is another product's
  corpus and its publisher owns its index; locale variants are not counted as separate pages.
- **Generated roles and checker:** `DOC-INTERNAL-QUALITY` section 2 rule 1 defines `render` as regenerated and
  never hand-edited. The canon's document-registry validator uses a `generated` field, but its source checks
  declared paths and indexable records, not reverse coverage of every maintained directory or Markdown anchors.
  A plan must cover those missing checks with explicit negative cases; a successful registry validation alone
  cannot establish reverse coverage or link integrity (0.11, amendment 2026-10-29 item L).
- **Unresolved owner input:** S0045 D2 asks whether to publish the offline help on the site. Its answer determines
  the public help surface and permanent addresses; the recommendation in S0045 is not an answer. S0045 remains
  `BlockQuestions`. If published, `DOC-EXTERNAL-QUALITY` 0.13, amendment 2026-10-29 item O, excludes those help pages from
  the sitemap and SEO block and requires `noindex`; reconcile S0045's prospective sitemap wording with that rule.
  **Resolved 2026-11-08:** D2 was answered (a), publish; S0045 is `Approved`. The `noindex` and sitemap reconciliation above is now a plan
  item of this ticket, not an open question, and the subject index of gap 5 applies.
- **Documentation context:** the document registry does not exist yet; `docs/contracts/PORTAL-DOCS.md`,
  `docs/README.md`, help sources, privacy sources, portal sections and generated site/help pages are affected by
  the ordered work. This spec pass changes no product documentation or generated surface.

### 2.1 Owner inputs (Approval gate)

- **Related tickets:** S0045 (D2 answer and the site help pages), S0048 (the family map's Help index cell), S0035 (help map, generator and
  screenshots), S0037 (the audit this ticket answers).
- **D1 decision (publish the help on the site, from S0045 D2):** (a) publish `help/{en,ru,uk}/` with the first release; the addresses are
  permanent - owner, 2026-11-08 (recorded in S0045, read here; no new question was asked).

### Quiz decisions (2026-11-08)
- Publish the offline help on the site? -> yes, `help/{en,ru,uk}/` with the first release (resolved without asking: the owner answered this
  question as S0045 D2, option (a), in the quiz of 2026-11-08; S0048 D2 carries the same answer into the Help index cell).
- Consequence for acceptance item 2: the permanent variant "offline help is not a site page" no longer applies, because the help is a site
  page. The remaining permanent variant is "the portal is another product's corpus". The acceptance text is left as written; the plan
  reconciles it.

## 3. Acceptance

- [x] The registry exists, is checked in both directions, and the gates run in the pre-release sweep; each gate has a negative case that fails. (Wiring proven statically and each gate run on its own; the first sweep is a RELEASE item.)
- [x] Permanent variants (offline help is not a site page; the portal is another product's) are recorded in a dated design record and referenced from the registry rows. (The first is retired by S0045 D2 and its successor recorded: `docs/design/DOC_QUALITY_VARIANTS.md` V1 and V2.)
- [x] Rows `DOC-INTERNAL-QUALITY` and `DOC-EXTERNAL-QUALITY` advance only to what ran; no release, tag, upload or deployment is implied. (Proposed in `PLAN/S0051_documentation-quality-gates/registry-sync.patch`; the canon session applies it.)

## 4. Plan - tactical pass 2026-11-08

Rung: complex (new files, more than three existing files, new public gates). Fills the slots from `CLAUDE.md` sections 4, 5 and
5a: no build and no Gradle test between releases; every gate is checked by a direct call and a measured wall time.

### 4.1 Coverage inventory

| Line | Step |
| --- | --- |
| Gap 1 registry, reverse coverage, `render` role, stamp keys | T1, T2 |
| Gap 2 link, anchor and house-style gates with the `CHECK-VERDICT` line | T3, T4 |
| Gap 3 help `-Check` with a fixed date; site, permissions and contract sync in the sweep and CI; one bindings table | T5, T9 |
| Gap 4 translation freshness for help, policies and portal sections | T6 |
| Gap 5 term base over prose, glossary, subject index (the help is published, S0045 D2 a) | T7 |
| Gap 6 screenshots fresh: rung 2, `CONTEXT-HELP` section 7 item D, the static form with strings and drawables added | T8 |
| Gap 7 `docs/README.md` complete, `PORTAL-DOCS` pointer true | T2 (gate), T10 |
| Internal rule 6 (assets), rule 7 (offline, https) | T3 |
| Item O and `CONTEXT-HELP` item G: published help carries `noindex`, stays out of the sitemap, robots does not block it; S0045's wording reconciled | T5, T10 |
| `PLAN/` ignore rule against the recorded decision; item K (partly tracked folders) | T1 |
| Acceptance 1 (each gate has a negative case that fails) | T9 |
| Acceptance 2 (dated design record, referenced from the registry rows) | T2 |
| Acceptance 3 (rows advance only to what ran) | T11 |
| Page-count threshold | out-of-scope: settled by `DOC-EXTERNAL-QUALITY` item N, one document below 25; recorded in T2's design record |
| Help on the site itself (`site/help/`) | out-of-scope: S0045 T6, blocked on S0014; this ticket supplies its gate and reconciles its wording |
| Contract sync on a CI runner | out-of-scope: the catalog is not on a runner and is no git repository (`git -C` of it answers "not a git repository", 2026-11-08); recorded in the design record, the sweep holds it |

### 4.2 Decisions taken without asking (settled by the sources named)

- **The `.gitignore` line `PLAN/`** is removed: `CLAUDE.md` section 4 item 6 is the recorded decision and this ticket's section 2 orders
  the reconciliation. No remote exists, so nothing is exposed by it; S0046 D1 stays the owner's gate before the first push, and its
  note says so. Nothing is deleted. The ticket store is outside the registry: its journal is its registry.
- **Stamp keys only, no re-sync.** `docRegistryShape` and `docRegistryFile` are added under `REPO-STAMP` rule 4 and the contrib record
  gets its dated section; a canon re-sync (`canon.version`, digest) is a different run with its own reconciliation.
- **Placement of the subject index and the glossary:** the help index page, the page the help's entry points already open
  (`DOC-EXTERNAL-QUALITY` section 8 item A). Generated from the page sources and the term base, so no second list exists; in-app and,
  through S0045 T6, on the site.
- **Translation fingerprints:** one row per English block with the fingerprint each locale was rebuilt from
  (`DOC-EXTERNAL-QUALITY` section 8 leaves the form open, section 9 item G says the flag clears only by a rebuild).
- **Rung 2:** the static form of `CONTEXT-HELP` section 7 item D, which the contract allows and the tree already half holds. A device
  regeneration stays how a stale record is cleared.

### 4.3 Steps, topology and checks

Pre-implementation blockers: none. Every step runs without a build. No step consumes what a later step produces.

- [x] **T1 Shared library, registry gate, ignore rule.** Produces: `tools/docs/_docs-lib.ps1`, `tools/docs/assert-doc-registry.ps1`,
  `tools/docs/registry-ignore.txt`, `.gitignore` without `PLAN/`. Consumes: `.sza-canon.json`, the plugin's `document_registry/validate`.
  Check: the gate exits 2 with `COULD NOT VERIFY` while the registry is absent.
- [x] **T2 Registry, stamp, design record.** Produces: `docs/DOCUMENT_REGISTRY.jsonl`, `docs/design/DOC_QUALITY_VARIANTS.md`, the two stamp
  keys, a complete `docs/README.md`. Consumes: T1. Check: `assert-doc-registry: PASS`.
- [x] **T3 Link gate.** Produces: `tools/docs/assert-doc-links.ps1`. Consumes: T1, T2. Check: `assert-doc-links: PASS` on the tree.
- [x] **T4 Style gate.** Produces: `tools/docs/assert-doc-style.ps1`; the corpus free of findings or the file frozen. Consumes: T1, T2.
  Check: `assert-doc-style: PASS`.
- [x] **T5 Generated documents.** Produces: `-Check` in `tools/help/generate-help-pages.ps1` (fixed date); the published-help rule in
  `tools/site/build-site.ps1 -Check`. Consumes: nothing new. Check: both exit 0 on the tree.
- [x] **T6 Translation freshness.** Produces: `tools/docs/assert-translation-freshness.ps1`, `docs/l10n-fingerprints.jsonl`. Consumes: T1.
  Check: `assert-translation-freshness: PASS`.
- [x] **T7 Term base, glossary, subject index.** Produces: `docs/TERMBASE.json`, `tools/docs/assert-termbase.ps1`, the two sections of the
  help index page and `help.css` rules. Consumes: T1, T5, the vendored item CW table. Check: `assert-termbase: PASS`;
  `generate-help-pages -Check` exits 0 after regeneration; `assert-help-map: PASS`.
- [x] **T8 Rung 2 inputs.** Produces: a `visibleSha256` of strings and drawables in `tools/help/help-shot-inputs.ps1`, written by the capture
  and compared by `assert-help-map.ps1`. Consumes: nothing new. Check: `assert-help-map: PASS`; a direct call detects a changed string.
- [x] **T9 Aggregator, self-test, wiring.** Produces: `tools/docs/assert-docs.ps1`, `tools/docs/test-docs-gates.ps1`, `tools/docs/README.md`; the sweep
  runs the aggregator and its self-test and calls `tools/contracts/check-sync.ps1` instead of its own table; `tools/ci/build-packages.ps1` and
  `.github/workflows/pages.yml` run the gates. Consumes: T1 to T8. Check: `docs-gates-selftest: PASS`, `assert-docs: PASS`,
  `release-syntax: PASS`; `grep -c "'rule-adoption'" tools/release/pre-release-sweep.ps1` prints 0.
- [x] **T10 Records.** Produces: true text in the `PORTAL-DOCS` and `CONTEXT-HELP` pointers and `docs/contracts/README.md`; `docs/DOCS_SURFACES.md`,
  `tools/help/README.md`, `CLAUDE.md` sections 5 and 5a, `CHANGELOG.md`; S0045 and S0046 notes; the contrib record section. Consumes: T1 to T9.
  Check: `assert-docs: PASS` again; the fast-command timings are in `CLAUDE.md`.
- [x] **T11 Catalog hand-over.** Produces: `PLAN/S0051_documentation-quality-gates/registry-sync.patch` and its reason note, on top of the S0057
  patch. Consumes: T9, T10. Check: the patch chain applies to a scratch copy of the live registry.

Self-review: every inventory line maps to a step above or carries an out-of-scope reason. Every consumed path exists on 2026-11-08 or is
produced by an earlier step (`tools/help/*`, `tools/site/build-site.ps1`, `tools/release/pre-release-sweep.ps1`, `tools/contracts/check-sync.ps1`,
`tools/ci/build-packages.ps1`, `.github/workflows/pages.yml`, `phone/src/test/resources/device-exchange/terms-item-cw.md`). The last step of each
gate is a command with an exit code, never a judgement.

## Last Audit

**Date:** 26-11-08 | **Mode:** static plus a direct call of each gate (between releases: nothing above 5 s except the single measuring runs named below) | **Outcome:** Implemented - not built, not tested until the release
**Counts:** PASS 15 | WARN 0 | FAIL 0 | MANUAL 0 | UNCHECKABLE 0 | EXEMPT 3 | RELEASE 6

**Proven by fresh commands (2026-11-08, each `pwsh -NoProfile -File ..`; expected exit 0 and the word PASS unless a line says otherwise):**

| # | Claim | Command | Actual |
| --- | --- | --- | --- |
| 1 | Registry in both directions (gap 1, acceptance 1) | `tools/docs/assert-doc-registry.ps1` | exit 0, 1.247 s, `PASS (records=30 files=264 ignore-lines=5)`; runs the canon's validator inside |
| 2 | Links, anchors, assets, https (gap 2) | `tools/docs/assert-doc-links.ps1` | exit 0, 0.946 s, `PASS (documents=42 pages=3 links=174)` |
| 3 | House style in the prose (gap 2) | `tools/docs/assert-doc-style.ps1` | exit 0, 0.633 s, `PASS (documents=42 sources=13)` |
| 4 | Translation freshness (gap 4) | `tools/docs/assert-translation-freshness.ps1` | exit 0, 0.576 s, `PASS (blocks=221 locales=ru,uk)` |
| 5 | Term base over the prose (gap 5) | `tools/docs/assert-termbase.ps1` | exit 0, 0.519 s, `PASS (terms=12 forbidden=8 blocks=48)` |
| 6 | Help pages equal their sources, fixed date (gap 3) | `tools/help/generate-help-pages.ps1 -Check` | exit 0, 2.597 s, `PASS (checked=37 pages=11 .. date=2026-10-29)`; before the index changed, it named only the 4 files this ticket edited, so no earlier drift existed |
| 7 | Site equals its sources, published-help rule (gap 3, item O) | `tools/site/build-site.ps1 -Check` | exit 0, 0.335 s, `OK site:` |
| 8 | Every gate has a negative case that fails (acceptance 1) | `tools/docs/test-docs-gates.ps1` | exit 0, 7.889 s, `docs-gates-selftest: PASS (run=53 skipped=0)`: one run to measure it, above 5 s, so it is release class now. Ran after the last edit of `tools/docs/*`; the generator's subject index was reworded after it, so it reruns at the release |
| 9 | One verdict for the sweep and CI | `tools/docs/assert-docs.ps1` | exit 0, 6.711 s, `PASS (6 gates)`, one measuring run; each child reran alone above |
| 10 | Sweep parses; its own bindings table is gone (gap 3) | `tools/release/check-syntax.ps1`; `Select-String pre-release-sweep.ps1 "'rule-adoption'"` | exit 0, `release-syntax: PASS`; 0 hits |
| 11 | Sweep and CI call the gates (gap 3) | `Select-String` over the sweep, `build-packages.ps1`, `pages.yml` | sweep lines 93, 137, 151, 213, 244, 253; `build-packages.ps1` lines 89-91, 165; `pages.yml` lines 35, 37. The sweep's patterns, called directly: match the PASS lines, reject the FAIL lines |
| 12 | Rung 2 holds strings and drawables (gap 6) | a direct call of `Get-HelpShotVisible` on a copy with one string changed; `assert-help-map.ps1 -Root <copy>` with a matching and a moved `visibleSha256` | differ=True; matching: no `player` finding; moved: `STALE-SHOT player: a string or drawable it shows changed` |
| 13 | Contract sync has a verdict line and exit 2 | `tools/contracts/check-sync.ps1` on the live catalog, a scratch catalog and a missing one | exit 1 `contract-sync: FAIL (14 finding(s))`, 0.390 s; exit 1 with 3 findings; exit 2 `COULD NOT VERIFY` |
| 14 | The catalog patch applies (acceptance 3) | `patch -p1 --binary`, S0057 then this ticket's, on a copy of the live registry | both exit 0; result byte-identical to the proposed file |
| 15 | The help index looks right (gap 5) | headless Edge on `ru/index.html` and `uk/index.html` | subject index and terms render in the page's layer. Found and fixed by looking: one page linked twice from a row; 0 such rows after the fix, 125 rows in `ru` |

**Known red, none of it from this ticket, tracked so it cannot mask a regression:**

- `tools/help/assert-help-map.ps1` exits 2 on the tree: `help-map-baseline.txt` is not in HEAD yet (S0060). With `-CommittedBaseline` it exits 1 with five `STALE-SHOT` (broadcast, broadcast-settings, catalog, filter-and-sort, qr-scanner): layouts edited by other tickets after the capture of 2026-10-29, recorded by S0039 and S0050 as the release capture.
- `check-sync`: 14 findings on the live catalog; 11 stale cells move with the unapplied S0057 patch, the missing `EXCHANGE-MARK` row is the S0039 patch, the item CZ table and its provenance row are S0053. With this ticket's chain on a scratch catalog 3 remain.
- `check-compliance`: 1 error `SZA-VER03` (recorded in S0033, S0041), warnings `SZA-CANON03` (the canon moved, a re-sync), `SZA-CTR02` on `PLAN/DONE/S0003_stream-core-port.md`, `SZA-SURF02` og:image (S0045 P6).
- `spec_catalog/validate`: `[FAIL] Journal->FS` on S0031, standing since 2026-10-27.

The sweep now reports the first two as FAIL (`HelpMap`, `ContractSync`) until they are cleared. That is the wiring doing its job.

**Exempt:** a local registry (item K: no partly tracked corpus, every file under `docs/`, `site/` and the help is seen by git); contract sync on CI (design record V4); `site/help/` itself (S0045 T6, its gate is in place, its wording reconciled in S0045 P5 and P6).

**Honest limits:** the fingerprint ledger was first recorded on 2026-11-08 from the text as it stood; that starts the mechanism and says nothing about each of the 221 blocks having been re-read. Headless Edge is not the app's WebView; the old-WebView fallback rules were added beside the others (S0055) and not looked at on API 27. The strings half of rung 2 binds only from the next capture: today's 8 records carry no `visibleSha256`, and the gate says so on a `note:` line.

**Scope and hygiene:** new `tools/docs/`, `docs/DOCUMENT_REGISTRY.jsonl`, `docs/TERMBASE.json`, `docs/l10n-fingerprints.jsonl`, `docs/design/DOC_QUALITY_VARIANTS.md`, `PLAN/S0051_documentation-quality-gates/`. Edited: `tools/help/{generate-help-pages,assert-help-map,capture-help-screenshots,help-shot-inputs,test-help-gate}.ps1`, `tools/help/help.css`, `tools/help/README.md`, `tools/site/build-site.ps1`, `tools/contracts/check-sync.ps1`, `tools/release/pre-release-sweep.ps1`, `tools/ci/build-packages.ps1`, `.github/workflows/pages.yml`, `.sza-canon.json`, `.gitignore`, `CLAUDE.md`, `CHANGELOG.md`, the regenerated help pages (three index pages and `help.css`), and the records below. Outside this repository only the allowed section of the contrib record (`rules/contrib/streamsplayer_mob.md`). Probe invariant: no `S0051` probe exists; the ticket is not in the needs-manual-test status.

**Documentation context:** changed and re-validated: `docs/README.md`, `docs/DOCS_SURFACES.md`, pointers `PORTAL-DOCS`, `CONTEXT-HELP`, `AUTOMATED-CHECKS`, `docs/contracts/README.md`, S0045 P5 and P6, S0046 D1. Unchanged: the privacy sources, the portal sections, the help sources.

**RELEASE items (not built, not tested until the release):**

- RELEASE: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` - expected: `Docs` and `DocsGateSelfTests` PASS; `HelpMap`, `ContractSync` and `Compliance` PASS once the known reds above are cleared.
- RELEASE: `pwsh -NoProfile -File tools/help/test-help-gate.ps1` - expected: `help-gate-selftest: PASS (run=21 skipped=0)`, with the new case, after the capture below.
- RELEASE: `pwsh -NoProfile -File tools/help/capture-help-screenshots.ps1 -Serial <emulator>`, then `generate-help-pages.ps1` - expected: no `STALE-SHOT`, `visibleSha256` on every record, the `note:` line gone. The same build shows the new help index in the app's WebView.
- RELEASE: `pwsh -NoProfile -File tools/privacy/permissions.ps1 -Build` - expected: `OK permissions:`. Not run here: it needs a Gradle build.
- RELEASE: the first release run of `.github/workflows/android-build.yml` (`tools/ci/build-packages.ps1`) and the first run of `.github/workflows/pages.yml` - expected: the new steps pass on ubuntu, with the note that the canon's validator is not installed there. Not run here: no remote exists.
- RELEASE: the canon session applies `PLAN/S0057_contract-sync-2026-10-29/registry-sync.patch`, then `PLAN/S0051_documentation-quality-gates/registry-sync.patch`; the rows hold what ran and move again only after the first sweep.
