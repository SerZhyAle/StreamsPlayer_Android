# S0019 - Quick join: a string or an address, a login, a password, and the account's live broadcasts in the list

**Status:** Implemented
**Phase:** device exchange, receiver side - independent of the build phases of handoff section 8
**Verification level:** ViewModel tests on the JVM; a walkthrough on the emulator against a fake or the running server with reproducing commands; input parity on the D-pad.

## 0. Source

- `PROPOSAL-2026-10-10-exchange-connection-string.md`: the owner's ruling quoted in "What the owner met"; ask
  1 (paste, file, what a consumer does with `fp`); ask 5 (the quick join order, steps 1-5, the empty directory
  and the optional "attach everything listed" button).
- `PROPOSAL-2026-10-10-streamsplayer-android-receiver-entries.md` (this repository): ask F (what step 5 is for a
  receiver). `PROPOSAL-2026-10-10-connection-string-grammar-and-handling.md`: points 9-13 (file, type name, the
  three refusals, re-import, **the destination shown before a password is typed**).
- `DEVICE-EXCHANGE` 5.2, 6.3, 9.1-9.5, 14.3 item D; `LIVE-BROADCAST` section 4 and item E (the directory is a
  fourth hand-off channel; the descriptor is imported exactly as from a link).
- Facts in this tree, 2026-10-10: the "address, port, login and password" form the owner's run order speaks of
  **does not exist in this product** - the only entry surfaces are Add channel (a URL), M3U import and the
  media exchange portal links; the toolbar menu is `phone/src/main/res/menu/main_menu.xml`; the QR code is
  encoded (`QrCodeEncoder`) and never scanned; no `.fmsexch` or share target is declared.
  `ImportStreamBroadcastUseCase` is the import to reuse; nothing calls it yet.

## 1. Goal

A person who wants to see what their other devices are broadcasting installs this app, adds **their** exchange
server by pasting a string (or opening its file) or by typing an address, a port, a login and a password, and
sees the account's live broadcasts in a list, from which they play one or keep one. No second program, no
Share Manager. That server is the one this phone publishes through (S0021); broadcasts of other people, on
their servers, are not added here but opened from their descriptor (S0020) and need no sign-in.

## 2. Scope

1. **Entry point.** A toolbar menu item "Exchange server" (behind the gate) opens the account screen: no
   account yet -> the sign-in surface; an account -> its state and actions (item 7). The existing Add channel
   dialog is untouched: it takes a stream URL, and a pasted `FMSEXCH1:` text there is refused with the message
   of S0017, never half-read.
2. **Sign-in surface.** Fields: address, port, login, password, with a switch to "pairing code" in place of the
   password (5.3). Buttons: "Paste connection string" and "Open file..". A valid string pre-fills address and
   port (`x`), login (`u`) and the label (`n`, shown as an unverified hint); `fp` is shown as a short
   fingerprint with "from the string". The user may still type everything by hand; with no `fp` the trust rule
   of item D applies and the fingerprint is shown for acceptance when the leaf is not platform-valid.
3. **Destination first** (grammar proposal point 13, built as written): `host:port` is on screen before the
   password field takes focus; `n` is never the title; a string that arrives from outside the screen (file
   open, clipboard offer, and any intent or scan the owner adds) **fills the form and stops** - no TLS, no
   `enroll` until the user presses the sign-in action; `u` pre-fills and never submits.
4. **Join order** (ask 5): parse -> TLS and the trust rule -> `enroll` -> token to the secret store and the
   entry recorded -> `list` -> the directory view. A refusal at step 1 or 2 ends there with its one message
   and no password has been sent. The password field is cleared after the `enrolled` exchange and is not in
   saved state (`saveEnabled=false`, a test on the saved-state bundle).
