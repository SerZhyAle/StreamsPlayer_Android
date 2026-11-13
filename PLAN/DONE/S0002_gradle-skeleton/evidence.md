# S0002 evidence - verdict-bearing extracts (2026-10-02)

Extracted from the raw logs of the run; the commands that reproduce each block are in section 6 of
`../S0002_gradle-skeleton.md`. Screenshots in this folder: `phone-store.png`, `phone-nolegal.png` (the
phone app, edition and version on screen), `wear-store.png` (the watch app).

## Clean checkout build (a copy holding the tracked and unignored files plus local.properties)

```
[streamsplayer] :phone 2.61.0021.604 (261002160) from the stamped
[streamsplayer] :watchface 2.61.0021.604 (261002160) from the stamped
[streamsplayer] :wear 2.61.0021.604 (261002166) from the stamped
BUILD SUCCESSFUL in 1m 33s
484 actionable tasks: 484 executed
CLEAN_BUILD_EXIT=0
```

## Build logic unit tests (same copy)

```
BUILD SUCCESSFUL in 4s
CLEAN_BUILDLOGIC_EXIT=0
testsuite name="com.sza.streamsplayer.buildlogic.ReleaseSigningTest" tests="7" skipped="0" failures="0" errors="0"
testsuite name="com.sza.streamsplayer.buildlogic.VersionStampTest" tests="17" skipped="0" failures="0" errors="0"
```

## Unit tests of the modules (same copy)

```
phone TEST-com.sza.streamsplayer.distribution.NoLegalDistributionTest: tests="1" skipped="0" failures="0" errors="0"
phone TEST-com.sza.streamsplayer.distribution.StoreDistributionTest: tests="1" skipped="0" failures="0" errors="0"
wear TEST-com.sza.streamsplayer.wear.distribution.NoLegalDistributionTest: tests="1" skipped="0" failures="0" errors="0"
wear TEST-com.sza.streamsplayer.wear.distribution.StoreDistributionTest: tests="1" skipped="0" failures="0" errors="0"
```

## Packages produced (same copy)

```
phone/build/outputs/apk/noLegal/debug/phone-noLegal-debug.apk
phone/build/outputs/apk/noLegal/release/phone-noLegal-release-unsigned.apk
phone/build/outputs/apk/store/debug/phone-store-debug.apk
phone/build/outputs/apk/store/release/phone-store-release-unsigned.apk
watchface/build/outputs/apk/debug/watchface-debug.apk
watchface/build/outputs/apk/release/watchface-release-unsigned.apk
wear/build/outputs/apk/noLegal/debug/wear-noLegal-debug.apk
wear/build/outputs/apk/noLegal/release/wear-noLegal-release-unsigned.apk
wear/build/outputs/apk/store/debug/wear-store-debug.apk
wear/build/outputs/apk/store/release/wear-store-release-unsigned.apk
```

## Device readback, `dumpsys package` (fresh run at the end of the ticket; the release builds signed with the debug key into temp/ are what is installed now, same version numbers as the debug installs read back earlier)

```
## phone emulator (emulator-5554, API 35)
    flags=0x0
  Package [com.sza.streamsplayer] (3a0385d):
    versionCode=261002155 minSdk=26 targetSdk=36
    versionName=2.61.0021.551
    flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]
## wear emulator (emulator-5556, API 37)
    flags=0x0
  Package [com.sza.streamsplayer] (380c004):
    versionCode=261002159 minSdk=28 targetSdk=36
    versionName=2.61.0021.551
    flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]
  Package [com.sza.streamsplayer.watchface] (fc965c4):
    versionCode=261002155 minSdk=33 targetSdk=36
    versionName=2.61.0021.551
    flags=[ ALLOW_CLEAR_USER_DATA ]
```

## Pinned pair, `aapt2 dump badging` of `:phone:assembleStoreDebug -Psp.versionName=2.60.9262.322 -Psp.versionCode=260926232`

```
package: name='com.sza.streamsplayer' versionCode='260926232' versionName='2.60.9262.322'
```

## Port ledger self-test

```
  ok    an absent ledger is in step
  ok    an empty ledger is in step
  ok    port B
  ok    port A (backslashes are normalised)
  ok    two rows, ordered by source
  ok    the recorded hash is the SHA-256 of the source bytes
  ok    LF line endings, no BOM
  ok    the same pair twice is refused
  ok    one destination, one source
  ok    a destination that is not there is refused
  ok    a path that leaves its root is refused
  ok    in step right after the port
  ok    a changed source is drift (exit 1, listed)
  ok    json report counts
  ok    a one-word note is not a decision
  ok    an unchanged file has nothing to decide
  ok    a written decision is accepted
  ok    a decided change is not uncarried, and its note is shown
  ok    a second change is drift again
  ok    carry is recorded
  ok    the row keeps portedOn, gains carriedOn, loses the old decision
  ok    in step after the carry
  ok    a removed source is reported
  ok    a removal can be decided
  ok    a decided removal is not uncarried
  ok    a missing destination is reported
  ok    a missing source root is exit 2, not a green
  ok    a malformed ledger is exit 2, not a green
test-port-ledger: all checks passed
```
