# Release queue - work remaining, in the owner's execution order

Projected from the ticket store (status) by the harness on every catalog write; package sections and
line order are the owner's. Line shape: `ticket  changed  status`. Package heading: its number alone,
`--` for not scheduled.

Execution order reviewed 2026-10-19 against the ticket specifications and catalog. Existing package
assignments and catalog statuses are retained. Within each block, prerequisites precede their consumers;
blocked work is skipped until its prerequisites are met. The `--` block is unscheduled, not a promise
to execute after package 1. Cross-package dependencies below can prevent a package-1 ticket from closing.

Statements refreshed 2026-10-27 after the PLAN triage: a ticket whose development is finished moved to RELEASE_READY.md and PLAN/DONE and has no row here; for a ticket still open, the remaining development is listed at the top of its specification.

current-next-release: 1

1
S0025_deep-dynamic-audit                                       26-11-13 06:16  BlockQuestions
S0014_free-promotion-campaign                                  26-11-13 01:55  Partial
S0040_test-lab-report                                          26-10-29 02:20  BlockByOtherTask
S0042_protected-directory-broadcast-box                        26-10-29 02:23  BlockByOtherTask
S0043_broadcast-deliberate-end-state                           26-10-29 02:27  BlockByOtherTask
S0044_broadcast-open-descriptor-shape-log                      26-10-29 02:31  BlockByOtherTask
S0045_sza-family-landing-and-documentation                     26-11-08 06:33  BlockByOtherTask
S0046_github-publication                                       26-10-26 17:05  Draft
S0047_google-play-first-release                                26-10-26 17:05  Draft
S0048_family-hub-and-sibling-links                             26-11-08 06:51  BlockByOtherTask
S0050_stream-bank-artwork-delivery                             26-11-08 15:20  BlockExternal
S0052_input-parity-gaps                                        26-10-29 03:01  BlockExternal
S0053_broadcast-end-pending-tunnel-gaps                        26-10-29 03:07  BlockExternal
S0059_stream-bank-three-letter-language-codes                  26-10-30 00:08  BlockExternal
S0066_target-sdk-37-local-network-access                       26-11-10 01:18  BlockByOtherTask
S0067_play-console-account-actions-2026-11                     26-11-10 20:13  BlockByOtherTask

--
S0028_exchange-account-broadcast-list                          26-10-23 14:19  In Progress        [idle 2, ok]
S0032_exchange-end-to-end-encryption                           26-10-29 23:41  BlockByOtherTask
S0024_exchange-p2p-endpoint                                    26-10-29 20:41  BlockExternal

### Execution statements and gates

These notes summarize remaining work; each ticket's specification and acceptance criteria remain
authoritative. They do not change status, approve implementation, or authorize publication.

| Ticket | Remaining work and execution gate |
| --- | --- |
| S0037 | Map current accepted contract changes to surfaces and existing tickets. Coordinate S0036, S0035 and exchange work without duplicating implementation; advance conformance claims only after evidence. |
| S0038 | Draft specification only. Reconcile S0028's directory lifecycle and S0020's unsaved playback route before implementation; S0027 supplies account lifecycle, and S0022/S0028 gate watch acceptance. Full closure waits for these unscheduled dependencies. Drive discovery is not required. |
| S0035 | Build what is left of the help: the exchange screens (with S0027 and S0031), the watch Help row, the stale-screenshot gate and the inherited-surface decision; then rerun the capture and the control diff and make a release build. Phone slices 1-4 are built and walked; the owner reviews the wording. The list is at the top of the specification. |
| S0025 | Phase A (rig and baseline) is done and the owner answered section 14 on 2026-11-08; the rig now carries the four promoted budgets, the debug main-Looper ping, the 100,000-entry M3U and the report-only phase A smoke of the sweep (`-AuditSerial`). Left: scene drivers and runs for phases B-D, which need debug builds and emulator sessions of minutes to overnight (the owner names them between releases, or they run at the release), and the owner's phase E pass; B-2 is re-judged with the ping, B-5b by the phase D overnight runs. Proposed budgets other than B-1, B-3, B-5a, B-7b remain hypotheses. |
| S0014 | Prepare campaign and listing materials after the release scope and UI stabilize, reusing the capture rig where useful. Recheck the recorded S0010 decisions; outward campaign steps remain gated by the ticket and the owner's publication instructions. |
| S0021 | Built through the video relay. Left: items DS-DU (the owner decides between keeping the publishing opt-in and the contract's "no separate switch") and DV (`verifiers`, with S0028's link password); then the device run of the tap and the upload and real listener handshake/delay reports through S0034 part A. Preserve the explicit publishing opt-in until the owner decides. |
| S0027 | Built and covered by JVM suites; the live walkthrough went as far as the credentials allow. Left: the Cyrillic wrapping of the connection-string buttons (the owner chooses shorter labels or stacked buttons); then sign-in, change password, sign out and the guest invitation on the live server with fresh owner credentials, and the TalkBack pass. |
| S0028 | The phone list on open and the protected-link reception are built. Left: the link with a password on the share panel and the watch broadcast screen, the watch list (the directory watcher from S0022 is built), `verifiers` (item DV, with S0021) and the locked-resource row. Supply one shared lifecycle for S0038. |
| S0031 | Built (the phone's `ExchangeDiagnosisDialog` exists; status moved to Implemented 2026-11-11, ready list). Left: verify failure cases on phone/watch and in both joint-run networks, record the emulator pass in the ticket folder, and add the help row for the dialog (S0035 slice 5). |
| S0032 | Sending is largely built (sealed relay upload for audio and video, pinned tunnel, device keys). Left: receive wiring on phone and watch, key trust and hand-off, the fail-closed paths, compatibility messages, the account-screen text and the tests listed at the top of the specification; then the live delay and the final joint run after S0034 part A. |
| S0024 | Keep P2P unscheduled and outside the release-46 milestone. Wait for a resolved contract proposal and an owner scheduling decision; ordinary router port forwarding does not satisfy this ticket. |

Ready statuses remain in RELEASE_READY.md. In particular, BlockNeedUserTest still carries the manual
checks stated in its ticket; queue placement is not proof that those checks or a release pre-flight passed.
