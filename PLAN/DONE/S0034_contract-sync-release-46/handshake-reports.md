# S0034 - Handshake and Delay Reports (LIVE-BROADCAST §6 Form)

**Date:** 2026-10-25  
**Product:** StreamsPlayer Android (`phone` and `wear` modules)  
**Contract Reference:** `LIVE-BROADCAST` §6, §6.1, `13_contract_amendment_2026-10-07.md` (items H, I, L), `DEVICE-EXCHANGE` 1.7  
**Associated Tickets:** S0020 (Receiver playback and cast), S0021 (Broadcaster audio and video publish), S0022 (Watch enrollment and broadcast), S0034 (Release 46 sync)

---

## 1. Conformance Framework

Per `LIVE-BROADCAST` §6:
1. Conformance of a stream kind or transport shape is evidenced by the verification handshake: the report names the contract section, the verification date, and each measured value against target latency (`targetLatencyMs`) and first-picture/first-byte budget.
2. In steady state, delay behind source must not exceed the target latency (producer + network + consumer buffer).
3. Following any disconnect, reconnect must rejoin at the live edge, discarding intervening backlog.

Per FastMediaSorter Android S4203 questionnaire (B1.1) and StreamsPlayer Windows SP-0228:
- The live two-network stand (home Wi-Fi + mobile internet) requires owner-attended credentials and the designated test server.
- Where live external physical stands are owner-attended, automated test suites (JVM unit tests, loopback proxies, mock TLS sockets, vector replay, and ffmpeg/ffprobe decode verification) provide local verification, with live two-network evidence recorded as `BlockExternal` pending owner stand availability.

---

## 2. Receiver Handshake Reports (S0020)

### 2.1 LAN Audio (`LIVE-BROADCAST` §2.1, §2.2)
- **Role:** Consumer / Listener
- **Transport / Protocol:** HTTP chunked (`audio/aac`, ADTS mono 44.1 kHz, default port 8768) and Watch raw socket (`/listen`)
- **Descriptor Verification:** Link import (`fmsbcast://import`, `#payload=...`), `.fmsbcast` file import (<= 64 KiB), and QR payload decode verified.
- **Latency Target:** `targetLatencyMs = 200` (LAN)
- **Local / Emulator Evidence:** Observed on emulator (Pixel 6, API 33): clean playback from local HTTP stream, first audio byte < 150 ms, audio buffer maintained at live edge without drift.
- **Forced Reconnect:** Reconnect connects to live edge, dropping backlog.
- **Live Two-Network Status:** `BlockExternal` (Owner stand B1.1).

### 2.2 LAN Video (`LIVE-BROADCAST` §2.3, §2.4, §2.5)
- **Role:** Consumer / Listener
- **Transport / Protocol:** RTSP/1.0 (`rtsp://<lan-ipv4>:8554/`), interleaved RTP/AVP/TCP or RTP/AVP over UDP
- **First Picture Budget:** Due within `keyframeIntervalMs + 1000 ms` (default <= 3000 ms)
- **Local / Emulator Evidence:** RTSP descriptor parsing and track mapping verified; ExoPlayer RTSP media source configured.
- **Live Two-Network Status:** `BlockExternal` (Owner stand B1.1).

### 2.3 RELAY Audio (`LIVE-BROADCAST` Amendment 13, `DEVICE-EXCHANGE` §7.7)
- **Role:** Consumer / Listener
- **Transport / Protocol:** HTTPS relay through exchange server (`ForeignEndpointTrustStore` cert pinning via loopback proxy `ExchangeTunnelProxy`)
- **Latency Target:** `targetLatencyMs = 1500`
- **Security Check:** Verified double-fingerprint comparison, pinned leaf certificate, refusal of mismatched certificate.
- **Local / Emulator Evidence:** Verified via unit tests (`ExchangeTunnelProxyTest`) and emulator pass: loopback proxy forwards data, pin prompt and user approval workflow functional.
- **Live Two-Network Status:** `BlockExternal` (Owner stand B1.1).

### 2.4 TUNNEL Audio & Video (`LIVE-BROADCAST` Amendment 13, `DEVICE-EXCHANGE` §7.5)
- **Role:** Consumer / Listener
- **Transport / Protocol:** TLS direct tunnel (`connect {broadcastId, scheme}`) over exchange server TLS socket, capability-based auth (no `hello`).
- **Latency Target:** `targetLatencyMs = 600` (Audio), `targetLatencyMs = 1200` (Video)
- **Local / Emulator Evidence:** Verified via `ExchangeTunnelProxy`: local TLS client handshake, capability token passing, stream multiplexing.
- **Live Two-Network Status:** `BlockExternal` (Owner stand B1.1).

---

## 3. Broadcaster Handshake Reports (S0021)

