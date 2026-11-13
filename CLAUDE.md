# CLAUDE.md - StreamsPlayer for Android

Shared contracts live outside this repository, in the catalog at `P:\Contracts` - the one file here that names it. Cite a contract by id and section, never by path or link; pointers in `docs/contracts/`.

> **Canon:** the SZA Unified Rules, shipped as the `sza` Claude Code plugin (`/plugin marketplace add SerZhyAle/sza-unified-rules`, `/plugin install sza@sza-unified-rules`; outside Claude Code read `rules/INVARIANTS.md` at https://github.com/SerZhyAle/sza-unified-rules). Consumption model **reference**: the canon is not restated; this file keeps only this repository's deltas. Overlay **B** (Android). Adoption stamp: `.sza-canon.json`. Per-project record: `rules/contrib/streamsplayer_mob.md` in the canon repository. A universal rule that looks wrong for this repo is reported to the canon session, never patched here.

`AGENTS.md` delegates to this file.

## 1. The product

- Phone app, Wear OS app and a watch face for internet radio, live video (HLS/DASH) and RTSP streams - and,
  despite the name, live broadcast from the phone (microphone, camera) and the watch (microphone).
- An independent product, extracted from FastMediaSorter Android. The only coupling to any other product is the
  shared contracts catalog: no shared code, no shared Gradle module, no shared release.
- Origin, owner decisions and the extraction source map: `docs/BOOTSTRAP_HANDOFF.md` - written 2026-10-02 by the
  FastMediaSorter session of its ticket S4053 and amended by it the same day on the owner's instruction (broadcast
  scope, section 6a), before this repository's first commit. Not edited from this repository's sessions; its open
  items are tickets now, and work is driven from the ticket store, not from the handoff.
- Read-only from sessions in this repository: `P:\ANDROID\FastMediaSorter_mob_v2` (the extraction source) and
  `P:\WINDOWS\Streams_Player` (StreamsPlayer for Windows, the reference for look and behaviour).

## 2. Standing law - staying in step with FastMediaSorter (handoff section 6a)

The playback and broadcast behaviour of this product must never quietly diverge from FastMediaSorter's. With no
shared code allowed, three mechanisms carry that guarantee, and each binds every ticket:

1. **Full port, never a rewrite.** Every stream, playback and broadcast class of the handoff source map is copied
   into this tree and cleaned (package `com.sza.fastmediasorter` -> `com.sza.streamsplayer`, sorter references
   cut), together with its unit tests. Reimplementing a behaviour that can be ported is refused in review.
2. **Port ledger and drift check.** Every ported file has a row in the committed port ledger: source path in
   FastMediaSorter, the source file's SHA-256 at port time, destination path, port date. The drift-check script
   re-hashes the sources and lists every file changed in FastMediaSorter since its port - the to-do list for
   carrying a fix across. It runs at the start of every release; a change left uncarried is a decision written
   next to its ledger row, never silence. Both exist before the first ported file (S0002).
3. **Behaviour lives in the contracts.** Playback routing, buffering, live-edge recovery and classification are
   written in the STREAM-BANK document set (documents 03, 05, 06, 07), broadcast in LIVE-BROADCAST. Both
   products conform to the same text; a behaviour either wants to change is amended in the catalog first, then
   implemented in both.

## 3. Frozen anchors and owner decisions

- **Application ids** (owner, 2026-10-02): `com.sza.streamsplayer` for phone and watch (one shared id - the Data
  Layer requires it), `com.sza.streamsplayer.watchface` for the watch face. The noLegal variant shares the base
  id of the store variant. Frozen forever once the first package ships.
- **Signing** (owner, 2026-10-02): the key lives in `P:\ANDROID\StreamsPlayer_credentials`, outside the
  repository; Play App Signing with this product's own key imported as the app signing key, plus a separate
  upload key. Store builds and sideload APKs carry one signature.
- **License:** MIT (owner, 2026-10-02).
- **Variants:** two - `store` and `noLegal`. Interface in `src/main`, implementation in the variant source set,
  one DI module per variant. The FastMediaSorter flavor matrix is not inherited.
- **Public presence** (owner's order of 2026-11-13, "Повтори всё выполненное для новго Streams_mob", the DeviceInfo
  procedure of canon S0058; decisions in the quiz blocks of S0046 and S0047):
  - public remote `https://github.com/SerZhyAle/StreamsPlayer_Android`, branch `main`, everything public (`PLAN/`
    included); issues on, wiki and projects off; the product tree arrived in commit `7b6ce41`;
  - GitHub Pages from `site/` by `.github/workflows/pages.yml` (check on Windows, deploy on Linux): the three privacy
    pages, `sitemap.xml` and `robots.txt` answer 200; the site root answers 404 until the landing (S0045) exists, so the
    repository has no homepage and the listings no website yet;
  - Play Console, personal account `8446656778368889827`: `com.sza.streamsplayer` ("StreamsPlayer: Internet Radio",
    app `4972961129584993008`) and `com.sza.streamsplayer.watchface` ("StreamsPlayer Watch Face", app
    `4976077107533670611`), both App, Free, both package names now bound to the account; App content, store settings,
    the en-US/ru-RU/uk listing of the main app with icon and feature graphic, and the Wear OS form factor of both are
    recorded in `docs/play/PLAY_DATA_SAFETY.md` ("Console record") and `docs/play/PLAY_LISTING.md`. Nothing is
    uploaded or published; what remains is S0072.
