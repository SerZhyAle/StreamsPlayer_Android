# S0029 - Broadcast announcements through Google Drive: read and publish in the user's own app folder

**Status:** BlockNeedUserTest
**Status note:** Development complete (Drive announcements read, publish and remove; 50 new JVM tests green in both variants; emulator smoke without a Google account). Only testing remains: the owner registers the Android OAuth client (docs/DRIVE_OAUTH_CLIENT.md), then the two-way run with a Google account against FastMediaSorter Android and FMS_W.
**Phase:** device exchange, release-46 milestone (FastMediaSorter Android S4203) - independent of the build phases of handoff section 8
**Verification level:** unit tests on the JVM with a fake Drive; a phone with the owner's own Google account against FastMediaSorter Android and FMS_W announcing in the same app folder; the joint runs of S0034.

## 0. Source

- Owner, 2026-10-14: answers to the questionnaire of FastMediaSorter Android (FMS_A) milestone S4203 (В4, В31,
  В32, В35, М2), final:
  - For broadcasts Google Drive is an address book: it carries the broadcast announcement only; the stream
    goes direct or through the exchange server.
  - Own Google account only; other people are reached through the exchange server or a link.
  - The name everywhere is "through Google Drive", short "through GD"; never "relay" in the UI ("relay" stays
    the server fan-out in the contract).
  - The family uses the app folder FastMediaSorter Android already uses for backups and settings; Windows signs
    in through the browser, because Google Drive for Windows cannot see the hidden app folder. The owner asked
    for a detailed step-by-step instruction for registering the OAuth clients, carried out by a developer agent.
  - Drive data may pass through the exchange server (the earlier ban is lifted).
- FMS_A S4218 (`DEVICE-EXCHANGE`: the Google Drive channel) - the text this ticket is written to;
  `DEVICE-EXCHANGE` section 8 (Drive record files, `schemaVersion` 1). The same channel elsewhere: FMS_A S4231
  (files), FMS_W 202 and 203, StreamsPlayer SP-0202.
- New for this product: S0019, S0020 and S0021 each put "the Drive channel" out of scope; nothing here was
  planned for Drive before 2026-10-14.
- Facts in this tree, 2026-10-14: no Google sign-in, no Drive client and no OAuth client for
  `com.sza.streamsplayer`; `play-services-wearable` is the only Google Play services library; the record codec
  of S0016 replays `vectors/records-v1.json`, the corpus that carries the Drive record shape.

## 1. Goal

With nothing but the user's own Google account, this phone sees the broadcasts that the same person's other
devices announce, and announces its own, so that a broadcast can be found without an exchange server and without
typing an address. The stream never goes through Drive.

## 2. Scope

1. **Google account, own only.** Sign in with the user's own Google account for the app-folder scope and no
   other scope (non-sensitive); sign out of Drive is its own action and does not touch the exchange account.
   The screen says "through Google Drive" and what is written there.
2. **The shared app folder.** This app's OAuth client is registered in the same Google Cloud project as the
   family's other clients, so that the app folder it reads is the one FMS_A, FMS_W and StreamsPlayer write: an
   Android client for `com.sza.streamsplayer` with the SHA-1 of the upload key and of the Play app-signing key,
   following the step-by-step registration instruction the owner asked for (FMS_A S4203). Whether different
   OAuth clients of one project see one app folder is the open experiment of FMS_A S4218 (milestone decision 26,
   tried by FMS_W 202 and StreamsPlayer SP-0202); this ticket takes its result and does not start before it.
   A client in a project of its own would see only this app's own announcements, so that setup is not used.
3. **Read.** The announcement files of the account's devices, in the S4218 shape, listed in the broadcast list
   of S0028 as "through Google Drive" with the announcing device's name; opening one plays its endpoints by the
   S0020 order and trust rules; an announcement whose broadcast ended is handled as S4218 says (removed or shown
   as ended), never played as live.
4. **Publish.** A per-broadcast choice, off by default like the server publish of S0021 and remembered per mode:
   on start, the announcement is written with the broadcast's endpoints (LAN, and the server's when S0021
   publishes it); on stop, it is removed; a failed write is shown once and never retried in a loop. No media
   is ever written to Drive.
5. **Words.** "through Google Drive" / "through GD" in EN, RU and UK in the same edit; the word "relay" never
   appears on a Drive surface.
6. **Privacy.** The policy page and the Data safety answers gain the Drive section in the same edit (canon
   invariant 14), in the shape S0033 sets.
7. Probes `Timber.d("S0029: ..")`; no Google token, file id or e-mail address in a log.

