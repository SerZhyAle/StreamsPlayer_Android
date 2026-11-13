# S0028 - The account's broadcast list on phone and watch, and a broadcast opened by link and password

**Status:** In Progress
**Status note:** Queue pass 2026-10-29: plan phase C built (producer Digest/RTSP gate, auth, verifiers, own pair sealed and played) - not built or tested until the release; next is phase D, the phone giving the link password. Phases C to F ship together: until D no screen shows the generated password of an announced phone broadcast.
**Phase:** device exchange, release-46 milestone (FastMediaSorter Android S4203) - independent of the build phases of handoff section 8
**Verification level:** ViewModel tests on the JVM against a fake server; the emulator phone and watch pair against the owner's exchange server; the joint runs of S0034.
**Complexity:** complex - confirmed 2026-10-29 at the queue pass: the giving half is a port of FastMediaSorter's producer password across the phone's servers, the shared publisher and two UIs, plus the watch's protected-link step and a shared-contract boundary (`LIVE-BROADCAST` item R, `DEVICE-EXCHANGE` items DV and AJ).

## Remaining development

*As of 2026-10-27, checked against the tree. Built: the phone list on open behind the visibility gate, the protected-link reception (the password is asked before connecting and kept only with a kept channel, in the Keystore-backed store; `ProtectedBroadcastTest`, `KeystoreBroadcastPasswordStoreTest`), the guest restriction through S0027, and no local discovery.*

1. **Scope 4 - the link with a password, giving.** The phone's broadcast share panel (`BroadcastShareManager`, `BroadcastShareLinkFactory`) and the watch's broadcast screen offer the link with its password as S4215 defines, beside the existing link, file and QR. The pairs and the choice are ported (plan phase B) and the phone's servers and the
   publisher demand them (phase C); the two UIs are phases D and E. **Phases C to F ship together:** since phase C a
   phone broadcast announced through a server is protected by a generated password that no screen shows before
   phase D, and the watch refers the phone's own broadcasts to the phone until phase F.
2. **Scope 2 - the watch list.** 2026-10-28, from S0038: the watch's list on open is now the live group of the Own category on the streams screen, read through the shared `AccountBroadcastDirectory` only while that category is in front (`WearExchangePresence.connection`); the unused watcher in `WearExchangeRuntime` is gone. Audio rows play through `WearBroadcastPlaybackResolver` (S0020 order); a password broadcast is listed and refers the wearer to the phone. Still S0028's: the password step on the watch before the first endpoint for a protected link (scope 3), and the run on a paired watch.
3. **Item DV (1.7, section 30) - `verifiers` on the broadcaster's announcements.** Built 2026-10-29 (plan phase C):
   every `broadcast-start` and record `broadcast-update` carries the list, empty for an open link (see S0021).
4. **Hand-over from S0026 - a protected resource is listed locked.** Built 2026-10-29 (plan phase A): every resource
   behind a PIN gets its own locked row - name, device, and the contract's `protection-unsupported` text of item CZ in
   EN, RU and UK, no PIN prompt; unprotected shared folders keep the one aggregate notice. Not built, not tested until
   the release.

## Plan (queue pass 2026-10-29)

Coverage: scope 1, 5, 6 and the receiving half of scope 3 on the phone are built (the paragraph above). The rest maps
to the phases below; scope 7 (words, TalkBack, D-pad) and scope 8 (probes) ride with every phase that adds a surface.
Topology: B produces the pairs and the choice that C, D and E consume; F consumes nothing of B-E.

- [x] **A. Locked resources (hand-over item AJ).** `ExchangeLockedResources` splits the snapshot; `ExchangeViewModel`
  carries `lockedResources`; `ExchangeActivity` inflates `item_exchange_locked_resource` into `layoutLockedResources`
  (TalkBack reads a row as one unit, the D-pad skips it); the `exchange` help page says so in EN/RU/UK. Static check:
  `ExchangeLockedResourcesTest` exists; `assert-help-map.ps1` PASS.
- [x] **B. The pairs and the choice, ported (scope 4, item DV groundwork).** Into `:exchange-core`, shared by phone and
  watch: `BroadcastLinkPasswordMode`, `BroadcastLinkPasswordRules`, `BroadcastLinkChoice`, `BroadcastVerifiers`,
  `BroadcastCredentialRegistry` (without the prepared link of item DW, which this product does not offer) and their
  three tests; `BroadcastCredentialPair` moved there from the phone, same package. Ledger rows recorded 2026-10-29.
