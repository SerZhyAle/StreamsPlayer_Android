# CLAUDE.md - StreamsPlayer for Android

Shared contracts live outside this repository, in the catalog at `P:\Contracts` - the one file here that names it. Cite a contract by id and section, never by path or link; pointers in `docs/contracts/`.

> **Canon:** the SZA Unified Rules, shipped as the `sza` Claude Code plugin (`/plugin marketplace add SerZhyAle/sza-unified-rules`, `/plugin install sza@sza-unified-rules`; outside Claude Code read `rules/INVARIANTS.md` at https://github.com/SerZhyAle/sza-unified-rules). Consumption model **reference**: the canon is not restated; this file keeps only this repository's deltas. Overlay **B** (Android). Adoption stamp: `.sza-canon.json`. Per-project record: `rules/contrib/streamsplayer_mob.md` in the canon repository. A universal rule that looks wrong for this repo is reported to the canon session, never patched here.

`AGENTS.md` delegates to this file.

## 1. The product

- Phone app, Wear OS app and a watch face for internet radio, live video (HLS/DASH) and RTSP streams - and,
  despite the name, live broadcast from the phone (microphone, camera) and the watch (microphone).
- An independent product, extracted from FastMediaSorter Android. The only coupling to any other product is the
  shared contracts catalog: no shared code, no shared Gradle module, no shared release.
- Origin, owner decisions and the extraction source map: `docs/BOOTSTRAP_HANDOFF.md` (a frozen record - its open
  items are tickets now). Work is driven from the ticket store, not from the handoff.
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
- Open owner decisions are owner-decision items inside the ticket of the phase that needs them (section 3.3 of
  each ticket); each is asked once, in question-with-options form, right before that phase.

## 4. Ticket store and the release package plan

- **The harness is adopted from the plugin, not copied.** Run any harness script through the forwarder:
  `pwsh -NoProfile -File tools/harness.ps1 <cluster>/<script> [args]` (for example `spec_catalog/validate`,
  `spec_catalog/select -Id S0002 -Format json`). Configuration is `.sza-profile.json` and nothing else; never
  edit or copy a harness script.
- **Ticket ids:** `S0001`.. (`^S\d{4}$`, the harness grammar). These ids overlap FastMediaSorter's S space, so a
  ticket of another repository is always cited with the repository name next to its id.
- Specs: `PLAN/Sxxxx_<slug>.md`. Journal: `PLAN/spec-catalog.jsonl`, written only through
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
  6. `PLAN/` is **tracked**: there is no remote yet, so an ignored ticket store would exist in exactly one place,
     and the tickets carry the owner's recorded decisions.

## 5. Repository layout

- `PLAN/` - tickets and the release plan. `docs/` - the handoff record and `docs/contracts/` pointers.
  `tools/` - the harness forwarder. Gradle modules (phone, wear, watch face) arrive with S0002.
- Scratch and evidence that is not committed: `temp/<ticket>/` (gitignored).
- Signing material never enters the tree; `.gitignore` carries the leak globs.
- Android SDK on this machine: `C:\Users\serzh\AppData\Local\Android\Sdk`.

## 6. Chain of command

This repository's session works as a subordinate of the canon session `sza-unified-rules-dc` (owner's
appointment of 2026-10-02): the owner in this chat wins; catalog changes and canon defects go to that session as
an exact diff with the reason; release, tags, uploads, anchors, keys, remotes and licence stay the owner's.