Not in scope: Drive as a file channel (this product has no files to exchange), Drive on the watch, another
person's Drive, any Drive scope wider than the app folder.

## 3. Constraints and dependencies

### 3.1 Constraints

- Written to the text FMS_A S4218 folds; not started before that fold. The announcement file is the contract's
  shape, replayed from its vectors, never this product's own variant.
- Variants: the interface lives in `src/main`, the Google implementation in the variant source sets, one DI
  module per variant (`CLAUDE.md` section 3).
- The OAuth client registration and the Cloud project are the owner's accounts; the agent follows the
  instruction and records what it registered, never a secret.

### 3.2 Related tickets

S0016 (record codec), S0020 (playing an announced broadcast), S0021 (the endpoints a published broadcast
announces), S0028 (the list that shows Drive announcements), S0027 (a guest announces nothing), S0030 (words),
S0033 (privacy and Data safety), S0034 (joint runs).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0016, S0020, S0021, S0027, S0028, S0030, S0033, S0034.
- **Channel rules:** answered 2026-10-14 (FMS_A S4203 questionnaire) - section 0.
- **Variants:** Drive announcements in both `store` and `noLegal` (recommended: both already carry Google Play
  services for the Data Layer), or `store` only.

## 4. Definition of done

- `.\gradlew.bat :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest --console=plain` exits 0 with the
  tests: announcement codec against the contract's Drive vectors at the folded version, publish and remove
  lifecycle on a fake Drive, ended announcement not played as live, requested scope equal to the app folder
  only, no token in a log.
- On a phone with the owner's Google account: a broadcast announced by FastMediaSorter Android or FMS_W is
  listed and plays here, and a broadcast announced here is listed there; recorded in `temp/S0029/`.