- [x] **C. The producer demands the pair (scope 4).** Built 2026-10-29. Carried whole from FastMediaSorter:
  `BroadcastHttpServer` (Digest gate, numeric-peer client handler, the deliberate `end()` that S0043 wires) with its
  test, `RtspEndpointAddress`, `RtspAuthGate`, `RtspDownstreamFramer` and their tests; `RtspGatedPort` without its
  `end()` (S0043). The phone's audio server and `VideoBroadcastService` check `BroadcastCredentialRegistry.pairs()`.
  `BroadcastExchangePublisher` begins the registry when a `Session.linkChoice` is given (the phone gives `Generated`
  until phase D resolves the person's choice; the watch gives none until phase E), writes `authMarker()` into the
  record's and the outward descriptor's `auth`, sends `verifiers` on every `broadcast-start` and `broadcast-update`
  (item DV) and a `broadcast-update` at every `revision` change, and seals the own pair as `credentials` (item DK).
  Receiving half (item BF, "a receiver plays an own protected broadcast with no question"): the opener copies the
  box's `credentials` onto the opened descriptor, `BroadcastDescriptorDto.credentials` carries it, the descriptor
  store keeps it in process memory only, the resolver answers with it, and a kept channel keeps it in the secret
  store. The internal pair for a device's own relay upload is phase E's: the phone never reads its own server.
  Static check: grep `verifiers` in the publisher; `RtspAuthGateTest`, `RtspDownstreamFramerTest`, the two
  protection tests of `BroadcastPublishFakeServerTest`, the own-pair tests of `ProtectedBroadcastTest` and
  `ExchangeBroadcastOpenerTest` exist.
- [ ] **D. The phone gives the link with its password (scope 4).** Port `KeystoreBroadcastLinkPasswordStore`,
  `SetBroadcastLinkPasswordUseCase`, `ResolveBroadcastLinkChoiceUseCase`, `RenewBroadcastLinkPasswordUseCase`,
  `BroadcastLinkPasswordDialogManager` with `dialog_broadcast_link_password.xml`, and the link-password rows of the
  broadcast settings; the share panel shows the password beside the link, file and QR, never inside them. Strings in
  EN/RU/UK, the help sources of the touched surfaces. Static check: the strings exist in the three locales;
  `assert-help-map.ps1` PASS.
- [ ] **E. The watch gives its link (scope 4).** The watch's broadcast screen shows the generated password beside its
  QR; the watch offers only the generated password, which item R allows ("a producer that recorded 1.2 does less, not
  wrong"). `LiveAudioLanServer` checks the registry's pairs, the watch's session passes a `linkChoice`, and its relay
  upload, which reads that server over loopback, answers with the registry's internal pair (FastMediaSorter
  `ExchangeRelayUploader`, `ExchangeRelayUploaderGenerationTest`). Wear strings in EN/RU/UK.
- [ ] **F. The watch asks the password of a protected link (scope 3 on the watch).** Before the first endpoint, as the
  phone does; a password broadcast handed over from the phone carries no password, so the watch asks or refers to the
  phone per `LIVE-BROADCAST` 15 (the password never enters a URL). Since phase C the phone's announced broadcasts are
  protected, so the watch's Own list plays them only with the own pair the opener now puts on the descriptor
  (`credentials`, FastMediaSorter `WearBroadcastPlaybackResolver`); until then the watch refers them to the phone.
- [ ] **G. Evidence and records.** The release suites; the emulator phone and watch walkthrough against the owner's
  exchange server with two broadcasters at once (`temp/S0028/`); the `DEVICE-EXCHANGE` and `LIVE-BROADCAST` registry
  notes and pointers name the list, the link password and the locked row.

## 0. Source

- Owner, 2026-10-14: answers to the questionnaire of FastMediaSorter Android (FMS_A) milestone S4203 (В11-В15,
  В24-В29), final:
  - With an exchange server, the account's broadcasts appear by themselves in a receiver's list when the list
    is opened; the user picks the broadcaster; several devices may broadcast at once. Without a server - a
    link. The broadcaster always has a link to give to anyone. No broadcast discovery in the local network.
  - Outward access per broadcast: a personal "guest" invitation, or a public link with a password and no
    account - broadcast links getting a password is new.
  - This product receives and broadcasts through the server fully; its watch has its own device record
    (S0022).
- FMS_A S4215 (`DEVICE-EXCHANGE` + `ANYWHERE-ACCESS`: one record with all channels, outward access - guest,
  link with password, broadcasts included) and FMS_A S4217 (`DEVICE-EXCHANGE` + `LIVE-BROADCAST`: roles,
  sources, the account broadcast list, cast, phone to watch hand-off) - the texts this ticket is written to.
  The same list in StreamsPlayer (Windows): StreamsPlayer SP-0222.
- Facts in this tree, 2026-10-14: S0019's directory view lists the account's live broadcasts grouped by device
  with presence, plays one, keeps one or all as channels and follows `subscribe` updates; S0020 imports a
  descriptor from a link, a file, a paste or a QR and plays its endpoints in order - no link carries a password
  today; the watch reads no directory (S0022 item 5 plans the read).

## 1. Goal

Opening the list on the phone or on the watch shows, at once, what the account's devices are broadcasting -
one or several at the same time - and the person picks whom to watch or hear. A broadcast handed over as a link
with a password opens with no account, after the password, and a broadcast this phone or watch makes can be
handed over the same way.

## 2. Scope

1. **Phone list on open.** The list screen shows the account's live broadcasts as soon as it opens (`list`
   then `subscribe`, no refresh action needed), grouped by broadcaster, several broadcasters live at once;
   picking one plays it; an ended broadcast leaves the list by itself. Keep as channel and "keep everything
   listed now" stay as S0019 built them.
