# S0062 - evidence: Google Play policy announcement of 2026-10-29

Read and measured 2026-11-08 by the unattended queue pass. The pages were read through a fetch tool that summarizes the page, not
as raw text: every date below is the one that tool returned, and the owner confirms the account-level ones (section 4) in the
console. Where two pages disagreed, the disagreement is named.

## 1. Sources read

| Page | What it settles |
| --- | --- |
| `support.google.com/googleplay/android-developer/answer/18100766` | the announcement: new and updated policies, clarifications, reminders; "at least 30 days" from 2026-10-29 |
| `support.google.com/googleplay/android-developer/table/12921780` | the Policy Deadlines table: the exact date of each item (the authority for dates) |
| `support.google.com/googleplay/android-developer/answer/16909972` | the policy preview text: local network, manage contacts, location, AppFunctions |
| `developer.android.com/privacy-and-security/local-network-permission` | what `ACCESS_LOCAL_NETWORK` covers and when it starts to apply |
| `support.google.com/googleplay/android-developer/answer/17492799` | app quality: memory and DEX optimization thresholds, February 2027 |
| `support.google.com/googleplay/android-developer/answer/17594752` | trader status declaration |

Two disagreements read: the announcement says the new policies take effect after "at least 30 days", while the Policy Deadlines table
dates them 2027-01-27, 2027-04-14 and 2027-08-25 - the table is the one used. The policy preview summarizes Manage Contacts as
needing a declaration from API 38, while its full text names no level; it does not matter here because the product does not use the
permission.

## 2. Each item of the announcement, classed

Class: **requirement** (binds an app that does the thing), **clarification** (enforcement unchanged), **reminder** (not a policy
change). Applies: whether it binds this product, by evidence in section 3.

| Item | Class | Date (table) | Applies to StreamsPlayer | Why |
| --- | --- | --- | --- | --- |
| Local Network permission | requirement | 2027-08-25 | not now; at target 37 | the app targets 36, where access is implicit; at 37 it needs `ACCESS_LOCAL_NETWORK` and a declaration - parked as S0066 |
| Manage Contacts permission | requirement | 2027-08-25 | no | no contacts permission or API in any module (3.1) |
| AppFunctions API | requirement | 2027-04-14 | no | no AppFunctions in any module (3.1) |
| Subscriptions with eligibility-based discounts | updated requirement | 2027-01-27 | no | no billing library, no purchase or subscription (3.1) |
| Location permissions | clarification (dated) | 2027-01-27 | no | no location permission, API or library (3.1) |
| All files access | clarification | none | no | no `MANAGE_EXTERNAL_STORAGE` (3.1) |
| Accessibility API | clarification | none | no | no accessibility service (3.1) |
| Deceptive behavior (review process) | clarification | none | no change | nothing in the tree manipulates a review; any app access or reviewer note given in the console must be true |
| Families self-certified ads SDK | clarification | none | no | no ads SDK in any module (3.2) |
| Cryptocurrency | clarification | none | no | not a crypto app |
| SMS or call log permission groups | clarification (dated) | 2027-01-27 | no | no SMS or call log permission (3.1) |
| Payments, AI and agentic purchases | clarification | none | no | the app sells nothing (3.1) |
| Play Age Signals | reminder | none | optional | not required; listed in S0067 |
| Japan payments authorization | reminder | 2026-12-01 | expected no | only for a developer that sells into or out of Japan; owner confirms - S0067 |
| Trader status | reminder (console requirement) | 2027-02-01 (existing accounts) | yes - owner | every active developer account declares - S0067 |
| App quality: DEX optimization | reminder | 2027-02 | not enforced at this size | DEX is below 10 MB (3.3) - reading after the first upload, S0067 |
| App quality: memory thresholds | reminder | 2027-02 | after first upload | measured by Android vitals from real users, mobile and tablet only - S0067 |
| Games for cars, Android XR | reminder | none | no | not a game; no XR or car form factor |

Two older rows of the same deadlines table share the 2027-01-27 date and were checked too: geofencing as a foreground service use
case (the product has no location access; its foreground service types are `mediaPlayback`, `microphone` and `camera|microphone`) and
phone-call account verification through `READ_CALL_LOG` (no such permission).

## 3. Inventory and measurements

