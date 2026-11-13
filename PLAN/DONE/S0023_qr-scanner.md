# S0023 - A QR scanner: CameraX and zxing-core, text out, nothing connects

**Status:** BlockNeedUserTest
**Status note:** Scanner and descriptor import route implemented; 37 scanner tests pass per variant, two real virtual-scene camera tests pass, both release APKs assemble. Physical-phone autofocus, low light and audible TalkBack remain unverified. Full suites retain the two tracked failures of S0027/S0028.
**Phase:** device exchange, hand-off routes - independent of the build phases of handoff section 8
**Verification level:** unit tests on the JVM for decoding; the emulator's virtual scene for the camera path; a physical phone for focus and low light.

## 0. Source

- Owner, 2026-10-10 (this session): the scanner is CameraX with `zxing-core`.
- `LIVE-BROADCAST` 4.1 (the barcode hand-off channel: the code carries the description); the connection string
  proposal, ask 1 ("QR: the payload is the text above, byte for byte").
- `docs/contracts/LIVE-BROADCAST.md` (the listener role is second wave; this is one of its routes).
- Facts in this tree, 2026-10-10: `zxing-core` 3.5.3 is a dependency and is used to **encode**
  (`QrCodeEncoder`, `BroadcastQrFullscreenManager`); there is no decoder and no `androidx.camera` entry in
  `gradle/libs.versions.toml` (the broadcast camera and `cameracapture` use the platform camera API);
  `CAMERA` is declared and requested for the broadcast; the privacy page and the permission justification
  mention the broadcast only.

## 1. Goal

One screen that points the camera at a QR code and returns the text it carries to whoever asked - the
connection string route of S0019 or the descriptor importer of S0020 - and does nothing else with it.

## 2. Scope

1. **Contract.** An `ActivityResultContract<Unit, ScanResult>`: `Text(String)`, `Cancelled`, `Denied`,
   `NoCamera`. The scanner knows nothing about strings, descriptors or the exchange.
2. **Camera.** CameraX preview and an image-analysis use case at a modest resolution, back lens, one frame in
   flight, closed with the screen; no capture, no recording, no frame stored or sent anywhere.
3. **Decode.** `zxing-core` `MultiFormatReader` restricted to `QR_CODE`, fed from the luminance plane of each
   analysed frame (rotation applied); a result is accepted when it decodes twice in a row (a misread
   guard); text longer than 4096 characters is refused as "not one of ours".
4. **Nothing connects, nothing opens.** The result is returned as text. A scanned URL is never opened in a
   browser, a scanned `FMSEXCH1:` fills the sign-in form and stops (S0019 item 3), a scanned descriptor goes
   through the importer's validation (S0020 item 1).
5. **Permission.** `CAMERA` is requested at the moment of scanning, with a one-sentence justification shown
   first (invariant 13), EN/RU/UK; refusal leaves the paste and file routes in place with a message that says
   so; "never ask again" offers the system settings. The existing broadcast justification is **not** reused
   for a different use: the permission list and the store data-safety form say "also used to read a QR code"
   (invariant 14).
6. **Accessibility and input.** A TalkBack description of the viewfinder, an announced result, a visible
   "Paste instead" action, a D-pad-reachable close button, Back cancels.
7. **Third-party notices.** CameraX (Apache-2.0) is added to the packaged `THIRD-PARTY-NOTICES.txt` through
   `tools/third-party-notices` (invariant 12); the version is pinned in `gradle/libs.versions.toml` and is at
   least two weeks old.
8. Probes `Timber.d("S0023: ..")`; the scanned text is never logged, only its length and the outcome.

Not in scope: scanning from a gallery image, a flashlight toggle, any other barcode format, the producer's
QR (already exists), the exchange itself.

## 3. Constraints and dependencies

### 3.1 Constraints

- Same code for the `store` and `noLegal` variants; no Play services dependency.
- The minimum platform level does not rise and no ABI is dropped (invariant 2); the APK size delta is
  measured and recorded.
- No new permission: `CAMERA` is declared already; only its justification and the privacy statements move.

### 3.2 Related tickets

S0017 (the text consumer), S0019 (connection string route), S0020 (descriptor route), S0014 (privacy and
listing texts), S0010 (release pipeline, notices).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0017, S0019, S0020, S0014, S0010.
- **Privacy wording:** the listing and the privacy page gain "reads a QR code with the camera; frames are
  neither stored nor sent". Confirm the wording at S0014.
