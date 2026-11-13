# S0002 - Gradle skeleton: three modules, two variants, version stamping, port ledger

**Status:** Verified
**Status note:** Last Audit 2026-10-10 (static): Verified - 12 PASS, 3 EXEMPT, 0 WARN/FAIL/MANUAL. Journal path and evidence folder repaired to PLAN/DONE. PACKAGE-VERSIONING bands folded at 0.3 (contract now 0.5); pointer/registry sync is S0013's. Release-key line stays with S0010; the S0002 commit awaits the owner's ask.
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

- Modules: phone, wear, watch face (resource-only Watch Face Format). Directory names fixed here as `phone`,
  `wear`, `watchface`, plus `build-logic` (the included build of convention plugins: versions, signing,
  variants); the lock path rules in `.sza-profile.json` and `probes` are updated in the same change.
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
- `.sza-canon.json` keeps `channels: []`. The `play` channel is NOT declared here (supervisor addendum of
  2026-10-02, amending the earlier line of this scope): a declared store channel raises SZA-SURF01 (a hosted
  privacy page is required), owner decision 4.7 is open, and the zero-data carve-out is not honest for a
  product with microphone and camera broadcast. The declaration moves to S0010 together with decision 4.7.
- Signing: the key is the owner's and `P:\ANDROID\StreamsPlayer_credentials` does not exist yet. Nothing is
  generated or created under that path, no passwords are chosen. Everything is built and installed with the
  debug signature first; the owner is asked once, in question-with-options form, right before the acceptance
  line "release builds signed from the out-of-repo key".
- `.gitattributes`: the FastMediaSorter-only vector line is replaced with this repository's path patterns,
  so vectors ported later keep their bytes and the port ledger SHA-256 stays valid.

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

- [x] All modules and both variants build from a clean checkout; exit codes cited (section 6).
- [x] Each package installs; `applicationId`, versionName and versionCode read back from the device (section 6).
- [~] Release builds signed from the out-of-repo key; no signing material tracked (`git ls-files` clean).
  **Moved to S0010 by the owner (2026-10-02, asked at the gate: "close S0002 without the key").** The build
  side is done and proven without a key (unsigned release, never the debug key; the signing config reads
  `keystore.properties` or `STREAMSPLAYER_KEYSTORE_PROPERTIES`; unit-tested). The "nothing tracked" half
  holds: no key, keystore, password or token among the tracked and unignored files.
- [x] Port ledger and drift-check script written, self-tested and run green on the empty ledger (section 6);
  "committed" lands with the S0002 commit.
- [x] PACKAGE-VERSIONING amendment diff sent to the canon session (`sza-unified-rules-dc`, 2026-10-02). The
  canon session received it, verified the diff against the live 0.2 file and the arithmetic against this
  repository's code and the devices, and filed it as the catalog proposal
  `PROPOSAL-2026-10-02-streamsplayer-android-bands.md` (catalog RULES.md section 7: another product's contract
  is amended by a proposal beside it, folded in by its owner). **The contract stays 0.2 until that fold:**
  `docs/contracts/PACKAGE-VERSIONING.md` is not moved to 0.3 and the registry row is not touched from here.

## 5. Decisions taken inside this ticket

Tactical choices made while building the skeleton; each is cheap to revisit before the first release.