- Open owner decisions are owner-decision items inside the ticket of the phase that needs them (section 3.3 of
  each ticket); each is asked once, in question-with-options form, right before that phase.

## 4. Ticket store and the release package plan

- **The harness is adopted from the plugin, not copied.** Run any harness script through the forwarder:
  `pwsh -NoProfile -File tools/harness.ps1 <cluster>/<script> [args]` (for example `spec_catalog/validate`,
  `spec_catalog/select -Id S0002 -Format json`). Configuration is `.sza-profile.json` and nothing else; never
  edit or copy a harness script.
- **Ticket ids:** `S0001`.. (`^S\d{4}$`, the harness grammar). These ids overlap FastMediaSorter's S space, so a
  ticket of another repository is always cited with the repository name next to its id.
- Specs: `PLAN/Sxxxx_<slug>.md`; the spec of a closed ticket (`Verified`, `Archived`) lives in `PLAN/DONE/`, the
  archive spelling declared in `.sza-profile.json` (`REPO-LAYOUT` rule 6). Journal: `PLAN/spec-catalog.jsonl`, written only through
  `spec_catalog/insert`, `update`, `close`, `archive` - never by hand.
- **Release package plan** - six decisions, recorded once:
  1. Files: `PLAN/RELEASE_QUEUE.md` (work remaining), `PLAN/RELEASE_READY.md` (ready),
     `PLAN/RELEASE_QUEUE_DONE.md` (shipped history, newest first).
  2. Single write path: every journal mutation goes through the harness's `Write-Catalog`, which calls
     `Sync-ReleaseQueue`; no skill or script maintains the files by itself.
  3. Ready set: `Implemented`, `Verified`, `BlockNeedUserTest`. Work remaining: `Draft`, `Approved`, `Tactical`,
     `In Progress`, `Partial`, `Broken`, `BlockByOtherTask`, `BlockQuestions`, `BlockExternal`.
  4. Package number: the `current-next-release:` marker in `PLAN/RELEASE_QUEUE.md`, starting at 1, advanced by
     `-Ship`. Not tied to a branch - this repository works on `main`. `--` is the not-scheduled bucket.
  5. Operator commands (all through `tools/harness.ps1 spec_catalog/release-queue`): `-List`, `-List -Ready`,
     `-Validate`, `-Reconcile`, `-SetCurrent N`, `-Ship -Release N [-Version X] -DryRun`. Package headings and
     line order are the owner's; no script reorders them.
  6. `PLAN/` is **tracked** and public on the remote (S0046 input D1, owner 2026-11-13): an ignored ticket store
     would exist in exactly one place, and the tickets carry the owner's recorded decisions. Nothing private goes
     into a ticket: the public contact is `sza@ukr.net`.

## 5. Repository layout

- `PLAN/` - tickets and the release plan. `docs/` - the handoff record, `docs/contracts/` pointers, the port
  ledger `docs/port-ledger.jsonl` and the S0025 findings ledger `docs/audit/findings.jsonl`. `tools/` - the harness
  forwarder and `tools/port-ledger/` (record a port, check drift against FastMediaSorter; its README says when to run
  what). `tools/audit/` - the S0025
  dynamic-audit rig and its coverage gate; its self-tests run at release (its README).
- In-app help (S0035, `CONTEXT-HELP`): the help map is `phone/src/main/assets/help/help-map.json` - one row per
  surface, a page or `inherit` / `none` with a reason; the page sources are in `docs/help/content/`; `tools/help/`
  holds the generator, the screenshot capture, the control diff and the gate `assert-help-map.ps1` (its README says
  when to run what), which the pre-release sweep runs. Accepted debt is `tools/help/help-map-baseline.txt` and only
  falls. A phone screen added or changed in the same edit changes its help source and its map row.
- Documentation quality (S0051, `DOC-INTERNAL-QUALITY`, `DOC-EXTERNAL-QUALITY`): every maintained document has a record in
  `docs/DOCUMENT_REGISTRY.jsonl`, written before anything links to it; `tools/docs/` holds the registry, link, style, translation
  freshness and term gates and their aggregator `assert-docs.ps1` (its README says when to run what); permanent variants are
  `docs/design/DOC_QUALITY_VARIANTS.md`. An English block edited makes its translation stale until it is rebuilt and recorded.
- Promotion (S0014, `PROMOTION`): `POSITIONING.md` is the one source every external surface (README, the Play listing in
  `docs/play/PLAY_LISTING*.md`, the landing, the repository metadata) is written from; `tools/promotion/` holds the forbidden-terms
  list, the gate `assert-promotion.ps1` (its README says when to run what) and the checkpoint script. No other product's name and no
  piracy word in a published source, ever; the internal research and the go ledger are in `PLAN/S0014_free-promotion-campaign/`.