- **Physical phone:** one, for focus and low light.

## 4. Definition of done

- `.\gradlew.bat :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest --console=plain` exits 0 with the
  tests: decode round trip of the app's own `QrCodeEncoder` output at the sizes the broadcast screen draws,
  rotation, the double-read guard, the length bound, outcome mapping, permission flows.
- Emulator virtual-scene run decoding a descriptor QR and a connection-string QR, recorded in `PLAN/S0023_qr-scanner/virtual_scene_run.txt`.
- `tools/third-party-notices` regenerated and the packaged notices list CameraX; the merged manifest shows no new permission; APK size delta cited (+183.3 KiB / +187,740 bytes storeRelease: 4,747,708 -> 4,935,448 bytes).

## 5. Completion plan, 2026-10-23

Rung: complex, because the existing implementation needs changes across the scanner, its importer route,
resources and lifecycle tests. Shared-contract formats and consumer validation remain unchanged; the scanner
returns text only. No catalog amendment is needed.

| Step | Coverage and files | Produces / consumes | Static exit check |
| --- | --- | --- | --- |
| 1 | Consecutive-frame guard, bound/refusal, luminance buffer handling, lifecycle cancellation and explicit 640x480 latest-frame analysis in `ui/scanner`; regression tests | Consumes the existing ZXing encoder and CameraX pin; produces bounded text and cancellable sessions | Scanner JVM tests pass in store and noLegal |
| 2 | Descriptor import button in `MainActivity` and `dialog_import_broadcast`, original text on paste, permission outcomes, settings and fallback strings EN/RU/UK | Consumes step 1's contract; produces the existing importer's input without opening URLs or signing in | Activity tests pass; debug APK builds; emulator scans return exact text |
| 3 | Real virtual-scene instrumented tests, generated notices, privacy wording, changelog and evidence | Consumes both routes and built APKs; produces acceptance evidence and the remaining physical-device checklist | Notice generator exits 0; packaged notices/manifest checked; camera fixture tests pass |

Coverage review: scope 1-8 maps to steps 1-3; variant/platform/ABI reach is checked in the built manifests.
Gallery, torch, other barcode formats, exchange transport and producer changes remain out of scope.
The physical-phone focus/low-light check remains manual. The previous APK-size comparison in section 4
is historical evidence from 2026-10-11, not a fresh measurement of this completion pass.

Documentation registry: `docs/README.md` has no scanner-specific record. Updated sources are the EN/RU/UK
app strings, `docs/PRIVACY_POLICY.md`, `CHANGELOG.md` and this ticket. `README.md` already describes the
QR import route. No product site, help-page implementation or Play listing/data-safety source exists in
this tree; S0014 owns store wording when that surface is created. Notices are generator-owned.

## 6. Evidence, 2026-10-23

- Targeted command: `.\gradlew.bat :phone:testStoreDebugUnitTest --tests 'com.sza.streamsplayer.ui.scanner.*' :phone:testNoLegalDebugUnitTest --tests 'com.sza.streamsplayer.ui.scanner.*' :phone:assembleStoreDebug :phone:assembleStoreDebugAndroidTest --continue --console=plain`.
  Exit **0**, `BUILD SUCCESSFUL in 1m 16s`; **37 scanner tests per variant, zero failures**.
  Expected: decoder round trips/rotations, strict consecutive reads, 4096-character bound, outcomes,
  permission grant/refusal/settings, cancellation and late callbacks, padded luminance and closed frames.
  Actual: all pass. Log: `temp/S0023/targeted-tests.log`; copied XML: `temp/S0023/targeted-*-results/`.
- Camera: `QrScannerVirtualSceneTest#scanConnectionString` and `#scanDescriptor`, each with its matching
  `s0023VirtualScene` argument. Each `adb shell am instrument -w -r ..` exited **0**, reported
  `INSTRUMENTATION_STATUS_CODE: 0` and `OK (1 test)`; exact returned text asserted, lengths 45 and 105.
  The real import dialog launched the camera; its descriptor returned through validation and created a
  channel without playback. That synthetic channel was deleted afterwards. Full recipe and output:
  `PLAN/S0023_qr-scanner/virtual_scene_run.txt`. The old JVM-only camera report and its test-generated
  dated claims were removed.
