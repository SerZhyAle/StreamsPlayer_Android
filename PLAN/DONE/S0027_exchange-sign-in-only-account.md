# S0027 - The exchange account, sign-in only: first connection, password change, sign-out, guest invitation

**Status:** BlockNeedUserTest
**Status note:** Development done 2026-10-29 (help page, ? and F1; button wrap resolved by S0036 under BUTTONS 2.0). Missing: owner's fresh exchange credentials and a guest invitation for the live walkthrough, ru/uk 320dp re-render, TalkBack, help screenshots; Kotlin not built until the release sweep.
**Phase:** device exchange, release-46 milestone (FastMediaSorter Android S4203) - independent of the build phases of handoff section 8
**Verification level:** ViewModel and session tests on the JVM against a fake server speaking the folded text; an emulator walkthrough against the owner's exchange server; the joint runs of S0034.
**Complexity:** complex (confirmed 2026-10-29): a Room migration, new account operations over the contract boundary, reshaped screen and dialog surfaces.

## Remaining testing

*As of 2026-10-29 development is finished: scopes 1-9 are in code, the in-app help is written, and what is left is testing (`## Last Audit`, manual items).*

1. **Cyrillic connection-string buttons - resolved without asking, 2026-10-29.** The mid-word break of `btnPaste`, `btnOpenFile` and `btnScanQr` recorded on 2026-10-25 (`temp/S0027/WALKTHROUGH.md`, item 4) was taken on the uppercase, fixed three-button row of that day. S0036 slice 5 has since moved the row to `CommandFlowLayout` (`BUTTONS` 2.0: content-width buttons, sentence case, `breakStrategy` balanced, a whole button wraps to the next row before its caption wraps) and dropped the 11 sp override, so the cause is gone. Neither offered way out applies: `BUTTONS` 2.0 section 4.2 forbids automatic vertical stacks of commands, and section 3.4 forbids shrinking a caption to fit; the full ru/uk labels stay. What remains is the re-render at 320 dp in ru and uk (manual item below).
2. **In-app help - done, 2026-10-29** (handed on by S0035 slice 5): the `exchange` page in EN/RU/UK, the `ExchangeActivity` page row and the `dialog_exchange_password` inherit row (anchor `change-password`), both lines out of `tools/help/help-map-baseline.txt`, the `?` in the toolbar and in the dialog title, F1 on both, and a capture recipe for the page's screenshots.

Testing only: the successful sign-in, the directory, sign out and change password against the live server need fresh credentials from the owner (the questionnaire password of 2026-10-14 is refused); a guest invitation on the live server; the TalkBack pass; the help screenshots and the F1 walk. The registry row note for the canon session is prepared in `temp/S0027/registry-row-proposal.md`.

## 0. Source

- Owner, 2026-10-14: answers to the questionnaire of FastMediaSorter Android (FMS_A) milestone S4203 (В3, В5,
  В8, В9, М6-М11 and the chat answers), final, quoted from the milestone's digest:
  - Registration happens in FMS_A and FMS_W only; this product, StreamsPlayer (Windows) and CyrFlip only sign in.
  - First connection is a connection string or a QR plus a login and password prompt; the login may already be
    inside the string or QR; then it is remembered until the owner of the sharing side changes the conditions.
    IP, ports, firewall and router details stay hidden from the user as long as possible.
  - The host resets any account password; a user changes his own password only with the old one; a forgotten
    password means a new account; no e-mail.
  - Changing an account password signs out all its devices, which then sign in again. No automatic rotation of
    passwords or PINs when a device is revoked.
  - No more than one password check per second on the server, no lockout.
  - A device stays in the account until deleted in the server program; "sign out on this device" exists.
  - Two invitation kinds: "own device or family" and "guest". A guest sees only what was given to him and
    announces nothing on the server. One-time invitations always exist, the host may enable a shared invitation
    password, an invitation lives until used.
  - TLS 1.2 minimum; the self-signed server certificate is pinned on first connect; renewal keeps the key.
- FMS_A S4214 (`DEVICE-EXCHANGE`: accounts, invitations, passwords, devices, account deletion) - the contract
  text this ticket is written to. Server side: FMS_W 224. The same work in StreamsPlayer
  (Windows): StreamsPlayer SP-0221.