- The privacy page and the Data safety answers carry the Drive section (S0033's shape).
- The `DEVICE-EXCHANGE` registry row note names the Drive channel for broadcasts.

## 5. Implementation record (2026-10-23)

Started after the premise of `DEVICE-EXCHANGE` 8.2 / item B was recorded as proven (FMS_W, 2026-10-23, an Android client
and a Desktop-app client of the family project) and the contract stood at 1.7 with section 8 and items C, CL, CM, CQ,
CR. This app's own Android client is not yet in the Cloud project (owner action, `docs/DRIVE_OAUTH_CLIENT.md`).

### What exists

- `phone/.../data/exchange/drive/`: `DriveAppFolder` (scope, folder, working numbers), `DriveFilesApi` +
  `HttpDriveFilesApi` (plain OkHttp REST over `appDataFolder`, one 401 retry, Drive's `Date` as the clock),
  `DriveAnnouncementReader`, `DriveBroadcastAnnouncer`, `DriveAuthorizer` (interface) with `GoogleDriveAuthorizer`
  (Play services Authorization API, the one scope), `SharedPrefsDriveChannelStore` (signed-in flag, notice flag,
  this phone's Drive `deviceId`); DI: `DriveModule` (shared) and `StoreDriveModule` / `NoLegalDriveModule`.
- `exchange-core`: `BroadcastExchangePublisher.published` (`PublishedEndpoints`), so the Drive file carries the same
  endpoint list and the same `broadcastId` as the server record when S0021 also publishes.
- UI: a "Through Google Drive" card on the Exchange screen (sign in / sign out, the list of other devices'
  announcements labelled "through Google Drive", works with no exchange account); the "Announce through Google
  Drive" switch beside the server switch in the broadcast settings (off by default, per mode, first-use notice that
  says what is written); one status line on the broadcast screen. Strings in EN, RU and UK (`strings_drive.xml`).
- `docs/PRIVACY_POLICY.md` section 4 (Google Drive), README, CHANGELOG, the `DEVICE-EXCHANGE` pointer,
  `THIRD-PARTY-NOTICES.txt` regenerated for `play-services-auth` 21.1.1.

### Decisions taken here (flag any the owner wants changed)

1. **Variants:** both, the recommended option. The Google implementation is one class in `src/main`; each variant has
   its own DI module, so narrowing Drive to `store` means binding a signed-out authorizer in `NoLegalDriveModule`
   (and `storeImplementation` for the dependency).
2. **Numbers** are the FMS_W working values (broadcast ttl 90 s, device ttl 300 s, rewrite at ttl/3, listing 10 s while
   the list is open, back-off 1 s to 5 min); rung 3 has not frozen them. They live in `DriveAppFolder` only.
3. **"A failed write is shown once and never retried in a loop"** is implemented as: no immediate retry; the next
   attempt is the next scheduled rewrite (30 s, pushed out by the back-off after rate limit or server error); the
   failure line is shown once until a write succeeds again; a failure no retry helps with (signed out, token refused,
   Drive full, daily limit, forbidden) stops the writing for that broadcast.
4. **Ended announcement:** not listed at all (item C, Drive's clock); a reader deletes nothing it did not write.
5. **Device record:** this app also writes its own `fmsx-device-*` file while announcing, because a reader names the
   device from it (item CU says "broadcast records only" - flagged in the registry proposal).
6. **Sign-out** is local (the app stops using Drive; the consent stays in the Google account settings, which the
   confirmation says). `play-services-auth` 21.1.1 has no `revokeAccess`; a later version would add it.
7. **List:** a broadcast also shown by the server's directory is listed once (the server's); the Drive list has no
   "keep as channel" action; a guest of the exchange server sees no Drive switch (S0027).

### Evidence

- `:phone:testStoreDebugUnitTest` and `:phone:testNoLegalDebugUnitTest` with the new tests (50): the announcement
  codec against the `records-v1.json` Drive vectors at 1.7 (`DriveAnnouncementReaderTest`), publish / rewrite / remove
  lifecycle, a crash leftover, a failed write shown once, the server id adopted, on a fake Drive
  (`DriveBroadcastAnnouncerTest`); the REST client against a local server including the log canaries for token, file
  id, name and body (`HttpDriveFilesApiTest`); the scope equals the app folder only and the switch is off by default
  (`DriveChannelScopeAndDefaultsTest`); EN/RU/UK parity and no "relay" (`DriveStringsTest`); the list mapping
  (`DriveDirectoryMapperTest`). Both variants assemble (Hilt graph valid).
- Emulator (Pixel_6 profile, API 33, no Google account): the Exchange screen shows the card; Sign in with Google reaches
  Google's own sign-in screen and cancelling shows "Google sign-in did not complete."; the broadcast settings show the
  switch and the first-use notice with a "Sign in" button; an audio broadcast with the switch on and no sign-in shows
  "Not announced through Google Drive: sign in first" and logs `S0029: drive write failed (NotSignedIn)`. Screenshots
  in `temp/S0029/`.

### Still open

- A phone with the owner's Google account against FastMediaSorter Android and FMS_W (both directions): needs this
  app's Android OAuth client (package `com.sza.streamsplayer`, SHA-1 of the debug / upload / app-signing certificates)
  in the family project - `docs/DRIVE_OAUTH_CLIENT.md`. Record the run in `temp/S0029/`.
- The Data safety answers do not exist yet (S0033); its Drive section is that ticket's to write in S0033's shape, from
  policy section 4.
- The registry row note: exact text in `temp/S0029/REGISTRY-ROW-PROPOSAL.md`, for the catalog session.
- The full phone suites stay red on the two tests of S0027 / S0028 named in S0021 (`ExchangeSignInOnlyTest`,
  `KeystoreBroadcastPasswordStoreTest`); none of this ticket's tests fail.

## Last Audit

**Date:** 2026-10-27 | **Mode:** pre-handoff (static re-read of this spec and its recorded evidence; no new build or test run) | **Outcome:** BlockNeedUserTest
**Counts:** PASS 4 | WARN 1 | FAIL 0 | MANUAL 2 | UNCHECKABLE 0 | EXEMPT 0

1. PASS: scopes 1 and 3-7 are implemented (section 5, "What exists"): own-account sign-in with the one app-folder scope, read, publish and remove, the words, the privacy text and the probes.
2. PASS: 50 new JVM tests in both variants (codec against the Drive vectors, publish and remove lifecycle on a fake Drive, an ended announcement not listed, scope equal to the app folder, switch off by default, log canaries, EN/RU/UK parity); both variants assemble.
3. PASS: emulator smoke without a Google account (the card, sign-in cancel, the first-use notice, "not announced: sign in first").
4. PASS: the Drive section is in the privacy policy and in `docs/play/PLAY_DATA_SAFETY.md` (S0033).
5. WARN: the registry row note awaits the catalog session (`temp/S0029/REGISTRY-ROW-PROPOSAL.md`).

- [ ] MANUAL: the owner registers the Android OAuth client for `com.sza.streamsplayer` (SHA-1 of the debug, upload and app-signing certificates) in the family Cloud project - `docs/DRIVE_OAUTH_CLIENT.md`. Scope 2 (the shared app folder) is this registration, not code.
- [ ] MANUAL: on a phone with the owner's Google account, a broadcast announced by FastMediaSorter Android or FMS_W is listed and plays here, and a broadcast announced here is listed there; record the run in `temp/S0029/`.