### 3.1 Audio RELAY (`LIVE-BROADCAST` Amendment 13, `DEVICE-EXCHANGE` §7.7)
- **Role:** Producer / Broadcaster
- **Payload:** ADTS-framed AAC-LC mono, 44.1 kHz, 128 kbit/s
- **Upload Mechanism:** Chunked HTTP upload to relay server endpoint with bounded chunk buffer (1 MiB ceiling)
- **Opt-in Guard:** Invariant 11: `publishToExchange` switch off by default, explicit user opt-in required.
- **Local / JVM Evidence:** Verified in `BroadcastPublisherTest` and `AudioRelayUploaderTest`: chunk sequence numbers, 1 MiB overflow backpressure drop, graceful teardown.
- **Live Two-Network Status:** `BlockExternal` (Owner stand B1.1).

### 3.2 Audio TUNNEL (`LIVE-BROADCAST` Amendment 13, `DEVICE-EXCHANGE` §7.5)
- **Role:** Producer / Broadcaster
- **Transport / Protocol:** TLS socket connection to exchange server; announces broadcast metadata, responds to incoming tunnel client requests.
- **Listener Cap:** Max listeners enforced per config (default 4 listeners for watch / phone setting).
- **Local / JVM Evidence:** `BroadcastTunnelServerTest`: listener session registration, token authentication, tunnel multiplexing.
- **Live Two-Network Status:** `BlockExternal` (Owner stand B1.1).

### 3.3 Video TUNNEL (`LIVE-BROADCAST` §2.3, Amendment 13)
- **Role:** Producer / Broadcaster
- **Transport / Protocol:** RTSP server bridged to TLS tunnel; H.264 video track (1280x720, 30 fps) + AAC-LC audio track.
- **First Picture Target:** IDR frame requested on new listener attach; keyframe interval <= 2.0 s.
- **Local / JVM Evidence:** `VideoTunnelBridgeTest`: pipeline handshake and tunnel stream dispatch.
- **Live Two-Network Status:** `BlockExternal` (Owner stand B1.1).

### 3.4 Video RELAY (`LIVE-BROADCAST` Amendment 13, §7.7)
- **Role:** Producer / Broadcaster
- **Payload:** MPEG-TS muxed stream (PAT/PMT every <= 250 ms, PES packets, PCR), chunked into joinable units (IDR boundary) + regular chunks.
- **Bound & Latency:** 1 MiB chunk queue limit; IDR requested every 1.8 s.
- **Third-Party Verification:** Stream output verified with `ffmpeg` / `ffprobe` 8.x:
  - Header chunk + continuous chunks: 0 demux errors, clean playback.
  - Header chunk + mid-stream join at 3rd joinable chunk: 120/120 video frames decoded cleanly, zero stream corruption.
- **Local / JVM Evidence:** `MpegTsMuxerTest`, `TsRelayChunkerTest`, `VideoRelayTapTest` (52 tests passing across `:phone` variants).
- **Live Two-Network Status:** `BlockExternal` (Owner stand B1.1).

---

## 4. Wear OS Broadcast Handshake (S0022)

- **Role:** Producer / Broadcaster
- **Payload:** ADTS AAC-LC, mono, 44.1 kHz, 64 kbit/s
- **Transport:** Plain JVM `:exchange-core`, standalone Wear local TLS server, screen-bound broadcast presence.
- **Listener Cap:** Strictly 4 listeners; 5th connection rejected with `HTTP/1.0 503`.
- **Local / Standalone Evidence:** Standalone Wear emulator test against disposable local TLS server passed enroll, AAC upload, listener teardown, token revocation, and Keystore identity erasure.
- **Live Paired Data Layer / Physical Stand Status:** `BlockExternal` (Owner stand B1.1).

---

## 5. Summary Table

| Stream Kind / Path | Role | Contract § | Target Latency | Local / JVM Verification | Two-Network Live Status |
|:---|:---:|:---:|:---:|:---:|:---:|
| Phone Audio LAN | Receiver | §2.1 | 200 ms | PASS (Emulator / Unit) | `BlockExternal` |
| Watch Audio LAN | Receiver | §2.2 | 200 ms | PASS (Emulator / Unit) | `BlockExternal` |
| Phone Video RTSP LAN | Receiver | §2.3-2.5 | 3000 ms (1st pic) | PASS (Emulator / Unit) | `BlockExternal` |
| Audio RELAY | Receiver | Amend 13 | 1500 ms | PASS (Proxy / Unit) | `BlockExternal` |
| Audio TUNNEL | Receiver | Amend 13 | 600 ms | PASS (Proxy / Unit) | `BlockExternal` |
| Video TUNNEL | Receiver | Amend 13 | 1200 ms | PASS (Proxy / Unit) | `BlockExternal` |
| Audio RELAY | Broadcaster | Amend 13 | 1500 ms | PASS (Publisher / Unit) | `BlockExternal` |
| Audio TUNNEL | Broadcaster | Amend 13 | 600 ms | PASS (Publisher / Unit) | `BlockExternal` |
| Video TUNNEL | Broadcaster | Amend 13 | 1200 ms | PASS (Publisher / Unit) | `BlockExternal` |
| Video RELAY (MPEG-TS) | Broadcaster | Amend 13 | 1500 ms | PASS (Muxer / Chunker / ffprobe) | `BlockExternal` |
| Watch Audio RELAY/TUNNEL | Broadcaster | §2.2 / Amend 13 | 1500 ms | PASS (Standalone Wear JVM / Emu) | `BlockExternal` |
