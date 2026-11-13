# S0010 - Release pipeline and a first release dry-run

**Status:** Implemented
**Status note:** Full release pipeline proven end-to-end; out-of-repo release key configured and verified with apksigner on all 5 packages; update over prior install succeeded on emulator; drift check green (698 in step, 0 uncarried); pre-release sweep passed with written verdict; nothing tagged or pushed.
**Phase:** 10 of 10 (handoff section 8)
**Verification level:** dry-run - full build, pre-release sweep with a written verdict, drift check, no upload and no tag.

## 0. Source

`docs/BOOTSTRAP_HANDOFF.md` section 8 item 10, sections 4.3, 4.5, 4.7, 6a item 2, 7, 9; canon
NEW_PROJECT_CHECKLIST step 9.

## 1. Goal

A release pipeline proven end to end without the one-way step: build every package, run the pre-release
sweep, run the port-ledger drift check, produce the PASS/FAIL verdict the ship step would take - and stop.

## 2. Scope

- Ledger shape chosen here: Keep-a-Changelog (`ledgerShape: 1`) declared in `.sza-canon.json` with root `CHANGELOG.md`.
- Distribution channel: `channels: ["github"]` declared per owner decision 4.3 (GitHub/sideload first before store release).
- Privacy policy: `docs/PRIVACY_POLICY.md` written, declaring zero telemetry, local storage only, and permissions required for streaming and live microphone/camera broadcasting (`sensitiveAccess: true`).
- Out-of-repo release signing key: generated RSA 4096-bit keystore at `P:\ANDROID\StreamsPlayer_credentials\keystores\streamsplayer_release.keystore`, read via gitignored `keystore.properties` or `STREAMSPLAYER_KEYSTORE_PROPERTIES`. No signing material tracked.
- The drift check reports zero uncarried FastMediaSorter changes without a written decision (handoff section 9 item 7).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0002-S0008 (S0009 optional).
- **4.3 Distribution of the first release (answered 2026-10-10):** GitHub / sideload first: release APKs on GitHub first before opening the Play Store channel.
- **4.5 GitHub remote (answered 2026-10-10):** `SerZhyAle/StreamsPlayer_Android` (Public). Rationale: focus on Android TV / big screen and mobile usage ("Потому что не на мобильном а на андроид-телеке будут больше запускать").
- **4.7 Site (answered 2026-10-10):** Join SITE-FAMILY-MAP with a dedicated page and hosted privacy policy.
- **Release signing key setup (answered 2026-10-10):** Dedicated keystore generated in `P:\ANDROID\StreamsPlayer_credentials` for the dry-run release test.

## 4. Acceptance

- [x] Build of every package and variant from a clean checkout, exit codes cited.
- [x] Release builds signed from the out-of-repo key, proven with `apksigner verify` and by updating a real prior install; no signing material tracked.
- [x] Pre-release sweep verdict file naming the version it judged.
- [x] Drift check green, or every uncarried change carries a written decision.
- [x] Nothing tagged, uploaded or published.

## 5. Implementation and Evidence (2026-10-10)

### 5.1 Out-of-Repo Signing Key & Hygiene
- **Credentials path:** `P:\ANDROID\StreamsPlayer_credentials\keystores\streamsplayer_release.keystore` (outside repository).
- **Key details:** RSA 4096-bit key pair, `SHA384withRSA`, validity 10,000 days. Distinguished name: `CN=StreamsPlayer, OU=SerZhyAle, O=SerZhyAle, L=Kyiv, ST=Kyiv, C=UA`.
- **Certificate SHA-256 fingerprint:** `61cf0209340f65d127291faedeee64e998ecc5dfa4b9f04a79e084cb22f1f497`.
- **Git hygiene:** `.gitignore` excludes `*.keystore`, `*.jks`, `keystore.properties`. Confirmed with `git ls-files` that no secrets or keystore files are tracked.

### 5.2 Release Artifacts & Builds
Executed release packaging for all 3 modules and distribution variants:

| Module | Task | Exit Code | Output Artifact | Size (bytes) | SHA-256 (prefix) |
|---|---|---|---|---|---|
| `:phone` | `assembleStoreRelease` | 0 | `phone/build/outputs/apk/store/release/phone-store-release.apk` | 4,712,612 | `37990AB3E48FD55F..` |
| `:phone` | `assembleNoLegalRelease` | 0 | `phone/build/outputs/apk/noLegal/release/phone-noLegal-release.apk` | 4,712,432 | `F8856141D3802A75..` |
| `:wear` | `assembleStoreRelease` | 0 | `wear/build/outputs/apk/store/release/wear-store-release.apk` | 6,443,386 | `91D0A717CF1CC4F5..` |
| `:wear` | `assembleNoLegalRelease` | 0 | `wear/build/outputs/apk/noLegal/release/wear-noLegal-release.apk` | 6,443,386 | `053B18368F2CDCC7..` |
| `:watchface` | `assembleRelease` | 0 | `watchface/build/outputs/apk/release/watchface-release.apk` | 60,139 | `0D04038D2323FB57..` |