- Gradle (S0002): application modules `phone`, `wear`, `watchface`; S0022 adds the Android-free JVM library
  `exchange-core`, consumed by phone and wear (owner decision, 2026-10-23). `build-logic/` is the included build of convention
  plugins (platform levels, the mechanically derived version, release signing, the `store` / `noLegal`
  variants) with its own unit tests. Pins live in `gradle/libs.versions.toml`. Release builds use `./gradlew`; the
  SDK path goes in the gitignored `local.properties`, the JDK in the user-level `gradle.properties`.
- Version stamp: never typed. An invocation that packages stamps itself from one clock reading; a release
  pins the pair per family with `-Psp.versionName` / `-Psp.versionCode` (one module per invocation); a build
  that neither packages nor pins carries the non-releasable sentinel. Release signing reads
  `keystore.properties` (gitignored) or the file named by `STREAMSPLAYER_KEYSTORE_PROPERTIES`; without a key
  release packages are built unsigned, never signed with the debug key.
- Scratch and evidence that is not committed: `temp/<ticket>/` (gitignored).
- Signing material never enters the tree; `.gitignore` carries the leak globs.
- Android SDK on this machine: `C:\Users\serzh\AppData\Local\Android\Sdk`.

## 5a. Verification between releases

Between releases run only checks measured at 5 s or less, plus static inspection and a direct call of the changed
area; full builds, unmeasured checks and slower tests run at release (TESTING_AND_QA section 2). Record each
skipped rung in the ticket audit as a RELEASE item naming the release command; report "not built, not tested until
the release". Android/Gradle has no measured fast test or compile form; do not run one between releases.

- Help-map fast command: `pwsh -NoProfile -File tools/help/assert-help-map.ps1`; 0.739 s, 2026-10-29, direct
  command wall time, exit 0 and `assert-help-map: PASS` (30 surfaces, 27 rows, 3 accepted baseline entries).
- Release script syntax fast command: `pwsh -NoProfile -File tools/release/check-syntax.ps1`; 0.288 s,
  2026-10-29, direct command wall time, exit 0 and `release-syntax: PASS` for the pre-release sweep.
- Promotion gate fast command: `pwsh -NoProfile -File tools/promotion/assert-promotion.ps1`; 0.63 s, 2026-11-13, direct
  command wall time, exit 0 and `assert-promotion: PASS` (12 rules, 61 published files). Its self-test
  `tools/promotion/test-promotion-gate.ps1` is 6.05 s, 2026-11-13: release class. `tools/promotion/write-checkpoint.ps1` reads the
  network (1.84 s, 2026-11-13): run at the first outward step, never between releases for its own sake.
- Documentation gates, fast one by one (S0051), `pwsh -NoProfile -File tools/docs/<gate>.ps1`, 2026-11-08, direct command wall
  time, exit 0 and the gate's `PASS`: `assert-doc-registry` 1.223 s, `assert-doc-links` 0.879 s, `assert-doc-style` 0.635 s,
  `assert-translation-freshness` 0.570 s, `assert-termbase` 0.563 s. Also `pwsh -NoProfile -File tools/help/generate-help-pages.ps1 -Check`
  2.532 s, `pwsh -NoProfile -File tools/site/build-site.ps1 -Check` 0.333 s and `pwsh -NoProfile -File tools/contracts/check-sync.ps1
  -CatalogRoot <catalog>` 0.390 s; check-sync exits 1 with 14 findings on the live catalog today (S0051 audit names them).
- Store graphics (S0047): `pwsh -NoProfile -File tools/store/render-store-assets.ps1` renders `docs/play/icon-512.png`,
  `docs/play/feature-graphic-1024x500.png` and `docs/github/social-preview-1280x640.png` from the launcher glyph; 0.36 s,
  2026-11-13, exit 0. Run it after changing the glyph; the PNGs are render targets.
- Release command: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` runs the release builds, Gradle
  unit suites and tool self-tests, as well as the existing release checks. `-SkipBuild` is release-time only.
- Release class: Gradle full phone/Wear/core suite 132 s and filtered phone/core suite 73 s, 2026-10-25,
  command timings recorded in `PLAN/DONE/S0026_contract-sync-2026-10-12.md`; `tools/docs/assert-docs.ps1` 6.711 s and
  `tools/docs/test-docs-gates.ps1` 7.889 s, 2026-11-08; all other unmeasured Gradle suites,
  audit, port-ledger, help-gate and CI-launcher self-tests remain release until measured there.

## 6. Chain of command

This repository's session works as a subordinate of the canon session `sza-unified-rules-dc` (owner's
appointment of 2026-10-02): the owner in this chat wins; catalog changes and canon defects go to that session as
an exact diff with the reason; release, tags, uploads, anchors, keys, remotes and licence stay the owner's.
