# S0002 - Gradle skeleton: three modules, two variants, version stamping, port ledger

**Status:** Draft
**Phase:** 2 of 10 (handoff section 8)
**Verification level:** build + install - every module and variant builds, each package installs on a device or
emulator, versionName/versionCode read back from the installed package.

## 0. Source

`docs/BOOTSTRAP_HANDOFF.md` section 8 item 2, sections 3, 4.1, 4.2, 4.6, 5 (tech stack reference), 6a, 7
(PACKAGE-VERSIONING).

## 1. Goal

Empty phone, wear and watch-face apps that build, install and carry their frozen ids, their signing config
and mechanically derived versions - plus the port ledger and drift check, which must exist before the first
ported file lands in S0003.

## 2. Scope

- Modules: phone, wear, watch face (resource-only Watch Face Format). Directory names fixed here; update the
  lock path rules in `.sza-profile.json` and `probes` in the same change.
- Variants: `store` and `noLegal`; interface in `src/main`, implementation in the variant source set, one DI
  module per variant (handoff section 3). The FastMediaSorter flavor matrix is not inherited.
- Toolchain: re-read FastMediaSorter's `gradle/libs.versions.toml` and `docs/TECH_STACK.md` for the current
  pins; minSdk 26 as reference, targetSdk as Play requires at the first release.
- Signing config reading the key path from a gitignored file or an environment variable; no key material in
  the repository.
- Version stamping per PACKAGE-VERSIONING (Android profile), derived mechanically, never hand-bumped.
- Port ledger (committed) and drift-check script, per handoff section 6a item 2: source path in
  FastMediaSorter, source SHA-256 at port time, destination path, port date; the script lists every source
  file changed since its port.
- Add `play` to `.sza-canon.json` `channels` only when a tracked `build.gradle.kts` exists - and note that a
  store channel then requires a hosted privacy page (canon gate SZA-SURF01, see S0010 / decision 4.7).

## 3. Contract work owned by this phase

- PACKAGE-VERSIONING: three new package families in the band table - phone and watch share one application id
  and the noLegal variant shares the base id, so phone and watch need disjoint bands; the watch face has its own
  id. A MINOR amendment, written in the catalog first: send the exact diff to the canon session
  (`sza-unified-rules-dc`), never write it here. Not registered in S0001 by the owner's instruction (waits for
  the frozen anchors, now answered).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0001 (foundation) must be closed first.
- **4.1 Frozen anchors (answered 2026-10-02):** `com.sza.streamsplayer` for phone and watch (one shared id,
  required by the Data Layer); `com.sza.streamsplayer.watchface` for the watch face; the noLegal variant
  shares the base id of the store variant (never co-published to one store). Frozen forever after the first
  shipped package.
- **4.2 Signing key location (answered 2026-10-02):** `P:\ANDROID\StreamsPlayer_credentials`, outside the
  repository, after the `FastMediaSorter_credentials` precedent (keystores, README, a properties template).
- **4.2 Play App Signing (answered 2026-10-02):** Play App Signing with the product's own key imported as the
  app signing key at the first Play upload, and a separate upload key for the AAB. One signature on Play builds
  and sideload APKs, so the variants update each other and the Data Layer pairs phone and watch across them.
- **4.6 License (answered 2026-10-02):** MIT, `Copyright (c) 2026 Serhii Zhyhunenko (SerZhyAle)` - `LICENSE`
  already written in S0001.

## 4. Acceptance

- [ ] All modules and both variants build from a clean checkout; exit codes cited.
- [ ] Each package installs; `applicationId`, versionName and versionCode read back from the device.
- [ ] Release builds signed from the out-of-repo key; no signing material tracked (`git ls-files` clean).
- [ ] Port ledger and drift-check script committed; the script runs green on an empty ledger.
- [ ] PACKAGE-VERSIONING amendment diff sent to the canon session.
