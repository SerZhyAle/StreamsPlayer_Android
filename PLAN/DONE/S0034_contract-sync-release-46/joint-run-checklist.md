# Device exchange - StreamsPlayer Android joint-run checklist (Release 46)

Owner tickets: FastMediaSorter Android S4213 (joint run #1, current transport, before end-to-end encryption)
and S4244 (final run of release 46, after S4240 encryption).
Cross-program checklist mapped to S4203 coverage matrix 5.4 and readiness criteria of section 11.

## Stand & Environment

- **Exchange server:** Owner's test server hosted by FMS_W on the workstation.
- **Accounts:** Account A (owner) and Account B (second account) on the same server.
- **Phone:** Galaxy S25 FE (owner's personal phone) / Galaxy S21+ test phone / Android Emulator API 34.
- **Watch:** Galaxy Watch 7 / Wear OS Emulator API 30.
- **PC:** FMS_W on workstation; StreamsPlayer for Windows.
- **Credentials & Secrets:** Test server connection string, login, and password live strictly in the owner questionnaire (В1.1); never copied into this repository, git tracking, or logs.
- **Build under test:** StreamsPlayer Android (`phone` and `wear`), current tree build (`v2.61.0020.000` sentinel).

---

## StreamsPlayer Android Run Matrix & Evidence

Result notation:
- `PASS (local / loopback / JVM)`: verified in this repository by automated unit tests or local disposable TLS emulator runs.
- `BlockExternal`: requires the owner's private stand, credentials, and physical devices in two networks (home Wi-Fi and mobile internet). Live stand confirmed unavailable on 2026-10-14; FMS_A S4213 is `BlockNeedUserTest` (archived), FMS_A S4244 is `BlockExternal`.

### 1. Enrolment and Entry

| Case | Description | Network | S4213 Result | S4244 Result | Evidence / Notes |
|---|---|---|---|---|---|
| 1.1 | Phone adds server by pasting `FMSEXCH1:` string; fingerprint shown and accepted; account devices appear | W, M | BlockExternal | BlockExternal | Local unit test: `ExchangeConnectionStringVectorsTest` (221 cases PASS), `ExchangeViewModelTest`, `ExchangeImportActivityTest`. |
| 1.2 | Same entry by QR scanned in app | W | BlockExternal | BlockExternal | Local camera / QR unit tests in S0023. |
| 1.3 | Same entry by opening `.fmsexch` file and by "Share" text | W | BlockExternal | BlockExternal | Local intent handling in `ExchangeImportActivityTest`. |
| 1.4 | `FMSEXCH2:` string refused with "update the app" / format-unsupported | W | PASS (JVM) | PASS (JVM) | Pinned in `ExchangeConnectionStringCodecTest` (`format-unsupported`). |
| 1.5 | "Connect watch" from phone menu enrols watch; watch lists entry | W | BlockExternal | BlockExternal | Local tests: `ConnectWatchToExchangeEntryUseCaseTest`, `WearExchangeHandoverCodecTest`. |
| 1.6 | Watch refuses server whose cert differs from phone trust decision | W | BlockExternal | BlockExternal | Standalone Wear local TLS run in S0022 passed enroll and cert pinning. |
| 1.7 | Phone and watch logs contain no pairing code, token, password or secret | W, M | PASS (JVM) | PASS (JVM) | Pinned in `DeviceExchangeRedactionTest`. |

### 5. Broadcast from Phone

| Case | Description | Network | S4213 Result | S4244 Result | Evidence / Notes |
|---|---|---|---|---|---|
| 5.1 | Phone broadcasts audio through server (`RELAY`); listeners receive from account list | W, M | BlockExternal | BlockExternal | S0021 audio relay muxer/chunker implemented; tested in `AdtsFrameSplitterTest`, `RelayChunkWriterTest`. |
| 5.2 | Phone broadcasts camera video through server (`TUNNEL` / `RELAY`); first picture within budget | W, M | BlockExternal | BlockExternal | S0021 `MpegTsMuxerTest` (PAT/PMT, PES, PCR), `TsRelayChunker`, third-party ffmpeg/ffprobe demuxer 120/120 frames PASS. |
| 5.3 | Broadcast appears in account list at start and disappears at stop | M | BlockExternal | BlockExternal | S0021 publish lifecycle and S0028 list handling. |
| 5.4 | StreamsPlayer (Windows) and StreamsPlayer_mob receive same broadcast | M | BlockExternal | BlockExternal | Outside descriptor playback in S0020; cross-product joint check BlockExternal. |

### 6. Receiving on Phone

| Case | Description | Network | S4213 Result | S4244 Result | Evidence / Notes |
|---|---|---|---|---|---|
| 6.1 | Phone receives broadcast from account list | W, M | BlockExternal | BlockExternal | S0020 / S0028 directory playback. |
| 6.2 | Phone imports broadcast descriptor of account B by link, QR, file, text; plays outside network via `RELAY` / `TUNNEL` | M | BlockExternal | BlockExternal | S0020 emulator tests: link import, LAN HTTP, loopback relay proxy, tunnel proxy. |
| 6.3 | Self-signed server descriptor carries cert fingerprint; mismatching leaf refused | M | PASS (JVM) | PASS (JVM) | Pinned in `DeviceExchangeTrustRuleTest` and `ForeignEndpointTrustStore`. |

### 7. Cast

| Case | Description | Network | S4213 Result | S4244 Result | Evidence / Notes |
|---|---|---|---|---|---|
| 7.1 | Cast offer to phone: prompt asked; accept plays, decline answers `declined` | W, M | BlockExternal | BlockExternal | S0020 cast evaluator (`declined`, `timeout`, `unsupported`). |
| 7.2 | Phone casts broadcast to watch; watch asks; accept plays relay stream | W, M | BlockExternal | BlockExternal | S0022 watch cast evaluation. |
| 7.3 | Stopping offer or broadcast removes prompt and stops playback | W | BlockExternal | BlockExternal | S0020 / S0022 teardown flow. |

### 8. Watch Broadcast

| Case | Description | Network | S4213 Result | S4244 Result | Evidence / Notes |
|---|---|---|---|---|---|
| 8.1 | Watch broadcasts microphone audio with outgoing entry | W, M | BlockExternal | BlockExternal | S0022 standalone Wear run on local TLS: 44.1 kHz mono AAC upload, teardown, revoke. |
| 8.2 | Phone and FMS_W hear through `RELAY` outside home network | M | BlockExternal | BlockExternal | Awaiting owner stand and multi-product run. |
| 8.3 | Listener limit 4 across paths: 5th listener refused | W | PASS (JVM) | PASS (JVM) | Pinned in S0021/S0022 `BroadcastSessionConfig` and `ExchangeAccountManager`. |

### 11. Built but Never Run Together (Direct & Other Receivers)

| Case | Description | Network | S4213 Result | S4244 Result | Evidence / Notes |
|---|---|---|---|---|---|
| 11.4 | StreamsPlayer_mob receives phone broadcast through server (S0020) | M | BlockExternal | BlockExternal | S0020 outsider receiver playback. |
| 11.5 | StreamsPlayer_mob watch broadcasts audio to phone (S0007 / S0022) | W | BlockExternal | BlockExternal | S0007 / S0022 Wear audio broadcast. |

---

## Server Corpora Conformance Evidence (DoD Item 3)

The following server transcript corpora from the shared catalog are vendored byte for byte under `phone/src/test/resources/device-exchange/` and replayed by automated unit tests:

1. `server-records-v1.json` (SHA-256 `2b0edd5dca31e91fc5b828fc5707d518c3b796bdf02f0bd28738e72108113e00`):
   Replayed by `DeviceExchangeServerRecordsReplayTest` (6 tests). Every accepted device and resource record decodes, verifies presence, and bounds checks; broadcast stand-in records handled; refused/closed cases verified.
2. `server-frames-v2.json` (SHA-256 `afa1a68273670f98c5dd615261759d1853a4cafd9086254e23acbdc7d0aea107`):
   Replayed by `DeviceExchangeServerFramesReplayTest` (5 tests). All 7 transcripts (`enroll-and-hello`, `pairing`, `refusals`, `directory`, `tunnels`, `relay`, `cast`), constants (`maxFrame`, `maxFrameAuth`, `maxRecord`), framing length words, envelopes and refusals verified.
3. `fmsw-224/frames-v2.json` (SHA-256 `f5c6ae0db7dd47454467184c5df3378104e35bfe09609acfd37f703e833541d8`):
   Replayed by `DeviceExchangeServerFramesReplayTest`. Transcripts `registration`, `password`, `pace` replayed.
4. Conformance vectors check: `tools/contracts/check-sync.ps1 -CatalogRoot P:\Contracts` verifies all 7 `DEVICE-EXCHANGE` vectors byte-identical (PASS).
