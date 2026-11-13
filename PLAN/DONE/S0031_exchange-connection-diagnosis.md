# S0031 - "Won't connect": a step-by-step diagnosis on phone and watch, in the family's common words

**Status:** Implemented
**Phase:** device exchange, release-46 milestone (FastMediaSorter Android S4203) - independent of the build phases of handoff section 8
**Verification level:** unit tests on the JVM that drive every step's outcome with a fake network and a fake server; an emulator pass that provokes each failure; the joint runs of S0034 in two networks.

## 0. Source

- Owner, 2026-10-14: answers to the questionnaire of FastMediaSorter Android (FMS_A) milestone S4203, final:
  - A step-by-step "won't connect" diagnosis with the same texts in every program, from a common EN/RU/UK table
    in `DEVICE-EXCHANGE` - in release 46.
  - IP, ports, firewall and router details stay hidden from the user as long as possible.
  - The client tries every connection kind of a record, fastest to slowest, before it declares "no
    connection", and switches on a network change.
- FMS_A S4219 (`DEVICE-EXCHANGE`: the common error table, terms, product names) - the text this ticket is
  written to. The same diagnosis elsewhere: FMS_A S4236 (phone and FastMediaSorter watch), FMS_W 232,
  StreamsPlayer (Windows) StreamsPlayer SP-0225.
- Facts in this tree, 2026-10-14: each surface ends in its own single message - the three connection-string
  refusals (S0017), invalid login or password (S0019), certificate changed (S0018, S0019), "not available"
  for an ended or full broadcast (S0020), not published (S0021); `NetworkContextAnalyzer` reads the phone's
  network state; the watch has a network monitor screen. Nothing walks the steps in order or says which step
  failed.

## 1. Goal

When a connection fails, the person presses one button and sees the steps checked one after another - network,
server reachable, secure connection, account, the broadcast itself - with the first failing step named in the
same words every program of the family uses, and what to do about it; network jargon appears only when the step
itself is about the network.

## 2. Scope

1. **Steps.** The step list and order of the S4219 table, run in order on the phone and on the watch, each
   with its outcome (passed, failed, not checked because an earlier step failed); a step never takes longer than
   the contract's timeout and the whole run can be cancelled.
2. **Texts.** Each step's title, failure text and advice come from the vendored EN/RU/UK table (the same file
   S0030 vendors), never rewritten here; a test fails when a step has no table row.
3. **Entry points.** "Why won't it connect?" from every place a connection fails: sign-in (S0027), the list
   (S0028), playing a broadcast (S0020), publishing (S0021), Drive (S0029), the watch's sign-in and list
   (S0022, S0028). Each opens the run for that connection.
4. **Network details on demand.** Addresses, ports and the certificate fingerprint are shown only inside the
   step that needs them, folded by default.
5. **Portal.** Each failed step offers its portal page (S0030 deep links).
6. **Copy for help.** One action copies the run's outcome in the table's words, with no password, token, link
   password, pairing code or full broadcast id in it (the S0016 canary test covers the copied text).
7. Probes `Timber.d("S0031: ..")`.

Not in scope: a diagnostic log bundle (`DIAGNOSTIC-REPORT` stays unbound until one is scheduled), repairing a
router or a firewall, the server's own diagnostics.

## 3. Constraints and dependencies

### 3.1 Constraints

- Written to the table FMS_A S4219 folds; not started before that fold. A failure this product meets and the
  table lacks is a proposal beside the contract, never a local text.
- The run reuses the connection code paths of S0016 and S0020; it never opens a second, diagnostic-only
  connection that behaves differently from the real one.
- Battery on the watch: the run starts only on the user's press and stops when its screen closes.

### 3.2 Related tickets

S0016, S0017, S0018, S0019, S0020, S0021, S0022, S0027, S0028, S0029 (the failures it explains), S0030 (the
vendored table and the deep links), S0034 (joint runs).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0016, S0017, S0018, S0019, S0020, S0021, S0022, S0027, S0028, S0029, S0030, S0034.
- **Diagnosis rules:** answered 2026-10-14 (FMS_A S4203 questionnaire) - section 0.

## 4. Definition of done

- `.\gradlew.bat :phone:testStoreDebugUnitTest :phone:testNoLegalDebugUnitTest :wear:testDebugUnitTest
  --console=plain` exits 0 with the tests: every step's outcome, order and "not checked", cancellation,
  every step mapped to a table row, copied text free of secrets.
- Emulator pass that provokes each failure (no network, wrong address, changed certificate, wrong password,
  revoked device, ended broadcast) on phone and watch, recorded in `PLAN/S0031_exchange-connection-diagnosis/`.
- The three locales render without truncation on the smallest phone width and on the round watch screen.
- In-app help for the diagnosis (handed on by S0035 on 2026-10-29, its slice 5; `CONTEXT-HELP` rule 18):
  `ExchangeDiagnosisDialog` gets a row in `phone/src/main/assets/help/help-map.json` (a page, or `inherit` from the
  exchange screen with a reason), the `?` and F1; its line leaves `tools/help/help-map-baseline.txt`, and
  `pwsh -NoProfile -File tools/help/assert-help-map.ps1` exits 0.