### 5.3 Signature Verification with apksigner
Verified all packages using Android SDK `apksigner.bat verify --verbose --print-certs`:
- `watchface-release.apk`: `Verifies` (v2 scheme: true, Signer: `CN=StreamsPlayer`, cert SHA-256: `61cf0209340f65d1..`)
- `wear-store-release.apk`: `Verifies` (v2 scheme: true, Signer: `CN=StreamsPlayer`)
- `wear-noLegal-release.apk`: `Verifies` (v2 scheme: true, Signer: `CN=StreamsPlayer`)
- `phone-store-release.apk`: `Verifies` (v2 scheme: true, Signer: `CN=StreamsPlayer`)
- `phone-noLegal-release.apk`: `Verifies` (v2 scheme: true, Signer: `CN=StreamsPlayer`)

### 5.4 Update Over Prior Install Verification
- **Device:** `emulator-5554` (`sdk_gphone64_x86_64`, Android API 36).
- Clean install: `adb install phone-store-release.apk` -> `Success` (`versionCode=261010130`, `versionName=2.61.0101.304`, signature `[f24f1c73]`).
- In-place update: `adb install -r phone-noLegal-release.apk` -> `Success` (updated smoothly without `INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- App startup: `adb shell am start -n com.sza.streamsplayer/com.sza.streamsplayer.MainActivity` -> process started (pid 9810), in focus `Window{eed5920 u0 com.sza.streamsplayer/com.sza.streamsplayer.MainActivity}`, zero crashes in logcat.

### 5.5 Port Ledger Drift Check
- Ran `tools/port-ledger/check-drift.ps1`:
  `OK: nothing uncarried (698 in step, 0 decided)`. Exit code 0.

### 5.6 Pre-Release Sweep Verdict
- Created automated tool: `tools/release/pre-release-sweep.ps1`.
- Executed sweep: judged version `2.61.0101.300`.
- All checks PASSED (compliance, secrets, ledger drift, 5 signed artifacts).
- Verdict written to `PLAN/S0010_release-pipeline-dry-run/attachments/pre-release-verdict.json` and `PLAN/S0010_release-pipeline-dry-run/attachments/pre-release-verdict.md`.
- **Verdict: PASS**.

### 5.7 Dry-Run Restraint
- No git tags pushed or created (`git tag` clean).
- No git commits pushed to remote.
- No artifacts uploaded or published.

## S4203 decisions 2026-10-14

S4203 is the release-46 device-exchange milestone of FastMediaSorter Android (FMS_A). The owner's answers of
2026-10-14 to its questionnaire are final; the one that reaches this ticket is recorded here.

- **В39 - the first release goes straight to Google Play.** It replaces decision 4.3 of section 3.3 ("GitHub /
  sideload first"). Decisions 4.5 (the GitHub remote) and 4.7 (the site) stand.

What that changes:

- **Sideload developer verification is moot for the first release.** A release that reaches users only through
  Play needs no registration of the package for installs from outside Play; the Play Console account's own
  identity verification is the one that applies. The question comes back the day a sideload channel opens
  (GitHub Releases, or a `noLegal` APK download), and is decided then.
- **Channel declaration.** `.sza-canon.json` still declares `channels` `["github"]` (section 2 above); the first
  store release declares `play`. The stamp's own comment says a declared store channel makes the compliance gate
  SZA-SURF01 require a hosted privacy page - that page, the permissions list and the Play Data safety answers
  are S0033, which flips the declaration in the same change.
- **Store package shape.** The dry run built signed APKs (`assemble*Release`); a Play upload takes App Bundles.
  The `store` bundles of `phone`, `wear` and `watchface` signed with the upload key, and Play App Signing with
  this product's own key imported (`CLAUDE.md` section 3), were not part of the dry run; they are steps of the
  owner's first release run.
- **Variants.** The first release is the `store` variant on Play; the `noLegal` sideload variant has no channel
  until a sideload channel opens.
- **Contracts that stay unbound.** `INSTALL-TRUST` (bound only if a sideload page ships) and `UPDATE-MANIFEST` (a
  sideload self-update client) stay unbound for the first release.
- **Promotion.** S0014's status note still waits on decision 4.3; 4.3 is answered now: the Play listing (S0014
  P7) is the first outward channel and GitHub Releases comes after it. S0014 itself is not edited by this note.
- **Name on Play.** Play hides "for Android", and the word "mobile" is avoided in product names (the product also
  runs on Android TV and VR headsets) - S0030.
- **Status.** The dry run's evidence holds and this ticket stays `Implemented`; the store-release work the
  decision implies is carried by S0033 and by the owner's first release run.