### 3.1 Permissions and APIs - commands run 2026-11-08

`grep -rEn '<policy list>' --include=*.kt --include=*.java --include=*.xml --include=*.kts --include=*.toml --exclude-dir=build
phone/src wear/src watchface/src exchange-core/src build-logic gradle <the four module build scripts> settings.gradle.kts`
- the list: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `ACCESS_LOCAL_NETWORK`,
`NEARBY_WIFI_DEVICES`, `READ_CONTACTS`, `WRITE_CONTACTS`, `MANAGE_CONTACTS`, `MANAGE_EXTERNAL_STORAGE`, `READ_CALL_LOG`,
`READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, `BIND_ACCESSIBILITY_SERVICE`, `isAccessibilityTool`, `androidx.appfunctions`,
`com.android.billingclient`, `play-services-location`, `LocationManager`, `FusedLocationProviderClient`, `ContactsContract`,
`AccessibilityService`.
- expected: no match | actual: no match, exit 1 (grep's no-match status).
- The search is not blind: the same file set returns the two `android.permission.RECORD_AUDIO` declarations (phone, wear).

Source manifests (`phone/src/{main,store,noLegal,debug}`, `wear/src/main`, `watchface/src/main`) declare, per `uses-permission`
line: `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE` (watch), `WAKE_LOCK`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_CAMERA` (the `noLegal` variant),
`RECORD_AUDIO`, `CAMERA` (phone). `RECEIVE_BOOT_COMPLETED` appears once as a `tools:node="remove"` marker, not a declaration. The
watch face declares none and has no code (`hasCode="false"`).

The last merged store-release manifests (built 2026-10-28 and 2026-10-29) list the same set and add only the product's own
signature permission. They are older than the current sources, so the proof for the shipped set is the release's
`tools/privacy/permissions.ps1 -Build` (RELEASE item in the audit).

### 3.2 SDKs

Phone dependencies read from `phone/build.gradle.kts`: AndroidX (core, appcompat, lifecycle, room, work, datastore, camera), media3
(exoplayer, hls, dash, rtsp, session, ui), OkHttp, Gson, Timber, Hilt, `play-services-wearable`, `play-services-auth`, NanoHTTPD,
ZXing core, RootEncoder, rtsp-server, `exchange-core`. No advertising, analytics, billing, location or contacts library.

### 3.3 App quality figures

- `isMinifyEnabled = true` and `isShrinkResources = true` in the `release` type of `phone` and `wear`; the watch face minifies to
  remove the generated `R` class only and has no code. Keep rules are narrow named classes, no `-dontobfuscate`, `-dontoptimize`
  or `-dontshrink` in the phone or watch rules.
- DEX of the last store release APKs (2026-10-29): phone `classes.dex` 5,719,864 bytes; wear `classes.dex` 5,561,928 bytes - one DEX
  each, both under the 10 MB floor below which the page does not enforce the 25 percent requirement. The bundle explorer's
  percentages exist only after an upload (S0067).
- Memory thresholds: the page measures real users through Android vitals (mobile and tablet, Android 13 and later, devices with 4 GB
  of RAM or more); nothing can be read before the app has users. The tree's own sustained-playback sampling is S0025 (B-4).

### 3.4 Where the product touches the local network

Phone: `BroadcastHttpServer` (NanoHTTPD bound to `0.0.0.0`), `RtspServerCamera2` and `RtspAuthGate`, `LanAddressResolver`. Watch:
`StreamNetworkHoldManager` (a Wi-Fi network held while a broadcast is served). Playback opens any stream address the user types. This
is why the permission will be needed at target 37 and why a picker cannot replace it; the detail is in S0066.

## 4. Owner actions and deadlines

All in S0067, none is code: trader status declaration (2027-02-01 for existing accounts; the status is the owner's own declaration);
Japan payments authorization (2026-12-01, expected not to apply); the console's app registration requirement at app creation; the
two app quality readings after the first upload; Play Age Signals (optional). The local network declaration is part of S0066, on the
day the target moves to 37; its policy date is 2027-08-25.

## 5. Parked from this ticket

- S0066 `target-sdk-37-local-network-access` - the code and console work for the local network permission.
- S0067 `play-console-account-actions-2026-11` - the owner-only console items above.