- Facts in this tree, 2026-10-14: S0016 enrols with a login and a password or a pairing code and keeps the
  device token in the Keystore; S0018 holds one entry with its `deviceId` and the states `Connected`,
  `NeedsSignIn`, `CertificateChanged`, `Revoked`; S0019 fills the sign-in form from a pasted string, a
  `.fmsexch` file, a share or a QR (S0023), shows the destination before the password and stops at the form;
  the account screen offers "Sign in again", "Remove account" and "change server". There is no change-password
  action, no sign-out distinct from removing the account, no invitation of either kind, and no message for a
  device signed out because the password changed.

## 1. Goal

A person given a connection string or a QR by the host of a server signs in once - the login already filled in
when the string carries it - and stays signed in until the host changes the conditions. He can change his own
password with the old one, sign this phone out, and understands, in words and not in network terms, why the app
asks him to sign in again. A guest invitation signs him in as a guest who sees what was given and announces
nothing. The app never offers to create an account.

## 2. Scope

1. **First connection.** The S0019 surface takes everything S4214 puts in a string or a QR (address, the
   login, an invitation where the contract carries one) and stops at the form as today. The remembered sign-in
   (the token) is kept until the server refuses it; the refusal reasons of S4214 (password changed, device
   deleted, invitation withdrawn, account deleted) each map to one message and to the state "sign in again".
2. **No registration.** No surface creates an account; a string or a QR meant for registration is refused with
   one message that names the programs that register (from the S0030 dictionary).
3. **Change password.** On the account screen: old password, new password, confirmation; sent per S4214; on
   success this device signs in again with the new password as the contract orders (every device of the
   account is signed out, this one included); the fields are cleared and never in saved state or a log.
4. **Signed out because the password changed.** A device whose token the server refuses for that reason
   shows "the password of this account was changed - sign in again" and keeps its entry, its pin and its
   `deviceId`; nothing is deleted. The watch, enrolled through the phone (S0022), is signed out as well and is
   offered a fresh pairing from the phone.
5. **Sign out on this device.** A separate action from "Remove account": the device revokes its own
   `deviceId` and forgets the token as S4214 defines; kept channels stay. "Remove account" is re-worded or
   merged so that the two actions never say the same thing.
6. **Guest invitation.** Signing in with a guest invitation per S4214: the entry is marked guest, the list shows
   only what was given (S0028), and the publish switch of S0021 and the Drive announcement of S0029 are not
   offered, with one line saying why.
7. **Pacing.** A refused password is shown once; the app never retries a password by itself, so it never
   competes with the server's one check per second.
8. **Words.** Messages speak of the server, the account and the device; IP, port, firewall and router appear
   only where the diagnosis (S0031) needs them. Strings in EN, RU and UK in the same edit, TalkBack labels,
   D-pad order and Enter-to-submit per `INPUT-PARITY`.
9. Probes `Timber.d("S0027: ..")`; no password, token, invitation or pairing code in a log, a saved state or a
   diagnostic export (the canary test of S0016 is extended to the new messages).