- **Build logic is an included build** (`build-logic/convention`), not a script applied three times. One place
  holds the platform levels (from the version catalog), the derived version, the signing config and the
  `store` / `noLegal` flavors, so the three modules cannot drift to three answers. Its unit tests are the
  PACKAGE-VERSIONING conformance ladder, rung 2 (fixed-instant vectors from the contract's own example).
- **Version sources, in order:** a pinned pair (`-Psp.versionName` + `-Psp.versionCode`, both or neither, the
  code checked against the family's partition and the name against the grammar), the in-build stamp (an
  invocation that packages reads the clock once, shared by all modules), the sentinel (instant `2610020000`,
  rendered by the same function, so it cannot drift). A pin names one family's code, so a release builds one
  module per invocation; the code check applies only to the modules the invocation builds.
- **Bands:** phone `floor(minute / 10)` (0..5), watch `6 + floor(minute / 15)` (6..9), watch face
  `floor(minute / 10)` (0..5, own namespace) - the proposal sent to the canon session.
- **Debug builds carry the frozen ids**, with no `applicationIdSuffix` and no `-DEBUG` name suffix: the
  versionName stays a pure timestamp (rule 2) and the id read back from a device is the real one. A debug
  install and a release install therefore cannot update each other (different signature); uninstall first.
- **Release without a key is unsigned**, never signed with the debug key; a signed release is only ever the
  out-of-repo key's.
- **Watch face minimum:** Watch Face Format v1, `minSdk 33`. The composition (decision 4.4, S0008) may raise
  it before the first release, never after. The face is a plain time on black: the empty face.
- **Stand-in artwork** (launcher icon, face preview) is a plain play triangle / clock; the product artwork
  arrives with S0004 and S0008.
- **Localization:** the one skeleton string exists in EN, RU and UK from the start; the product name is not
  translated.

## 6. Evidence (2026-10-02)

Commands run from the repository root with the Gradle wrapper (9.4.1, AGP 9.2.1, Kotlin 2.2.10, JDK 21);
verdict-bearing extracts and the screenshots are kept in `PLAN/DONE/S0002_gradle-skeleton/` (`evidence.md`).

| Check | Command | Result |
| --- | --- | --- |
| Clean checkout, every module, both variants, debug and release, unit tests | `./gradlew assembleDebug assembleRelease testStoreDebugUnitTest testNoLegalDebugUnitTest` in a copy holding only the tracked and unignored files plus the machine-local `local.properties` | exit 0, BUILD SUCCESSFUL, 484 tasks executed, 10 APKs; 4 unit tests, 0 failures |
| Build logic | `./gradlew -p build-logic :convention:test` (same copy) | exit 0; 24 tests (17 version stamp, 7 signing), 0 failures |
| Port ledger | `pwsh -NoProfile -File tools/port-ledger/test-port-ledger.ps1` | exit 0, 28 checks; it caught one real bug (an empty row lookup unrolled to `$null`) before this passed |
| Drift check on the empty ledger | `pwsh -NoProfile -File tools/port-ledger/check-drift.ps1` | exit 0, "OK: nothing uncarried (0 in step, 0 decided)" |
| Ticket store | `spec_catalog/validate`, `spec_catalog/release-queue -Validate` | 9 OK 0 WARN 0 FAIL; no drift |

Installs, read back from the device with `dumpsys package` (Android emulator API 35 for the phone, Wear OS
emulator API 37 for the watch and the face), all from one stamped invocation `2.61.0021.551`:

| Package | applicationId | versionName | versionCode | minSdk / targetSdk |
| --- | --- | --- | --- | --- |
| phone, store debug, then noLegal debug installed over it (same id, same signature) | `com.sza.streamsplayer` | `2.61.0021.551` | `261002155` | 26 / 36 |
| watch app, store debug | `com.sza.streamsplayer` | `2.61.0021.551` | `261002159` | 28 / 36 |
| watch face, debug | `com.sza.streamsplayer.watchface` | `2.61.0021.551` | `261002155` | 33 / 36 |

- The phone and the watch app launch; the screen names the edition (`store`, then `noLegal`) and the version,
  so the per-variant DI binding is proven end to end. No crash in logcat.
- Release packages (R8 on): built unsigned; signed locally with the debug key into `temp/` only to prove
  they install and launch - phone and watch app start, the face installs with no code (`HAS_CODE` absent).
- The pin: `-Psp.versionName=2.60.9262.322 -Psp.versionCode=260926232` on `:phone:assembleStoreDebug` reads
  back `2.60.9262.322` / `260926232` (the contract's example); a one-sided pin, and the phone's code pinned
  onto `:wear`, are refused at configuration with a message naming the cause.
- Bands: phone `...5`, watch app `...9`, face `...5` at the same minute; the watch app never repeats the
  phone's code under the shared id.
- Known cosmetic: the status bar clock of the stand-in phone screen is light on a light background. The UI is
  S0004's; not chased here.

## 7. Owner answers at the gate (2026-10-02)

- **Signing line:** the owner chose to close S0002 without the key. The line moved to S0010 (its acceptance
  list); the key stays the owner's to create.
- **Commit:** the owner asked not to commit yet. Nothing is committed from this ticket. The S0001 "Repeat
  audit" section (uncommitted since the previous session, accurate as history, not reverted) stays in the
  working tree; when the owner asks for the commit, whether it goes in the same commit or its own is asked
  again, and the owner's instruction is named in the commit message.

## Last Audit

**Date:** 2026-10-10 Europe/Malta | **Mode:** static | **Outcome:** Verified
**Counts:** PASS 12 | WARN 0 | FAIL 0 | MANUAL 0 | UNCHECKABLE 0 | EXEMPT 3

### Fixes

- Corrected this ticket's journal path to the existing `PLAN/DONE/` spec through
  `tools/harness.ps1 spec_catalog/update -Id S0002 -File PLAN/DONE/S0002_gradle-skeleton.md`
  (exit 0, `Implemented -> Implemented`). The same repair was applied to S0003-S0006; the store
  re-validated green afterwards (`Summary: 9 OK, 0 WARN, 0 FAIL`).
- Moved the evidence folder beside the spec: `PLAN/S0002_gradle-skeleton/` ->
  `PLAN/DONE/S0002_gradle-skeleton/`, citation in section 6 updated. The evidence files were not
  modified (`evidence.md` 4.6 KB, screenshots 8-29 KB).

### Evidence

1. **PASS - build line.** `./gradlew assembleDebug` (fresh run, 2026-10-10): exit 0, `BUILD SUCCESSFUL`,
   209 actionable tasks; phone, wear and watchface all packaged. The recorded clean-checkout run of
   2026-10-02 stands: exit 0, 484 tasks, 10 APKs across both variants (`evidence.md`). A fresh
   clean-checkout copy build was not re-run by this audit.
2. **PASS - build logic.** `./gradlew -p build-logic :convention:test --rerun`: exit 0; 24 tests,
   0 failures (17 VersionStampTest, 7 ReleaseSigningTest) - the PACKAGE-VERSIONING conformance ladder.
3. **PASS - variant DI.** `./gradlew :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest
   :wear:testStoreDebugUnitTest :wear:testNoLegalDebugUnitTest --tests "*Distribution*"`: exit 0;
   4 tests, 0 failures - the per-variant DI binding proven for phone and wear, store and noLegal.
4. **PASS - version stamping and bands.** `aapt2 dump badging` of the three fresh debug APKs
   (2026-10-10): one clock reading `2.61.0101.148` on all three; phone `versionCode 261010114`
   (band 0..5), watch `261010119` (band 6..9, disjoint under the shared id), face `261010114`
   (own namespace) - the PACKAGE-VERSIONING rule 3 rows as folded at 0.3.
5. **PASS - release without a key.** `./gradlew :watchface:assembleRelease`: exit 0, output
   `watchface-release-unsigned.apk` - unsigned, never the debug key. `git ls-files` shows no key,
   keystore, password or token; `keystore.properties` is gitignored.
6. **PASS - port ledger and drift.** `pwsh -NoProfile -File tools/port-ledger/test-port-ledger.ps1`:
   exit 0, all checks. `pwsh -NoProfile -File tools/port-ledger/check-drift.ps1`: exit 0,
   `OK: nothing uncarried (665 in step, 0 decided)`.
7. **PASS - frozen anchors.** `applicationId` in the three build files: phone and wear
   `com.sza.streamsplayer` (identical, Data Layer requirement), face
   `com.sza.streamsplayer.watchface`; debug carries no suffix - the readback of 2026-10-02 confirms
   the real ids on device (`evidence.md`).
8. **PASS - deferred store channel.** `.sza-canon.json` keeps `channels: []`; the `play` declaration
   moves to S0010 with owner decision 4.7, as the ticket's scope decision states.
9. **PASS - repo mechanics.** `.gitattributes` carries this repository's vector/binary patterns
   (ported vectors keep their bytes, so ledger SHA-256 rows stay valid); `settings.gradle.kts`
   includes `phone`, `wear`, `watchface` and the `build-logic` included build; variant source sets
   `store` / `noLegal` with per-variant test sets are in place.
10. **PASS - probe invariant.** `grep -rn "S0002" phone/src wear/src watchface/src build-logic`: one
    resource comment in `watchface.xml` naming the stand-in face's provenance; no probe log line.
    The ticket is not in a needs-manual-test status, so no probe may exist.
11. **PASS - install and readback record.** `dumpsys package` blocks for the three packages recorded
    2026-10-02 (API 35 phone, API 37 Wear emulators): ids, `versionName 2.61.0021.551`, codes
    `261002155` / `261002159` / `261002155`, minSdk/targetSdk 26/36, 28/36, 33/36; screenshots in
    this folder. Not re-run by this audit; corroborated today by the S0007 emulator session, which
    installed and launched both modules from this tree.
12. **PASS - ticket store.** `tools/harness.ps1 spec_catalog/validate`: exit 0, `9 OK, 0 WARN, 0 FAIL`;
    `spec_catalog/release-queue -Validate`: exit 0, `no drift`. The closing gates
    (`check-headings-unique`, `check-evidence-durable`, `check-open-items-carried`,
    `check-probe-absent`) each returned `PASS S0002`.

### Scope and documentation

- **EXEMPT - release signing from the out-of-repo key.** Owner decision 2026-10-02 moved the
  acceptance line to S0010. The build side is proven here: unsigned release without a key, the
  signing config reads `keystore.properties` or `STREAMSPLAYER_KEYSTORE_PROPERTIES`, unit-tested.
- **EXEMPT - the S0002 commit.** The owner asked to wait (section 7); committing stays the owner's
  call. The port ledger's "committed" half of acceptance line 4 lands with that commit.
- **EXEMPT - contract pointer sync.** The bands proposal was folded by the catalog owner at
  PACKAGE-VERSIONING 0.3 (2026-10-07; the contract is now 0.5), so the "contract stays 0.2" line of
  the original status note is history. Moving `docs/contracts/PACKAGE-VERSIONING.md` and the registry
  rows to the current version is S0013's Definition of done; this audit verified only that the build
  implements the folded bands.

### Action items

None within S0002. The journal status note and the section 6 citation were refreshed by this audit;
no manual or device checks remain open.
