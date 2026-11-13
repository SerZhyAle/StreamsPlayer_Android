# S0058 - Change-password dialog shows no server address (DEVICE-EXCHANGE 1.11 item ED)

**Ticket:** S0058
**Status:** Implemented
**Status note:** Implemented 2026-10-30 (queue pass pq1, static): change-password dialog shows the entry host:port above the first field; grep checks and assert-help-map PASS exit 0; not built, not tested until the release - build, device walk and 320dp ru/uk are RELEASE items; canon note for the DEVICE-EXCHANGE 1.11 registry row
**Complexity:** light - confirmed 2026-10-30 at the spec pass: three existing files change (the activity call, the dialog, its layout), no new file, no new string, no contract wire change
**Date:** 2026-10-29
**Rung:** primitive spec - Problem, Approach and Done criteria below; the pipeline sections are kept because the ticket was captured as a full spec

## Captured request and migration scope

Parked from S0057 (contract sync to the catalog of 2026-10-29).

Symptom: the change-password dialog of the exchange screen asks for the old and the new password of a server but does not show the `host:port` the password goes to. DEVICE-EXCHANGE 1.11 item ED (amends items AZ and BN, which came from this product's own release-46 audit proposal): item BN governs every surface that asks for a password or a pairing code for a server - the `host:port` is on the face of that surface before the field takes focus, not behind a details control; it may be set smaller than the server's name and beside the trust state; a display name is never the only identification.

Evidence (static reading, 2026-10-29): `phone/src/main/java/com/sza/streamsplayer/ui/exchange/ExchangePasswordDialog.kt` builds the dialog from `dialog_exchange_password.xml` and focuses `etOldPassword` at show time; the layout holds the three password fields and the error line and no address view. The sign-in form shows the address (S0027); the pairing-code surface of the watch hand-over was not inspected.

Needed: show the entry's `host:port` on the dialog before the first field takes focus (EN, RU, UK strings if any new label is needed), check the pairing-code surfaces against the same item, and re-verify the DEVICE-EXCHANGE registry row at 1.11. Contract boundary is the user-visible placement only; no wire change.

## 3. Open questions

All resolved on 2026-10-30 without the owner, by the records below.

- Is the address owed on every surface, or only on the change-password dialog? **Every surface that asks for a password or a
  pairing code for a server** - DEVICE-EXCHANGE 1.11 item ED, read in `P:\Contracts\device-exchange\RECOMMENDATIONS-2026-10-29.md`
  (section ED) with item BN of `README.md`. The contract settles the placement; no owner call is left.
- Which pairing-code surfaces exist? **Two were inspected, one has an input:**
  - Phone sign-in form (`ExchangeActivity.kt`, `renderState`, `NoAccount` branch): `switchPairingCode` only changes the field
    label of the same form, so the form covers the pairing-code case. `tvDestination` shows `form.hostPortDisplay` whenever
    `form.host` is not blank (`ExchangeActivity.kt` lines 490-491, S0027). Already compliant; no change.
  - Watch hand-over: the phone sends `host`, `login` and `pairingCode` over the Data Layer (`WearExchangeHandoverPayload`); the
    watch has no screen that asks for a code or a password (`wear/src/main/res/values/exchange_watch.xml` carries only reason
    strings; no text input is declared in `wear/src/main`). Nothing to show on the watch; no change.
  - Phone `btnConnectWatch` (the hand-over trigger) asks nothing; it is a button and a status line. No change.
- Which strings are new? **None.** The value is the `host:port` itself, so EN, RU and UK resources stay as they are.
- DEVICE-EXCHANGE registry row at 1.11 - **a canon-session item, not a block.** The row lives in the catalog `P:\Contracts`,
  which this session reads and does not write. The fix belongs to the canon session as a one-line note; it does not hold
  the code change (see section 5 below).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none blocks this ticket. S0057 is the parent (the ticket it was parked from). S0054 and S0055 are neighbours
  that touch no file listed here.
- **Owner question on placement:** none - settled by DEVICE-EXCHANGE 1.11 item ED (section 3 above).
- **Canon-session item:** the DEVICE-EXCHANGE 1.11 registry row, re-verified by the canon session; not a block of this ticket.

## 4. Definition of done

- The change-password dialog shows the entry's `host:port` above the first password field, before focus is requested.
- The address comes from the account entry the screen already holds; no new state, no new storage, no new wire field.
- Static checks in section 6 pass in this run (grep and the help-map gate), with their exit codes recorded in the audit.
- Not built and not tested here: the build, the unit suites and a run on the device are RELEASE items.

## 5. Shared-contract boundary

Placement only. DEVICE-EXCHANGE 1.11 item ED fixes the placement; no wire shape, envelope, reason or version changes, and no
catalog file is written from this ticket. The registry-row re-verification is recorded as a canon-session note, not done here.

## 6. Plan

Coverage: the placement rule (item ED over BN) maps to phase 1 and phase 2; the pairing-code inspection (section 3) is settled
research with no code, and its finding is checked by phase 3; the help source is checked by phase 4; the registry row is the
canon-session note in section 5.

- [x] Phase 1 - `ExchangeActivity.kt` `setupAccountControls`: the password button passes `"${entry.host}:${entry.port}"` of the
  current `ExchangeUiState.AccountReady` entry as the third argument of `ExchangePasswordDialog.show`. The click does nothing if
  the state is not `AccountReady`, as `keepBroadcast` already does. Check: `grep -n "ExchangePasswordDialog.show(this, viewModel, " phone/src/main/java/com/sza/streamsplayer/ui/exchange/ExchangeActivity.kt` finds one line.
- [x] Phase 2 - `dialog_exchange_password.xml`: a `TextView` `tvPasswordDestination` is the first child of the root, 12sp,
  `?attr/colorOnSurfaceVariant`, `layout_marginBottom="8dp"`, the same style as `tvDestination` in `activity_exchange.xml`.
  `ExchangePasswordDialog.show` sets its text from the new `hostPort` argument before `dialog.show()`. Check: the id is declared
  before the first `TextInputLayout` (`grep -n "tvPasswordDestination\|TextInputLayout" .../dialog_exchange_password.xml`), and
  `grep -n "tvPasswordDestination.text = hostPort" .../ExchangePasswordDialog.kt` finds one line.
- [x] Phase 3 - static check of the settled findings: `grep -n "tvDestination.isVisible = form.host.isNotBlank()" phone/src/main/java/com/sza/streamsplayer/ui/exchange/ExchangeActivity.kt` finds the line (the sign-in form and its pairing-code switch already show the address). Check: the grep prints the line and exits 0.
- [x] Phase 4 - help gate: `pwsh -NoProfile -File tools/help/assert-help-map.ps1`. The change adds no screen and no control, so the map is expected to stay as it is. Check: exit code 0 and the line `assert-help-map: PASS`.

## Last Audit

**Date:** 2026-10-30 | **Mode:** queue pass pq1 (between releases, static checks and the help gate only) | **Verdict:** Implemented - not built, not tested until the release

**Proven by fresh commands (2026-10-30):**

- `grep -n "ExchangePasswordDialog.show(this, viewModel, " phone/src/main/java/com/sza/streamsplayer/ui/exchange/ExchangeActivity.kt` - expected: one call with the third argument | actual: line 303, `"${entry.host}:${entry.port}"`, exit 0
- `grep -n "import.*ExchangeUiState" ExchangeActivity.kt` - expected: the type is imported for the new cast | actual: line 37, exit 0
- `grep -n "tvPasswordDestination" dialog_exchange_password.xml` - expected: declared above the first `TextInputLayout` | actual: TextView at line 6, first `TextInputLayout` at line 10, exit 0
- `grep -n "tvPasswordDestination.text = hostPort" ExchangePasswordDialog.kt` - expected: one line, set before `dialog.show()` | actual: line 25, exit 0
- `grep -rn "ExchangePasswordDialog.show" phone/src` - expected: one caller, so the new argument reaches every call | actual: 1 match, exit 0
- `grep -n "tvDestination.isVisible = form.host.isNotBlank()" ExchangeActivity.kt` - expected: the sign-in form (and its pairing-code switch) already shows the address | actual: line 494, exit 0
- `pwsh -NoProfile -File tools/help/assert-help-map.ps1` - expected: exit 0 and `assert-help-map: PASS` | actual: `assert-help-map: PASS (30 surfaces, 29 rows, 1 baseline, 33 pages, 8 picture sets checked ...)`, exit 0; the accepted debt entry is `ExchangeDiagnosisDialog`, S0031, not this ticket

**Scope and hygiene:** three files changed (the activity call, the dialog, its layout); no new file, no new string, so EN, RU and UK resources are untouched. No probe line was added (the ticket is not in the needs-manual-test status). No dead code left.

**RELEASE items (not built, not tested until the release):**

- RELEASE: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` - compile and unit suites; this is the first time the Kotlin change is compiled.
- RELEASE: run on a device or emulator: open Change password on a signed-in account; the `host:port` line is above the Current password field and the focus is on that field.
- RELEASE: the same dialog at 320dp in ru and uk, to check the 12sp line wraps without pushing the fields off screen.
- Canon session note (not a block of this ticket): re-verify the DEVICE-EXCHANGE 1.11 registry row for item ED in the catalog `P:\Contracts`; this session does not write the catalog.