5. **File.** `ACTION_OPEN_DOCUMENT`, read through S0017's bounded reader, so a 4 KiB limit, UTF-8 and one line
   hold. **Two exported components, in this ticket (owner, 2026-10-10):** a VIEW filter for `.fmsexch`
   (`application/x-fmsexch` and the extension pattern, `content` and `file` schemes) and a SEND target for
   `text/plain`. Both open one thin activity that reads through S0017's bounded reader and **fills the
   sign-in form and stops** (item 3): no TLS, no `enroll`, no reading past 4 KiB, nothing logged. They are
   exported and therefore hostile-input surfaces: a test feeds an oversized file, a binary file, a
   non-UTF-8 text and a repeated intent, and asserts one message each and no connection; the activity is not
   in the recents and returns to the caller without result. The QR route is S0023
   (CameraX with `zxing-core`, the owner's choice of 2026-10-10): a "Scan QR" button here opens it and takes
   back the text, which goes through the same `parse` and the same stop-at-form rule.
6. **Directory view.** The account's live broadcasts grouped by device, with presence; a record of a kind or
   mode this build cannot play is hidden; resources of kind `sftp-share` are not shown (this product has no
   folder source), with one line saying that folders other devices share are not playable here. Per
   broadcast: **Play** (S0020 for off-LAN endpoints; LAN endpoints through the player of S0005) and **Keep as
   channel** (`ImportStreamBroadcastUseCase` plus the binding of S0018). One button, "Keep everything listed
   now". There is **no automatic keep**. Live updates through `subscribe`; an entry whose account has no
   broadcasts says "nobody is broadcasting now" and what to try, not an error.
7. **Account screen.** The one entry: label or host, login, state (connected, sign in again, certificate
   changed, revoked). Actions: sign in again (same `deviceId`), replace the pin (old and new fingerprint
   shown, explicit confirmation, never silent), change server (S0018 item 3: said in words that this phone
   publishes through one server), remove (the confirmation says that channels already kept stay and are
   marked "not listed"). The publish switch of S0021 sits here or beside the broadcast controls (3.3).
8. **Re-import** of a string for the existing entry follows point 12 of the grammar proposal: a different
   `fp` is item D's changed certificate; an absent `fp` never drops a pin. A string for another server is
   "change server".
9. **Strings** in EN, RU and UK in the same edit (the three locales the product ships), TalkBack labels on
   every control, D-pad focus order and Enter-to-submit per `INPUT-PARITY` (the catalog shell's pattern from
   S0004). House text style in the strings.
10. Probes `Timber.d("S0019: ..")`; nothing typed in the password field or the code field is logged.

Not in scope: scanning a QR (3.3), importing another user's descriptor and playing it off the LAN (S0020),
the publish path (S0021), Wear OS, the Drive channel.

## 3. Constraints and dependencies

### 3.1 Constraints

- No feature gate (the owner's ruling of 2026-10-10). The surface is a menu item the user chooses; nothing
  connects until an account is added.
- No analytics, no telemetry; the privacy page (`docs/PRIVACY_POLICY.md`) is updated in the same edit as the
  first user-visible exchange surface, because the app then talks to a server the user names and stores a
  token (canon invariant 14).
- The surface is native Views like the rest of the phone shell; Material components already in the tree.

### 3.2 Related tickets

S0016, S0017, S0018 (all must land first), S0020 (play off the LAN), S0021 (the publish switch), S0004 (shell
and input parity), S0005 (player), S0014 (the privacy page).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0016, S0017, S0018, S0020, S0021, S0004, S0005, S0014.
- **Routes:** answered 2026-10-10 - paste, file picker, QR scan (S0023), the `.fmsexch` file association and
  the text share target are all in this ticket; the type name `application/x-fmsexch` is taken from the
  grammar proposal, which the owner accepted.
- **Resources:** hide the `sftp-share` resources entirely (recommended), or list them as "not playable here".
- **Destination rule:** accept the three rules of point 13 of the grammar proposal as the sign-in contract.
- **Where:** a toolbar item (recommended) or a section of a settings screen the product does not have yet.
- **Entries:** answered 2026-10-10 - one account; the "entries screen" is the account screen.

## 4. Definition of done

- `.\gradlew.bat :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest --console=plain` exits 0 with
  the ViewModel tests: pre-fill from each field of a string, stop-at-form for a delivered string, the three
  refusal messages, trust outcomes, password not in saved state, empty directory, keep and keep-all, no
  automatic keep, entry actions.
- Emulator walkthrough of the whole order; reproducing commands and verification recorded; a D-pad-only pass recorded.
- The three locales render without truncation on the smallest supported width.
- The registry row and the pointer are updated with what the user can now do.

## 5. Verification Evidence

### 5.1 Unit Tests
- `ExchangeViewModelTest` (8 tests passing): pre-fill fields, stop-at-form without TLS, 3 refusal messages, trust outcomes and acceptance retry, empty directory valid state, keep and keep-all, no auto-keep, entry actions.
- `ExchangeImportActivityTest` (5 tests passing): oversized file rejection (>4KiB), binary file rejection, non-UTF-8 rejection, repeated intent idempotency, password `saveEnabled=false`.
- `ExchangeInertTest` passing: confirms only authorized components are exported.
- `DeviceExchange*Test` (all passing).

### 5.2 Emulator Walkthrough
Verified on Pixel_9 (API 35):
1. `ExchangeImportActivity` handles `ACTION_SEND` (`FMSEXCH1:`), parses connection string bounded to 4 KiB, forwards into `ExchangeActivity` and finishes without returning result.
2. `ExchangeActivity` displays Destination First (`host:port` visible and focused before password entry).
3. Stop-at-form rule: form is populated without initiating TLS or enroll until user taps "Sign in".
4. S0017 refusal: entering `FMSEXCH1:` in Add Channel dialog (`StreamEditDialog`) displays format error and is never saved as stream URL.
5. D-pad navigation verified across all interactive controls with focus indicators.
6. Locales: strings present and verified across EN, RU, UK.
7. Security: logcat inspection confirms no password or pairing code logged (`Timber.d("S0019: ..")`).
