# S0018 - The exchange account entry: its `deviceId`, the token in the Keystore, an imported channel bound to its entry

**Status:** Implemented
**Phase:** device exchange, receiver side - independent of the build phases of handoff section 8
**Verification level:** unit tests on the JVM with a fake cipher; one instrumented test on the emulator for the real `AndroidKeyStore`; a Room migration test with the exported schema.

## 0. Source

- `PROPOSAL-2026-10-10-exchange-connection-string.md`, ask 3 (any number of entries, `{endpoint, login, pin,
  deviceToken, deviceId}`, the `deviceId` once per entry, an attached resource bound to `(entry, resourceId)`,
  removing an entry never deletes what was attached through it) and ask 5 step 4 (token to the secret store,
  `{x, fp-or-pin, login, deviceId}` recorded).
- `PROPOSAL-2026-10-10-streamsplayer-android-receiver-entries.md` (this repository): asks C (an imported
  channel is bound to its entry and `broadcastId`), D (one entry per `(endpoint, login)`), H.
- `DEVICE-EXCHANGE` 2 (the `deviceId`: 16 random bytes in the share id form of `ANYWHERE-ACCESS` 6.2), 5.2 (the
  token lives in the platform secret store, the password never), 9.1-9.3, 14.3 item D (the pin is a property of
  the account entry), 14.5 item G (`id-collision`), `VERSIONING.md` 4.5.
- Facts in this tree, 2026-10-10: `stream_sources` carries `sourceDeviceId` (one row per broadcasting device,
  enforced in `ImportStreamBroadcastUseCase`), `sourceOrigin` MANUAL / IMPORTED / CATALOG and no exchange
  column; the database file declares `version = 1` and `exportSchema = true`; `allowBackup="false"`; no
  `androidx.security` and no Keystore wrapper (`androidx.security:security-crypto` is deprecated upstream, so
  the wrapper is a few lines of `AndroidKeyStore` AES-GCM written here).

## 1. Goal

**Owner, 2026-10-10:** the server this product broadcasts through is one; connecting to other users' broadcasts,
possibly on their servers, is any number and is part of the broadcast's description. So this product holds
**at most one account entry**. The entry model is kept general (it has its own `entryId`, and the `deviceId`,
pin and token belong to it) so that ask 3 of the connection string proposal - several entries - would need no
migration; the product simply allows one. A live channel kept from the account's directory stays the user's own
data when the entry is removed.

## 2. Scope

1. **Entry model.** `ExchangeAccountEntry`: local `entryId`, `endpoint` (`host:port`, host case-folded),
   `login` (lower-cased, 5.1), `pin` (`SHA256:...` or null), `deviceId`, `deviceName`, `label` (the string's `n`,
   an unverified hint), `state` (`Connected`, `NeedsSignIn`, `CertificateChanged`, `Revoked`), `createdAt`. No
   password, no token in this record.
2. **`deviceId` per entry.** Generated once when the entry is created with `SecureRandom`, 16 bytes, the
   share id form; kept for the life of the entry; never reused for another entry (with one entry at a time the
   proposal's two readings, per entry and per installation, coincide; the id is still made per entry so
   a replaced server gets a new device). A re-enrolment of the same entry
   reuses it, so the server rotates the token and nothing collides (7.2).
3. **One entry.** A second sign-in while an entry exists is a *replace*: the user is told that this phone
   publishes through one server, and the old entry is removed by item 8 before the new one is created. The
   same `(endpoint, login)` again re-enrols the entry with its own `deviceId` and updates its label and pin by
   the rules of S0019. Foreign servers named in a descriptor are **not** entries: no record here, no token,
   no `deviceId` (S0020 keeps their trust state with the channel).
4. **`SecretStore` on the Keystore.** AES-256-GCM, a non-exportable key in `AndroidKeyStore`, one key alias per
   installation, the token's ciphertext and IV stored in DataStore or Room beside the entry id. A token that
   cannot be decrypted (key invalidated, data restored where it should not be) moves the entry to
   `NeedsSignIn` and keeps its login, pin and `deviceId`; it is never deleted silently and never retried in a
   loop. `allowBackup="false"` stays and a test reads the merged manifest to prove it.
5. **`EntryStore`** (Room, a new table `exchange_entries`, schema exported, migration tested) implements the seam
   of S0016; an installation with no account has an empty table and opens nothing.