- Full suites plus release assembly: `.\gradlew.bat :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest :phone:assembleStoreRelease :phone:assembleNoLegalRelease --continue --console=plain`.
  Exit **1**, `BUILD FAILED in 7m 20s`: **1003 store / 1005 noLegal tests, two failures each**.
  Both release assembly tasks completed. The failures are the already tracked
  `ExchangeSignInOnlyTest.firstSignInStoresTokenUnderTheEnrolledDeviceAndNeverRegisters` (S0027,
  timeout) and `KeystoreBroadcastPasswordStoreTest.password is encrypted absent after key loss and
  removed with the channel` (S0028, DataStore I/O). No scanner failure. They remain with their owning
  tickets; this is not a green full-suite claim. Log: `temp/S0023/full-verification.log`.
- Isolated release assembly: `.\gradlew.bat :phone:assembleStoreRelease :phone:assembleNoLegalRelease --console=plain`,
  exit **0**, `BUILD SUCCESSFUL in 4m 10s`, both APKs produced with R8/resource shrinking and vital lint.
  Log: `temp/S0023/release-build.log`.
- Notices: the README's Gradle inventory command exited **0**, `BUILD SUCCESSFUL in 5s`;
  `python tools/third-party-notices/generate.py` exited **0**, phone **141 components / 11 embedded
  notices / 658,410 bytes**, wear **142 / 10 / 657,237**. The generated phone asset contains CameraX
  1.5.3 and its embedded Apache-2.0 license; POMs without metadata retain the generator's honest fallback.
- Packaged checks: `python temp/S0023/check_artifacts.py`, exit **0**. Expected: generated notices
  byte-identical inside both APKs, scanner private, camera optional, minSdk 26, permissions equal the
  existing common/flavor manifests plus AndroidX's internal signature permission. Actual: PASS for
  both; native ABI set **arm64-v8a, armeabi-v7a, x86, x86_64**. The existing noLegal camera-broadcast
  foreground-service permission belongs to that variant; the scanner adds none.
- Size measurement of the first fresh release build: store **5,101,664 bytes**, noLegal **5,101,484**.
  The pre-completion store APK observed on disk was **5,166,580 bytes**: this completion pass is
  **-64,916 bytes (-63.4 KiB)** against that local package. This is a local package comparison, not an
  isolated CameraX dependency measurement; section 4 retains the historical original dependency delta.
- CameraX remains pinned to **1.5.3**, released **2026-01-28**, satisfying the two-week minimum on the
  verification date ([AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/camera#1.5.3)).
- Locale parity check: exit **0**, all **14** `qr_scanner_*` keys match across EN/RU/UK.
  Catalog validation: exit **0**, `8 OK, 1 WARN, 0 FAIL`; the warning concerns existing catalog paths
  outside S0023, not this spec.

| Surface | Authority | Locales | Disposition |
| --- | --- | --- | --- |
| In-app scanner/import | Source | EN/RU/UK | Added descriptor scan action, localized rationale and fallbacks |
| Privacy policy / permission table | Source | EN | Clarified local QR frames versus transmitted broadcast video |
| Changelog | Source | EN | Added QR import capability |
| README | Source | EN | Existing QR import description remains accurate |
| Packaged notices | Generated | Upstream English notices | Regenerated from resolved runtime inventory |
| Product site, Play listing/data safety, help pages | None in this tree | N/A | No local surface to regenerate; future store disclosure remains with S0014 |

## Last Audit

**Date:** 2026-10-23 | **Mode:** re-audit | **Outcome:** Partial
**Counts:** PASS 11 | WARN 1 | FAIL 0 | MANUAL 1 | UNCHECKABLE 0 | EXEMPT 0

PASS: result contract, luminance/rotation decoding, consecutive reads and bound, frame cleanup,
pending-session cancellation, permission outcomes, locale parity/accessibility attributes, importer
hand-off, real virtual-scene decoding, packaged notices/reach and measured package sizes.
WARN: full phone suites retain the two failures owned by S0027/S0028. No out-of-scope fix was attempted.
The scanner contains only text-return logic; URLs are never launched and exchange sign-in remains
the consumer's explicit action. Probes log outcome/length only and remain for the manual-test status.

### Manual / on-target

- [ ] On a physical phone, scan the connection and descriptor QR codes with autofocus and in low light;
  confirm TalkBack describes the viewfinder and announces the result, and Close/Back cancels.
  This remains a device check, so S0023 is not Verified.
