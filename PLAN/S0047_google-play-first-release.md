# S0047 - First release on Google Play: signed bundles, console, listing, rollout

**Ticket:** S0047
**Status:** Partial
**Status note:** Console part B done 2026-11-13 for both apps except Sign in details, Target audience and Data safety submit of the main app (owner), screenshots and the watch face listing; parts A, C, D open - S0072.
**Date:** 2026-10-26
**Phase:** package 1 - the first store release (owner decision В39 of 2026-10-14, recorded in S0010)
**Verification level:** a PASS pre-flight verdict naming the version; signed `store` bundles of `phone`, `wear` and `watchface`
that match the plan; every console form complete and equal to its source in `docs/play/`; an install and an update over a prior
install on real devices from a Play test track; after production rollout, the listing answers 200 and a device installs it.
Static predicates in section 4, each with its command or the owner's console read.
**Owner:** the StreamsPlayer Android session of this repository drives and prepares; the console account, keys, uploads, the
production release and every submission are the owner's (`CLAUDE.md` section 6; invariants 1 and 4).

## Captured request and migration scope

Owner, 2026-10-26, "create specifications", item 2 of four, verbatim: "publish in Google Play".

This ticket is the whole path from signed packages to a live listing. It does not write the listing's wording (S0014 P1 to P3),
the privacy policy and Data safety answers (S0033), the repository (S0046) or the other products' links (S0048); it consumes the
first three and hands the live URL to the rest.

## 0. Source

- S0010 decision В39 (owner, 2026-10-14): the first release goes straight to Google Play; the `store` variant ships, `noLegal`
  has no channel; store packages are App Bundles; "Play App Signing with this product's own key imported" and the `store`
  bundles of the three modules "are steps of the owner's first release run".
- `CLAUDE.md` section 3 (frozen anchors, 2026-10-02): `com.sza.streamsplayer` for phone and watch (one shared id - the Data
  Layer requires it), `com.sza.streamsplayer.watchface` for the watch face; the signing key lives in
  `P:\ANDROID\StreamsPlayer_credentials` outside the repository; Play App Signing with the product's own key imported as the app
  signing key, plus a separate upload key; store builds and sideload APKs carry one signature.
- S0033 (policy, permissions table, Data safety source `docs/play/PLAY_DATA_SAFETY.md`, hosted privacy pages); S0014 P2, P3, P6,
  P7 and decisions 3 and 8 (forbidden terms, listing source, demo capture, console fields, contact `sza@ukr.net`);
  S0030 (names: `app_name` "StreamsPlayer", no "mobile", Play hides "for Android").
- `gradle/libs.versions.toml` platform levels: compile 37, target 36, minSdk phone 26, wear 28, watch face 36 (Wear OS 6).
- `tools/release/pre-release-sweep.ps1` (S0010 dry run: drift check, compliance, leak check, release builds, `apksigner`, version
  pair, written PASS/FAIL verdict) and `tools/ci/build-packages.ps1`.
- Canon: the `release` and `store-publish` skills, `RELEASE_AND_DISTRIBUTION`, `SECURITY_AND_PRIVACY` (permissions, data safety,
  IARC), invariants 1 to 6, 12 to 15.

## 1. Goal

A person searching the Play Store on a phone or a Wear OS watch finds StreamsPlayer, reads a listing in English, Russian and
Ukrainian that says what the shipped build does, installs it, and later updates it, with the policy, the permissions and the
Data safety form saying the same thing.

### Baseline - read 2026-10-26