Not in scope: account deletion (FMS_A and FMS_W delete accounts; this product links to the portal page "how to
delete an account" through S0030), the account list of broadcasts (S0028), the watch's hand-over itself (S0022).

## 3. Constraints and dependencies

### 3.1 Constraints

- Written to the text FMS_A S4214 folds into `DEVICE-EXCHANGE`; not started before that fold. A message shape the
  fold leaves open is a proposal beside the contract, never a local invention.
- The password never reaches disk, a log or a saved state; the token stays in the Keystore (S0018).
- TLS 1.2 minimum and the pin-on-first-connect rule are the S0016 trust rule; a renewal with the same key is not a
  changed certificate - checked against the S4214 text and corrected in the trust rule if the code differs.

### 3.2 Related tickets

S0016, S0018, S0019, S0023 (what this builds on), S0022 (the watch signed out with the account), S0028 (what a
guest sees), S0021 and S0029 (what a guest may not announce), S0030 (words), S0031 (diagnosis), S0034 (joint
runs).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0016, S0018, S0019, S0022, S0023, S0028, S0021, S0029, S0030, S0031, S0034.
- **Account rules:** answered 2026-10-14 (FMS_A S4203 questionnaire) - section 0.
- **Destination first meets "hide IP and ports":** S0019 item 3 puts `host:port` on screen before the password
  (grammar proposal point 13, accepted 2026-10-10, a protection against typing a password into the wrong
  server); the owner's answer of 2026-10-14 hides IP and ports as long as possible. Recommended reading of
  both: the server's name and the login lead, the address stays as one small secondary line under them before
  the password is typed, and is never presented as something the user has to act on. Confirm or choose
  "address under Details only". **Resolved by the owner, 2026-10-15:** the address is a small secondary line
  below the server name and login.

### 3.4 Fold reconciliation (2026-10-15)

- Read DEVICE-EXCHANGE 0.28, including section 19 items AO-AZ, section 21 items BO-BR and section 25.
- Item AT takes precedence over scope 3: `password-changed` rotates this device's token and keeps it signed
  in; all other devices sign in again. No redundant password enrollment is sent.
- Items AR/AZ take precedence over scope 2/6: an invitation string opens sign-in in this product. It cannot
  register a guest; first register in Fast Media Sorter & Sharing, then sign in here. Invitation bytes are
  validated and discarded, never retained in the form or encoded from an account entry.
- No `invitation-withdrawn` or `account-deleted` token refusal exists in the folded contract. Use the defined
  `signed-out`, `device-revoked` and `bad-credentials` reasons; do not invent wire messages.
- Watch enrollment is not present in this tree. S0022 owns fresh phone pairing and both-pin hand-over; this
  ticket implements the phone session state and preserves its identity on refusal.

## 5. Implementation plan

1. Persist account kind, sign-in reason and public-key pin with a non-destructive Room migration. Port the
   password-change and own-device revoke operations from FastMediaSorter; wipe passwords on every exit.
   Verify by JVM fake-server and migration tests, plus compilation.
2. Keep enrollment and the stored entry on the same device identity; map token refusal to retained sign-in
   state and stop control reconnects on authentication refusal. Validate/discard invitation `i`. Accept
   renewed pinned leaves only with the saved key and enforce TLS 1.2. Verify identity, invitation, trust,
   refusal and canary tests.
3. Add password change and merge removal into sign-out; preserve kept channels. Present destination before
   the password, localized reasons, and guest restrictions across account and broadcast settings. Verify
   ViewModel tests, all three locales, target builds and an emulator walkthrough where available.
4. Update the contract pointer, consumer registry note, port provenance and README. Record fresh command
   evidence and remaining manual dependencies here and in `temp/S0027/`.

Coverage: scope 1/2/7 -> steps 1/2; scope 3/4/5 -> steps 1/2/3; scope 6 -> steps 1/3 (S0028 owns directory
grant behavior; S0029 owns Drive); scope 8/9 -> steps 2/3. Each step consumes existing seams or fields
produced by an earlier step. Self-review: no forward dependency and no unresolved owner placement choice.

## 4. Definition of done

- Between releases inspect and directly exercise the changed area where possible; Android has no measured fast
  Gradle test. RELEASE: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` runs phone and wear suites with
  the tests: pre-fill including the login, each refusal reason and its message,
  change password (success, wrong old password), signed out by a password change (entry kept), sign out on this
  device, guest entry with publishing not offered, no automatic password retry, canaries.
- Emulator walkthrough against the owner's exchange server (credentials in the FMS_A questionnaire, В1.1 - never
  copied into this repository), with reproducing steps and verification recorded in `temp/S0027/`.
- The three locales render without truncation on the smallest supported width.
- The `DEVICE-EXCHANGE` registry row note and the pointer name what the user can now do.
- In-app help for the reshaped screen (handed on by S0035 on 2026-10-29, its slice 5; `CONTEXT-HELP` rule 18):
  `ExchangeActivity` and `dialog_exchange_password` get rows in `phone/src/main/assets/help/help-map.json` (a page, or
  `inherit` with a reason), a page source in `docs/help/content/` in EN/RU/UK, the `?` and F1; their lines leave
  `tools/help/help-map-baseline.txt`, and `pwsh -NoProfile -File tools/help/assert-help-map.ps1` exits 0
  (`tools/help/README.md`, "Adding or changing a screen").

## 6. Evidence, 2026-10-29

Unattended queue pass (`run-spec-queue`). Between releases only the measured fast checks ran; Android has no
measured fast compile or test form (CLAUDE.md section 5a), so the Kotlin edits below are **not built, not tested
until the release**.

Changed: `docs/help/content/exchange.json` (new page source, 30 elements, EN/RU/UK), `docs/help/content/catalog.json`
(related link to the new page), `phone/src/main/assets/help/help-map.json` (page row `ExchangeActivity`, inherit row
`dialog_exchange_password` -> `change-password`), `tools/help/help-map-baseline.txt` (both S0027 lines removed),
`phone/src/main/assets/help/{en,ru,uk}/*.html` (regenerated), `HelpSurfaceIds.kt` (`EXCHANGE`, `EXCHANGE_PASSWORD`),
`ExchangeActivity.kt` (toolbar `menu_help`, F1 in `dispatchKeyEvent`), `ExchangePasswordDialog.kt` (title with the
help action, `HelpKeys.install`), `tools/help/capture-help-screenshots.ps1` (recipe and reach `exchange`),
`tools/help/README.md`, `docs/contracts/DEVICE-EXCHANGE.md` (pointer note).

| Command | Exit | Expected | Actual |
| --- | --- | --- | --- |
| `pwsh -NoProfile -File tools/help/generate-help-pages.ps1` (2.26 s) | 0 | PASS, pages for the new help id in 3 locales | `generate-help-pages: PASS (files=34 written=9 pages=10 locales=en,ru,uk date=2026-10-29)`; `{en,ru,uk}/exchange.html` written, `id="change-password"` present |
| `pwsh -NoProfile -File tools/help/assert-help-map.ps1` (0.73 s) | 0 | PASS with the two S0027 surfaces mapped | `assert-help-map: PASS (30 surfaces, 29 rows, 1 baseline, 33 pages, 8 picture sets checked ..)`; only `ExchangeDiagnosisDialog` (S0031) left as accepted debt |
| `pwsh -NoProfile -File tools/release/check-syntax.ps1` | 0 | PASS | `release-syntax: PASS` |
| PowerShell parser on `capture-help-screenshots.ps1` | - | 0 errors | 0 errors |

RELEASE: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` compiles the Kotlin edits and runs the phone
and wear suites (`ExchangeSignInOnlyTest`, `HelpTargetResolverTest`). Manual: `capture-help-screenshots.ps1 -Help
exchange` and `-F1Proof` on an emulator.

## Last Audit

**Date:** 26-10-29 20:15 | **Mode:** static | **Outcome:** Partial
**Counts:** PASS 9 | WARN 1 | FAIL 0 | MANUAL 6 | UNCHECKABLE 0 | EXEMPT 0 | RELEASE 2

PASS: scopes 1-7 and 9 in code with JVM coverage (`ExchangeSignInOnlyTest`, 10 tests, green 2026-10-25); scope 8
strings in EN/RU/UK; the owner's placement of the address (2026-10-15) live on 2026-10-25; pre-fill, pin-on-first-
connect, refusal message and no-retry pacing observed live on 2026-10-25; help map rows, page, anchor and baseline
(gate PASS above); `?` and F1 wired on both surfaces by static inspection; the button defect resolved by S0036
under `BUTTONS` 2.0 (no owner decision left); pointer note updated; probes `Timber.d("S0027: ..")` present for the
manual-test status.
WARN: the help pages ship without screenshots until the capture runs (the gate allows a page without pictures).

### Action items
1. RELEASE - `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1`: compile `ExchangeActivity.kt`,
   `ExchangePasswordDialog.kt`, `HelpSurfaceIds.kt` and run the phone/wear suites.
2. RELEASE - the same sweep re-runs `assert-help-map.ps1` and the help gate self-test.

### Manual / on-target
- [ ] With fresh credentials from the owner: successful sign-in from a string, the directory, `Sign out on this
  device`, and change password (wrong old password shown once; success keeps this phone signed in).
- [ ] A guest invitation minted by the host: the entry is a guest, the publish switch is absent with its line.
- [ ] ru and uk at 320 dp: the three connection-string buttons wrap whole and no word breaks mid-word.
- [ ] TalkBack pass over the sign-in form, the account card and the change-password dialog.
- [ ] `capture-help-screenshots.ps1 -Help exchange` on an emulator, then `generate-help-pages.ps1` and the gate.
- [ ] `?` and F1 on the exchange screen open `exchange`; in the change-password dialog they open its
  `change-password` entry.