2. **Watch list.** On the watch enrolled by S0022: the same list for the modes the watch declares in `plays`
   (audio today), round-screen layout, rotary and touch input per the watch's existing input parity; the list
   is read only while the screen is open (S0022 item 4).
3. **Link with a password - receiving.** A broadcast link that carries a password requirement (S4215) asks for
   the password before connecting, on phone and watch (the watch receives a link handed over from the phone);
   a wrong password is one message; when the user keeps the channel, the password is kept with it in the
   secret store and never logged; no account is needed.
4. **Link with a password - giving.** The phone's broadcast share panel, and the watch's broadcast screen where
   it shows its QR today, offer the link with its password as S4215 defines, beside the existing link, file and
   QR.
5. **Guest.** Signed in as a guest (S0027), the list shows only what was given to that guest.
6. **No local discovery.** Nothing browses the local network for broadcasts; the list is the server's directory
   only, and the link is the way without a server.
7. **Words and input.** Strings in EN, RU and UK in the same edit (terms from S0030 once it lands), TalkBack
   labels, D-pad order on the phone.
8. Probes `Timber.d("S0028: ..")`; a broadcast id is logged as 8 hex of its SHA-256, a password never.

Not in scope: Drive announcements in the list (S0029 adds its source to this list), encryption (S0032), the
watch's enrolment and its own publishing (S0022), StreamsPlayer (Windows)'s several windows (a phone plays one
picked broadcast at a time).

## 3. Constraints and dependencies

### 3.1 Constraints

- Written to the texts FMS_A S4215 and S4217 fold; not started before both folds. The password shape on a link
  is the contract's, never this product's own.
- The S0020 endpoint order and trust rules are reused unchanged; this ticket adds the password step before the
  first endpoint, not a second player path.
- Battery: the watch list holds no stream open outside its screen.

### 3.2 Related tickets

S0019 (the directory view), S0020 (playing a descriptor), S0021 (the phone's published broadcasts and their
link), S0022 (the watch's device record - must land first for item 2), S0027 (guest), S0029 (Drive
announcements in the list), S0030 (words), S0032 (encrypted broadcasts in the list), S0034 (joint runs).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0019, S0020, S0021, S0022, S0027, S0029, S0030, S0032, S0034.
- **List and link behaviour:** answered 2026-10-14 (FMS_A S4203 questionnaire) - section 0.

## 4. Definition of done

- Between releases inspect and directly exercise the changed area where possible; Android has no measured fast
  Gradle test. RELEASE: `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1` runs phone and wear suites with
  the tests: list on open, several broadcasters, ended broadcast leaves, guest
  view, link password asked, wrong password, password kept only with a kept channel, no password in a log.
- Emulator phone and watch pair walkthrough against the owner's exchange server (credentials in the FMS_A
  questionnaire, В1.1 - never copied here) with two broadcasting devices at once, recorded in `temp/S0028/`.
- The `DEVICE-EXCHANGE` and `LIVE-BROADCAST` registry row notes name the list and the link password.

## Hand-over from S0026 (2026-10-25)

The DEVICE-EXCHANGE round S0026 recorded dated registry exceptions (until 2026-12-31) that touch this ticket; the
proposal is `PLAN/DONE/S0026_contract-sync-2026-10-12/registry-sync.patch`.

- **Item AJ and section 9 point 10 - a protected resource is listed locked.** The directory screen draws one aggregate
  notice for a record of kind `sftp-share` ("Folders shared by other devices cannot be played here."). The contract
  lists a resource behind a PIN as a locked row and never hides it. When this ticket reworks the list, either draw that
  row (name, device, "locked - cannot be opened here", no PIN prompt, because this product declares no `pin-v1`) in EN,
  RU and UK, or ask the catalog whether a product with neither resource role owes only the aggregate notice. The
  record already keeps its lock and carries no `access` (`DeviceExchangeResourceRecord.isLocked`,
  `DeviceExchangeProtectionVectorsTest`).