| What | Read how | Value |
| --- | --- | --- |
| Listing `com.sza.streamsplayer` | `curl -L https://play.google.com/store/apps/details?id=com.sza.streamsplayer` | 404 |
| Listing `com.sza.streamsplayer.watchface` | same | 404 |
| Console account, type, verification, payment, drafts | the owner's console | **not readable from this repository** - input D2 |
| Hosted privacy URL | `curl -L https://serzhyale.github.io/StreamsPlayer_Android/privacy.html` | 404 (no repository, S0046 part A) |
| Packages built so far | S0010 | signed **APKs** of five variants; no App Bundle built |
| Keystore | S0010 | a dedicated RSA 4096 keystore generated "for the dry-run release test" in `P:\ANDROID\StreamsPlayer_credentials\keystores\`; whether it is the permanent app signing key is input D1 |
| Listing source | `ls docs/play` | `PLAY_DATA_SAFETY.md` only; titles, descriptions, screenshots, graphics, "What's new": none (S0014 P3) |
| Content rating (IARC) | repository | no questionnaire answers recorded |
| Declared channel | `.sza-canon.json` | `["play"]` (S0033) |
| Release queue | `PLAN/RELEASE_QUEUE.md` | `current-next-release: 1`; package 1 holds S0036, S0035, S0033, S0025 and Drafts; the owner's order |
| Platform levels | `gradle/libs.versions.toml` | target 36; Play's required target level on the upload day is read in the console, not assumed |

## 2. Scope

Tags: **A** agent alone, **A·GO** agent after the owner's go naming the step, **A→O** agent drives and the owner acts at one
point, **O** owner only.

### Part A - prepare in the tree

- **A1 Store bundles - A.** The `store` variant as `.aab` for `phone`, `wear` and `watchface` (the build path for bundles is
  added to the harness-built release flow and `tools/ci/build-packages.ps1` if it is not there), each with the stamped
  `-Psp.versionName` / `-Psp.versionCode` pair of its family, one module per invocation. Signed with the **upload** key only
  when the owner has configured it (`keystore.properties`); otherwise unsigned, never with the debug key (`CLAUDE.md` section 5).
  Validated with `bundletool validate`; the manifest's id, version pair, `minSdk` and `targetSdk` read back and compared with the plan.
- **A2 Notices and native checks - A.** `THIRD-PARTY-NOTICES.txt` reachable inside each package that bundles third-party
  binaries (invariant 12; `tools/third-party-notices/`). If any package carries native libraries, their alignment is checked
  against Play's current page-size requirement; the requirement is read on the day, not recalled.
- **A3 Pre-flight with the written verdict - A.** `pre-release-sweep.ps1` extended to bundles; the verdict file names the
  version it judged; a FAIL or a missing verdict blocks the upload (invariant 5). Includes the port-ledger drift check, the
  compliance check with `play`, the permissions-table check (`tools/privacy/permissions.ps1`) and the privacy page check
  (`tools/site/build-site.ps1 -Check`).
- **A4 Reach record - A.** Per module: minSdk, ABIs, device classes, target countries (input D4), age rating. Written before the
  upload so every later release compares against it and none shrinks reach (invariant 2). The watch face's floor (Wear OS 6, API
  36) is stated as the reach it has.
- **A5 Listing source - A.** Under `docs/play/` in EN, RU, UK: title (name plus at most a category word), short and full
  description, "What's new", per form factor the screenshots (phone, Wear OS, watch face) taken by the capture rig, the icon and
  the feature graphic, the contact `sza@ukr.net`, the website and the privacy URL. Derived from S0014's `POSITIONING.md`; the
  text is a shipped fact (invariant 17); no competitor name, no piracy-signal term, no other app named, no ranking, price or
  promo wording (store metadata policy, re-read on the day). Character and size limits are read from the console at submit time.
- **A6 Declarations source - A.** A record in `docs/play/` of every answer the console's App content forms take, with the reason
  and the file it comes from: privacy policy URL, ads (none: no ad SDK, S0014 criterion 9), app access (input D3), a **fresh**
  content-rating questionnaire for this app's real exposure (live streams of third parties, camera and microphone broadcast,
  links to outside servers) - never a copied portfolio rating id (invariant 15) - target audience, Data safety (from
  `PLAY_DATA_SAFETY.md`), account deletion (not applicable: the app creates no account, S0033), and each permission or foreground
  service declaration the merged manifests trigger (microphone, camera, media playback and the others in the generated
  permissions table), each with the purpose sentence of `docs/privacy/permission-purposes.json` and, where the console asks for
  one, a short screen recording from the capture rig.
- **A7 Handoff package for the console - A.** One checklist in `temp/S0047/` giving the owner the exact values in console order,
  so the console is filled from the repository's sources and never decided inside the console.

### Part B - the console (A→O: the owner signs in, accepts terms and presses submit; the agent drives and prepares)

- **B1 App entries - A→O.** The phone and Wear OS app under `com.sza.streamsplayer` (two form-factor releases of one listing) and
  the watch face under `com.sza.streamsplayer.watchface` as its own listing, with the console shape confirmed on screen (input D6).
- **B2 Signing - O.** Play App Signing enrolment: the product's own key imported as the app signing key and a separate upload key
  (`CLAUDE.md` section 3). Key material never passes through the agent's output or the repository.
- **B3 Store settings and App content - A→O.** Category, contact, countries (input D4), price free (input D8), the forms of A6,
  Data safety from `PLAY_DATA_SAFETY.md` re-read against the console's current form on the day.
- **B4 Listings - A→O.** The three languages from A5, pasted from the source; no hand edit in the console that is not first in
  the source.

### Part C - test tracks and proof

- **C1 Internal testing - A→O.** Upload to the internal track; install from Play on a real phone and a real Wear OS watch.
- **C2 Update proof - A.** A second upload with a higher version pair over the first install: the app keeps its data, no crash
  in `logcat` (invariant 6, an update over a real prior install, not only a fresh install).
- **C3 Closed testing if the account requires it - O.** Read the console's production-access requirements for the account type on
  the day (new personal accounts may need a closed test with a minimum number of testers for a minimum number of days
  [unverified here]); start early, it can dominate the calendar. Internal testing does not count toward it.
- **C4 Pre-launch report - A.** Read the console's automated report for the uploaded build; record each finding as fixed,
  ticketed or accepted with a reason.

### Part D - production (O)

- **D-1 Go.** The owner's exact request for this release and this version (invariant 4), quoted in a go-ledger row.
- **D-2 Rollout.** Per input D5; the owner submits for review.
- **D-3 Landed.** After approval: the listing URL answers 200 in `hl=en`, `hl=ru`, `hl=uk`; a device installs the production
  build from Play; the Data safety page shows the answers of the source; the version on the device equals the verdict's.
- **D-4 Hand-over.** The live URL to S0045 (the get-it block, D3 there) and S0048 (the family map row and every request); the
  release recorded through the harness (`spec_catalog/release-queue -Ship -Release 1 -DryRun`, then for real on the owner's go).

### 2.1 Non-goals

- No upload, submission, promotion to production or tag by the agent without the owner's exact go.
- No second app signing key, no change to an application id (frozen anchors).
- No in-app purchase, no ad, no analytics or crash-reporting SDK (S0014 criterion 9).
- No paid promotion, no listing text written to qualify for a catalogue or ranking.
- No feature advertised that the uploaded build lacks; Drive announcements (S0029) and end-to-end encryption (S0032) are in the
  listing and the form only if the uploaded build carries them (S0033 item 9).
- The `noLegal` sideload packages and GitHub Releases: S0046 part B.

## 3. Constraints and dependencies

### 3.1 Constraints

- The policy, the permissions table, the in-app notices and the Data safety answers say the same thing (invariant 14); a change
  to what the build stores or sends changes all of them in one edit.
- The product ships EN, RU and UK in the same edit on every surface (invariant 17); the other interface languages show English.
- Store and sideload builds carry one signature; the key stays outside the repository (invariant 3).
- The listing claims only what the `store` variant does.

### 3.2 Related tickets

S0010 (pipeline, decision В39), S0033 (policy, Data safety, hosted pages), S0014 (P2 to P3, P6, P7; decisions 3 and 8), S0035 and
S0036 (help and UI contracts the screenshots show), S0025 (measured quality, owner's package-1 call), S0034 part A (the joint run
that proves exchange claims), S0029 and S0032 (included only if the build carries them), S0046 part A (the privacy URL must answer
first), S0045 (landing after this listing answers), S0048 (links after it answers).

### 3.3 Owner inputs (Approval gate)

- **D1 The app signing key.** Frozen once uploaded (invariant 1). Options: (a) **the existing keystore from S0010 becomes the app
  signing key and is imported, with a separate upload key generated** (recommended only after a verified off-machine backup of the
  keystore and its passwords exists), (b) generate a fresh production keystore and import that one. The agent never reads the
  passwords.
- **D2 The console account.** Which developer account, its type (personal or organization), identity verification and fee state,
  and whether production access requires a closed test. The owner reads these from the console and tells the agent.
- **D3 App access for the reviewer.** The catalog and local playback need no account; the exchange server screens need a server
  the reviewer does not have. Options: (a) **declare that the main function works without an account and give the reviewer a
  connection string or guest invitation for an exchange server the owner runs, valid during review** (recommended),
  (b) declare the functions without a login only. Needs an owner-run server reachable during review.
- **D4 Countries.** Recommended: every country the console offers; the reach record of A4 states the choice. A narrower choice is
  recorded as a decision, not an accident.
- **D5 Rollout.** Options: (a) **internal, then closed if required, then production at a staged percentage, then 100%**
  (recommended for a first release), (b) straight to 100%.
- **D6 Console shape.** Confirm on screen that phone and Wear OS ride one listing as two form-factor releases and that the watch
  face is its own listing with its own forms.
- **D7 Which tickets close before package 1 ships.** The owner's package-1 call (`RELEASE_QUEUE.md`): S0036, S0035, S0025 and the
  Drafts are scheduled there; this ticket does not decide the content of package 1, it only blocks the upload on the verdict.
- **D8 Price.** Free, no in-app products, no ads. Recorded as irreversible: Play does not turn a free app into a paid one.
- Settled and not asked again: Play first (В39), application ids and the one-signature rule (`CLAUDE.md` section 3), the contact
  (S0014 decision 8), the name "StreamsPlayer" on Play (S0030).

### Quiz decisions (2026-11-13)

Asked by the side session that repeats DeviceInfo's public presence for this product (owner's order "Повтори всё выполненное
для новго Streams_mob"), before any console step:

- One-way console steps -> yes, all: create `com.sza.streamsplayer` (App, Free), accept the Developer Program Policies, the US
  export laws declaration and the IARC terms of use.
- D3 App access -> (a) part of the app is restricted: the instructions are written by the agent; the login and password of a
  review account on the owner's exchange server are typed by the owner, never by the agent.
- D6 Console shape -> the watch face `com.sza.streamsplayer.watchface` is created now as its own app, with its declarations
  ("no data collected") and an empty listing until its listing source exists.
- D8 Price -> Free (covered by the one-way yes above).
- Target audience -> 13 and over (13-15, 16-17, 18 and over); no group under 13, so the Families policy does not apply.
  Superseded the same day for the main app: once the IARC questionnaire showed the catalog's third-party content (the
  `Adult` rubric, violence and language in uncurated channels), the owner chose the honest answers with 18 and over only
  for `com.sza.streamsplayer`; the watch face keeps 13 and over (owner, 2026-11-13). Record: `docs/play/PLAY_DATA_SAFETY.md`
  "Console record".
- Content rating -> a fresh IARC questionnaire answered by the facts: users exchange audio and video (the live broadcast) -
  yes; content from the internet that the developer does not make - yes; everything else no. The answers and the resulting
  ratings are recorded in `docs/play/PLAY_DATA_SAFETY.md`.
- Store graphics -> a renderer `tools/store/render-store-assets.ps1` draws the 512 icon and the 1024x500 feature graphic from
  the launcher glyph, as render targets (the DeviceInfo shape).

## 4. Acceptance

- [ ] 1. A PASS verdict file in `temp/S0047/` names the `versionName` and `versionCode` of each module, produced by the sweep
  before the upload; the upload step refuses without it (invariant 5).
- [ ] 2. For each `.aab`: `bundletool validate` exits 0; the manifest read back shows the expected id, version pair, `minSdk` and
  `targetSdk`; the signing certificate SHA-256 equals the upload key's recorded fingerprint, never the debug key.
- [ ] 3. The reach record exists and the first release is its baseline (invariant 2).
- [ ] 4. `tools/privacy/permissions.ps1` and `tools/site/build-site.ps1 -Check` exit 0 on the uploaded build's tree, and
  `PLAY_DATA_SAFETY.md` was read against the console's current Data safety form on the submit day, the result recorded.
- [ ] 5. The console's Publishing overview shows no incomplete App content item for either listing (the owner's screenshot in
  `temp/S0047/`).
- [ ] 6. The listing texts in the console equal `docs/play/` for EN, RU, UK (read in the owner's browser, compared field by field,
  recorded); the tense grep of S0045 item 5 returns nothing on the source.
- [ ] 7. The content-rating answers are recorded in `docs/play/` with the date and the resulting ratings; the questionnaire was
  filed for this app (no copied rating id).
- [ ] 8. Internal-track install on a real phone and a real Wear OS watch, and an update over that install with data kept and no
  crash, recorded with the device models and the `logcat` excerpt (invariant 6).
- [ ] 9. The owner's written go for the production release is quoted in a go-ledger row naming the version (invariant 4).
- [ ] 10. After approval: `curl -s -o /dev/null -w "%{http_code}" -L` returns 200 on the phone listing (and the watch face listing
  if shipped) for `hl=en`, `hl=ru`, `hl=uk`; a device installs from Play and shows the verdict's version.
- [ ] 11. `.sza-canon.json` still declares `play` and the canon compliance check exits 0 (SZA-SURF01 satisfied by the hosted
  privacy page).
- [ ] 12. The live URL is recorded in S0045 and S0048; no tracker or analytics SDK entered the build
  (`gradle/libs.versions.toml` and every module's `build.gradle.kts` carry none).

## 5. Risks

- **One-way anchors**: the application id, the app signing key and "free" cannot be taken back; D1 and D8 are asked, not defaulted.
- **Review on third-party streams**: an app that opens public stream addresses and a catalog of stations draws a content and
  copyright review (S0014 section 7); the owner may be asked to show rights or a source of the catalog. No `iptv` or channel-brand
  word in any field.
- **Permissions and foreground services**: microphone, camera and the foreground service types need their declarations and, where
  asked, a recording; a gap rejects the upload or the release.
- **The reviewer cannot reach an exchange server** (D3): without credentials the exchange features are untested and the listing
  can be refused for broken functionality.
- **Wear OS and watch-face listing rules are their own** and the watch face needs Wear OS 6 (S0014 section 7).
- **Account prerequisites** (closed test length, identity verification) can set the calendar; found in D2, not at the end.
- **Target level, page size and policy changes** between today and the upload day: read on the day.
- **Two variants**: a listing claim true of `store` and false of `noLegal`, or the reverse; the listing claims `store` only.

## 6. Open questions

1. D1 to D8 above.
2. Whether `tools/ci/build-packages.ps1` and the release plugin already produce bundles (read at the start of A1).
3. Whether the watch face ships in package 1 or later (the owner's package call; its listing is independent).