6. **Channels bound to entries.** `stream_sources` gains nullable `exchangeEntryId` and `exchangeBroadcastId`
   (Room migration, exported schema, a migration test with rows from the previous version). The import of a
   broadcast from a directory goes through the existing `ImportStreamBroadcastUseCase` and then writes the
   binding. The `sourceId` replacement rule of `LIVE-BROADCAST` 3.1 and item G is unchanged: one row per
   source, the later record wins.
7. **Absence is not authority to destroy** (9.3, `VERSIONING.md` 4.5). A broadcast that leaves the directory
   marks its bound row "ended"; removing an entry marks every row bound to it "not listed"; neither deletes a
   row, and a row the user created or imported by hand is never touched. Deleting a channel stays the user's
   act. The catalog refresh of S0003 never reads these columns.
8. **Removing an entry.** In this order, each best effort: `bye`, `revoke` its **own** `deviceId` (so the
   account does not collect a dead receiver that casters would offer), forget the token, delete the entry. It
   never depends on the server being reachable: with the server down it forgets locally, says that the old
   device stays in the account's list until another device revokes it, and does not retry in the background.
9. **Keyed by entry anyway.** The session, the control stream and the in-memory directory are keyed by
   `entryId` (S0016), so a second entry would be a setting, not a rewrite. Nothing merges across entries.
10. Probes `Timber.d("S0018: ..")`; ids hashed as in S0016; the canary test of S0016 is extended to the
    entry and secret stores.

Not in scope: the sign-in and management screens (S0019), the connection string (S0017), Wear, the Drive
channel.

## 3. Constraints and dependencies

### 3.1 Constraints

- The Keystore wrapper is a seam (`KeyCipher`); the JVM tests use a fake and prove everything but the platform
  call, which has its one instrumented test. No claim of device security beyond that test.
- No new permission, no dependency beyond what the tree has.
- A schema change before the first release is allowed to follow the database policy S0003 recorded; nothing
  has shipped, but the migration test runs anyway.

### 3.2 Related tickets

S0016 (seams), S0017 (the identity key's inputs), S0019 (screens), S0020 (playback of foreign broadcasts needs
no entry; cast receive does), S0021 (publishing uses this entry), S0003 (the stream store and its schema
policy).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0016, S0017, S0019, S0020, S0021, S0003.
- **One entry:** taken from the owner's answer of 2026-10-10. Confirm the reading: a second account (for
  instance a family member's directory on another server) is **not** wanted; other users' broadcasts from
  other servers arrive as descriptors. If a second account is wanted, it is a setting on this model, not a
  ticket of its own.
- **Revoke on removal:** answered 2026-10-10 - yes, `bye` then `revoke` of its own `deviceId`, best effort
  (item 8); the proposal's ask 4 says it for a producer only, so the entries proposal asks it of every device.
- **The watch:** answered 2026-10-10 - the watch is a device of the same account with its **own** `deviceId`
  and token (S0022); it is not a second entry of this phone, and its token lives on the watch.

## 4. Definition of done
 
 - [x] `.\gradlew.bat :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest --console=plain` exits 0 with the
   new tests: identity and duplicates, `deviceId` stability across re-enrol, removal order with the server up
   and down, undecryptable token -> `NeedsSignIn`, binding and "ended" / "not listed" without deletion,
   catalog refresh leaves bound rows alone, migration with rows.
   - Verified 2026-10-11: `BUILD SUCCESSFUL in 2m 7s` (`:phone:testStoreDebugUnitTest`, 610 tests) and `BUILD SUCCESSFUL in 19s` (`:phone:testNoLegalDebugUnitTest`, 612 tests).
 - [x] The instrumented `AndroidKeyStore` test on the emulator exits 0, command and output cited.
   - Command: `.\gradlew.bat :phone:connectedStoreDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.sza.streamsplayer.data.exchange.security.AndroidKeyStoreCipherTest" --console=plain`
   - Output: `Starting 3 tests on Pixel_9(AVD) - 15` -> `Finished 3 tests on Pixel_9(AVD) - 15` -> `BUILD SUCCESSFUL in 1m 57s`. XML report: `<testsuites tests="3" failures="0" errors="0" skipped="0">` with `encryptAndDecrypt_roundTripSucceeds`, `decryptWithTamperedCiphertext_throwsAEADBadTagException`, and `keyPersistsAcrossCipherInstances`.
 - [x] `schemas/` carries the new exported schema; no hand edit.
   - Exported: `phone/schemas/com.sza.streamsplayer.data.local.db.StreamsDatabase/2.json` generated by Room KSP.
 - [x] The registry row is updated with what was built.