- **Item DV (1.7).** `verifiers` on the broadcaster's announcements goes with the link password built here (see S0021).

## Last Audit

**Date:** 26-10-29 23:50 | **Mode:** static | **Outcome:** Partial
**Counts:** PASS 6 | WARN 3 | FAIL 0 | MANUAL 1 | UNCHECKABLE 0 | EXEMPT 0 | RELEASE 4

Queue pass 2026-10-29, plan phase C. Not built, not tested until the release.

- PASS `tools/port-ledger/record-port.ps1` - exit 0 for each of eight rows: `RtspAuthGate`, `RtspDownstreamFramer`, `RtspGatedPort` and the two gate tests recorded; `BroadcastHttpServer`, its test and `RtspEndpointAddress` carried with `-Refresh`. `check-drift.ps1` afterwards: `UNCARRIED: 140 of 798` (143 before).
- PASS full ports compared by content: `BroadcastDigest` of this tree equals FastMediaSorter's except line endings; RTSP-Server 1.4.3 and NanoHTTPD 2.3.1 are pinned alike in both `libs.versions.toml`, so the ported gate and the client handler call the same library API.
- PASS `pwsh -NoProfile -File tools/help/assert-help-map.ps1` - exit 0, 0.69 s, `assert-help-map: PASS (30 surfaces, 29 rows, 1 baseline, 33 pages, 8 picture sets checked ..)`; no surface changed in this phase.
- PASS probe invariant - no `"S0028:` line in `phone/src`, `wear/src`, `exchange-core/src`, `watchface/src` while the ticket is In Progress.
- PASS no secret in a persistent or logged place by inspection: the own pair rides only in the sealed box, the opened descriptor and `BroadcastDescriptorStore`'s process memory (the preferences get the descriptor without `credentials`); `BroadcastCredentialsDto.toString` masks the password; the new tests assert both.
- PASS contract boundary - no catalog change: items BF, DK and DV of `DEVICE-EXCHANGE` 1.7 and `LIVE-BROADCAST` item M are implemented as written; `verifiers` is sent on every `broadcast-start` and record `broadcast-update`, empty for an open or an unprotected link.
- WARN partial carries: `VideoBroadcastService`, `BroadcastCaptureService`, `BroadcastDescriptorDto`, `ImportStreamBroadcastUseCase` and `ExchangeBroadcastOpener` take only the protection lines of their FastMediaSorter versions; their ledger rows stay `DRIFT` for the rest (torch, rotation and the deliberate end belong to other tickets).
- WARN the `maxListeners`-only `broadcast-update` sends no `verifiers`; item DV reads a missing member on an update as "no change", which is what that update means.
- WARN phases C to F ship together: a phone broadcast announced through a server is now protected by a generated password no screen shows before phase D, and the watch refers the phone's own broadcasts to the phone until phase F.

### Action items
1. Phase D - the phone gives the link with its password (store, use cases, dialog, settings rows, share panel, EN/RU/UK, help), replacing `BroadcastLinkChoice.Generated` in both phone sessions with the person's choice.
2. Phases E to G of the plan.

### Release items
- RELEASE compile of `:phone`, `:wear`, `:exchange-core` and the suites with `ExchangeLockedResourcesTest`, `BroadcastCredentialRegistryTest`, `BroadcastVerifiersTest`, `BroadcastLinkPasswordRulesTest`, `BroadcastHttpServerTest`, `RtspAuthGateTest`, `RtspDownstreamFramerTest`, `RtspEndpointAddressTest`, `BroadcastPublishFakeServerTest`, `ProtectedBroadcastTest`, `ExchangeBroadcastOpenerTest` - `pwsh -NoProfile -File tools/release/pre-release-sweep.ps1`.
- RELEASE `BroadcastCredentialPair` moved from `:phone` to `:exchange-core` in the same package; the phone's `BroadcastDigest`, `BroadcastPlaybackPairs` and `BroadcastPlaybackResolver` resolve it through the module dependency - proven by the same sweep's compile.
- RELEASE Hilt graph of `:phone` and `:wear` with `BroadcastCredentialRegistry` injected into `BroadcastExchangePublisher`, `BroadcastCaptureService` and `VideoBroadcastService` - proven on the release variant by the same sweep.
- RELEASE the `exchange` screenshot set after the phase A layout change - `tools/help/capture-help-screenshots.ps1 -Serial emulator-NNNN`, then `generate-help-pages.ps1` and `assert-help-map.ps1`.

### Manual / on-target
- [ ] Emulator phone and watch walkthrough against the owner's exchange server with two broadcasters at once and one PIN-protected folder of FMS_A listed locked, recorded in `temp/S0028/`.
