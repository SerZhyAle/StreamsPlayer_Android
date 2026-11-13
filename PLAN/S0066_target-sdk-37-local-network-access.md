# S0066 - target-sdk-37-local-network-access

**Ticket:** S0066
**Status:** BlockByOtherTask
**Status note:** Blocker: S0047 - package 1 stays at target 36; target 37 local network access work becomes the package right after the first Google Play release (S0047) is live.
**Complexity:** hard - revised from medium at the spec pass 2026-11-08: raising the target pulls in the whole API 37 behaviour set that only a target-37 device run can judge (background audio, TLS defaults, widget bitmaps), and the permission has five entry points across phone and watch plus one question the platform text does not answer (loopback, R1); the permission text surfaces are three locales and four generated or hand-kept documents.
**Date:** 2026-11-08
**Verification level:** gate - JVM tests for the pure parts (host classification, permission-state to action mapping) run at the release, plus a run of a target-37 build on the installed Android 17 emulator image: broadcast start with a listener on another device, playback of a LAN camera address, denial, later revocation, the loopback forwarders.
**Owner:** the StreamsPlayer Android session of this repository.

## Captured request and migration scope

Parked from S0062 (Google Play policy announcement of 2026-10-29), by the unattended queue pass of 2026-11-08.

Policy: Google Play Policy Deadlines table, row "Local Network Permission" (announced 2026-10-29), deadline 2027-08-25. An app that targets Android 17 (API 37) or higher may request `ACCESS_LOCAL_NETWORK` only when its core functionality needs it and the system device pickers are not enough, and it must submit a Play Console declaration that justifies the access (policy page `answer/16909972`, section "Local Network Permission").

Platform (developer.android.com/privacy-and-security/local-network-permission, read 2026-11-08): from API 37 the local network is blocked by default for an app that raises its target. An outgoing or incoming TCP connection and any UDP unicast, multicast or broadcast to or from a local address needs the runtime permission, in every networking API (raw sockets, OkHttp, libraries). An app that targets 36 or lower keeps access through the implicit grant that INTERNET gives and must not declare the permission yet.

This product targets 36 today (`gradle/libs.versions.toml`, `target-sdk`), so nothing is required now. This ticket is the work for the day the target moves to 37. Play's required target level is read in the console on that day, not assumed; the announced deadline is 2027-08-25.

What the code does on the local network (read 2026-11-08): the phone serves a live broadcast over HTTP on `0.0.0.0` (`BroadcastHttpServer`, NanoHTTPD) and over RTSP (`RtspServerCamera2`, `RtspAuthGate`), resolves its LAN address (`LanAddressResolver`), and the watch holds a Wi-Fi network while it serves a broadcast (`StreamNetworkHoldManager`). Playback opens whatever stream address the user types, including a camera on the user's own network. Inbound and outbound LAN traffic is core functionality, and no picker covers an address the user types or a listener that connects in, so the permission path applies and the declaration will be needed.

Needed: take the target to 37 in `phone` and `wear` after reading the API 37 behaviour changes; declare `ACCESS_LOCAL_NETWORK` in the phone and watch manifests; request it at runtime before the first local network traffic, with a plain-words reason and a handled denial or later revocation (TCP times out, UDP fails with EPERM); regenerate the permissions table of the privacy policy and the purposes sentence (`tools/privacy/permissions.ps1`), and keep the Data safety source and the help pages saying the same; the Play Console declaration is the owner's. The watch face has no code and no change.

## 1. Problem

At target 36 the product keeps its LAN access through the implicit grant that INTERNET gives, so today there is nothing to fix. The day the target is 37 the platform blocks every local-address connection by default: a listener on the user's network cannot reach a broadcast served by the phone or the watch (the inbound connection never completes), and playback of a camera or radio on the user's own network times out. The user would see a stalled player or an address nobody can open, with no sentence explaining why. The work is to take the target to 37 with the permission declared, asked in context, a denial and a later revocation answered in plain words, and every surface saying the same.

Package 1 (the first Google Play package, S0047) has not shipped, so no user has a copy that a target change could surprise; that is the fact the owner's question in section 4 turns on.

## 2. Goals, non-goals and constraints

Goals:

- **G1 target.** One `target-sdk` level for the three application modules, moved from 36 to 37 (the convention plugin reads a single pin; the watch face has no code and takes the same level without a change of behaviour).
- **G2 declaration.** `android.permission.ACCESS_LOCAL_NETWORK` declared in the phone and the watch manifests in the same edit as the bump, never before (the platform text forbids declaring it at a lower target).
- **G3 ask in context.** The permission is requested at the action that needs it, never at launch, only when `SDK_INT` is 37 or higher (the permission does not exist below; minSdk is 26 on the phone and 28 on the watch), with one plain sentence of why before the system prompt.
- **G4 denial and revocation.** The permission state is the authority, read before the action (not inferred from a timed-out connection). A denied or revoked permission produces a sentence and a way to the system settings of the app, and no silent stall.
- **G5 surfaces.** Privacy policy permissions table (generated), the purposes sentence in en, ru and uk, the Play Data safety source, the in-app strings in three locales and the help pages with their `help-map.json` rows, all in one edit with the manifest change.
- **G6 proof.** JVM tests for the pure parts, run at the release; a run of the target-37 build on the Android 17 emulator image for everything the platform decides (section 3.5).

Non-goals: adopting the `NsdManager` picker or the Output Switcher (resolved in 3.6: not applicable); the Play Console declaration and any console action (the owner's, with S0067); any release, tag or upload; the Windows product; a change to what a broadcast or a catalog carries on the wire.

Constraints that bind the work:

- `CLAUDE.md` section 2 (full port, never a rewrite, and no quiet divergence from FastMediaSorter): the playback and broadcast classes touched here are ported classes. Every added line in a ported file is a deliberate delta, written in this ticket next to its reason and in the port-ledger row; FastMediaSorter's own target-37 work is checked with `tools/port-ledger` drift before the change, so the two products do not drift on this.
- Canon invariant 13 and 14 (the permission list, the privacy page and the Data safety source say the same thing): the permission is added to `docs/privacy/permission-purposes.json` and the manifests in one edit, and `tools/privacy/permissions.ps1` is the check.
- Canon invariant 17 (every surface and every authored locale in one edit; text written as a shipped, present-tense fact).
- `CLAUDE.md` section 5a: nothing above 5 s runs between releases; the build, the unit suites and the emulator run are RELEASE items.

## 3. Research findings

### 3.1 Platform (read 2026-11-08, closed)

Sources: `developer.android.com/privacy-and-security/local-network-permission`, `developer.android.com/about/versions/17/behavior-changes-17`.

- The permission is `android.permission.ACCESS_LOCAL_NETWORK`, a runtime permission in the `NEARBY_DEVICES` group; a user who already granted another permission of that group is not asked again. Both the constant and the group are present in the installed `platforms/android-37.0/android.jar` (checked 2026-11-08).
- Covered: an outgoing TCP connection, an accepted incoming TCP connection, UDP unicast, multicast and broadcast in both directions, in every networking API (raw sockets, OkHttp, any library built on them), and resolving a `.local` name. A WebView inherits the host app's state.
- Not covered: traffic to a DNS server on port 53; an app that uses the Output Switcher as its picker; a service the user picks in the `NsdManager` picker (`DiscoveryRequest.FLAG_SHOW_PICKER`), whose addresses connect without the permission.
- Failure shape: a TCP connect times out; UDP fails with `EPERM`; the NDK has `android_getnetworkblockedreason`.
- Target 36 or lower: the implicit grant through INTERNET stays; the permission must not be declared or requested before the target moves.
- A prior denial of the group gets further chances to ask after a rationale (the platform's reset counter), so the rationale sentence is the right place to explain.
- Not stated in the documents: whether loopback (`127.0.0.1`) counts as the local network. Open, R1 below.

### 3.2 Where the tree touches the local network (read 2026-11-08)

| Module | Class | What it does | Permission at 37 |
|---|---|---|---|
| phone | `BroadcastHttpServer` | NanoHTTPD serving listeners on all interfaces | yes, inbound TCP |
| phone | `RtspGatedPort`, `RtspAuthGate` | RTSP port on all interfaces, forwarded to an inner port | yes, inbound TCP |
| phone | `LanAddressResolver` | reads the device's own interface addresses | no traffic; confirm in the run (R1 run) |
| phone | `AudioPlaybackService`, `FullscreenPlayerActivity` | outbound TCP to the address the user typed or the catalog holds | yes when the host is a local address |
| watch | `LiveAudioLanServer` | `ServerSocket` for up to four listeners | yes, inbound TCP (this class was not named in the captured request; added by this pass) |
| watch | `StreamNetworkHoldManager` | holds a Wi-Fi network while the watch serves | no traffic by itself; the server on it needs the permission |
| exchange-core | `ExchangeRelayProxy`, `ExchangeTunnelProxy`, `SealedRelayProxy` | `ServerSocket` on `127.0.0.1` for the in-process player; the network side goes out to the exchange server | R1 |

### 3.3 The tree's existing permission pattern (read 2026-11-08)

The phone asks for the camera and the microphone at the action: `BroadcastControlManager` registers `ActivityResultContracts.RequestPermission` launchers, keeps the pending start across the prompt and shows a message on denial; `QrScannerActivity` carries a rationale and a settings fallback. The watch uses `rememberMultiplePermissionsState` in `WearBroadcastScreen`, and only the microphone gates the broadcast. The new permission takes the same shape on both devices. Neither module has a helper that wraps a permission and an `SDK_INT` guard, so one small helper per module (or one in `exchange-core` if the watch and phone share the pure part) is the plan's first step.

### 3.4 Contract boundary (closed)

None crossed. No format, wire shape, algorithm constant or behaviour another product reads or reproduces changes. The catalog holds no rule for this permission: the only mention is `LAN-DISCOVERY`'s degradation clause for the FastMediaSorter mDNS client, which this product does not implement. A refused or reduced start is local interface behaviour, not what a listener reads. No catalog amendment is needed.

### 3.5 The other target-37 behaviour changes against this tree (read 2026-11-08)

- Large-screen orientation and resizability ignored: no `screenOrientation`, `requestedOrientation` or `resizeableActivity` anywhere in the phone and watch sources (grep, no match). Nothing to ignore. Closed.
- Static `static final` reflection, the lock-free `MessageQueue`, native `System.load` read-only rule: no use in the application sources of `phone`, `wear` and `exchange-core` (grep, no match). Libraries are judged by the run (D3).
- Bluetooth RFCOMM, Content Capture, background activity launch constants, contacts and SMS: no use (grep, no match; S0062 already measured contacts and SMS).
- Background audio hardening: the playback service is a foreground `mediaPlayback` service, and playback also starts from a widget and a quick-settings tile. Whether those starts keep while-in-use capability is judged on the device (D3).
- Widget bitmap memory cap: one `setImageViewBitmap` (`StreamLaunchWidgetProvider`, the plate icon). The size is read with `adb shell dumpsys appwidget` in the run (D3).
- Certificate transparency on by default and ECH: TLS to the catalog host, the exchange servers and Google endpoints. A server with a public certificate is unaffected; self-hosted exchange servers with a private certificate are outside CT. Judged in the run with one exchange fixture (D3).

Open item D3 is a device run, not a research question: it cannot close before the target is 37 and the build exists.

### 3.6 Resolved without asking

- **Pickers do not apply.** The `NsdManager` picker covers mDNS services; this product connects to an address the user typed and serves a listener that connects in. The Output Switcher covers casting; this product does not cast. The permission path (B) is the only one, which is what the captured request already says.
- **Where to ask.** In context, at the action, as the tree already does for the camera and the microphone and as the platform text recommends; not at launch. Settled by section 3.3 and the platform's reset-counter guidance.
- **Which `SDK_INT`.** 37 and higher, as a named constant; below it the permission does not exist and the helper reports "granted" without asking.
- **Denial on a broadcast.** The permission state is read before the foreground service starts. A denial removes the LAN address from what the broadcast offers and says so in a sentence with a way to the app's settings; when the broadcast would have no other endpoint (no exchange publication), the start is refused with the same sentence. This holds whatever R1 answers: R1 only decides whether the relay path survives a denial.
- **Denial on playback.** A host that is a literal private, link-local, unique-local or `.local` address is checked before the player opens it. A host name the app cannot classify without a DNS lookup is not checked; when its connection fails and the permission is denied, the failure sentence adds the local network hint.
- **Watch.** The watch is a separate app with its own manifest and its own prompt; nothing is sent over the Data Layer for this permission. It needs the permission for `LiveAudioLanServer` and for playing a LAN address.

### 3.7 Open research items

- **R1 - does loopback count?** `Status: Open, closes only on the Android 17 emulator image with a target-37 build.` The platform text names "local network addresses"; the three exchange forwarders listen on `127.0.0.1` for the in-process player, and `LanAddressResolver` only enumerates interfaces. Plan step: run the exchange tunnel playback and the broadcast relay upload with the permission denied; the outcome decides whether the permission is needed for the exchange path (a denied phone could then not play an exchange broadcast) or for LAN only. It does not change the declaration (the LAN paths need it regardless) and it does not block spec approval; it blocks the closing audit.
- **D3 - the device run of section 3.5.** A RELEASE item.

## 4. Open questions (owner)

### Quiz decisions (2026-11-10)
- When does the target move to 37 -> Option A (Package 1 stays at target 36; this ticket becomes the package right after the first release is live with Blocker: S0047, so the first release is not delayed by API 37 declarations and tests).

1. **When does the target move to 37?** `Status: Resolved (2026-11-10, quiz - Option A).` Package 1 is the first Google Play package and has not shipped (S0047 is Draft), so a target-37 first package would surprise no installed user, but it also puts the Android 17 behaviour set, a runtime permission with its console declaration, and an emulator proof on the path of the first publish. The policy date is 2027-08-25; Play's required target level is read in the console, not assumed.
   - **A. Recommended - package 1 stays at target 36; this ticket becomes the package right after the first release is live**, dated well before 2027-08-25. The first publish carries only what S0047 and S0067 already put on it; the ticket waits with `Blocker: S0047` and is worked as one package with its device run. The cost is that the first update changes the target for installed users, with the permission asked on first LAN use.
   - **B. Move to target 37 inside package 1**, before the first publish. No installed user is surprised and the migration never happens later; the cost is that the first publish needs the Play Console local network declaration accepted, plus the full Android 17 device run, before it can go.
   - **C. Leave it unscheduled (`--`) until the console shows a required target level that forces it.** Nothing is built now; the cost is that the work lands under the date pressure of 2027-08-25 with no installed-user data on how the permission is received.

## 5. Scope map for the plan pass (not a plan)

Independent of the answer once the answer is "do it": the permission helper and the `SDK_INT` guard (phone, watch), the broadcast start check and the denial sentence (phone `BroadcastControlManager` start path, watch `WearBroadcastScreen`), the playback pre-check and the failure hint (phone and watch players), the manifest lines and the `target-sdk` pin, the JVM tests for host classification and state mapping, `docs/privacy/permission-purposes.json` and the generated tables, `docs/play/PLAY_DATA_SAFETY.md`, strings in en, ru and uk, help pages for broadcast, player and the watch with their `help-map.json` rows, the port-ledger rows for every touched ported file. Depends on the answer: whether any of it is built in this package, and the order against S0047.

### Owner inputs (Approval gate)

- **Related tickets:** S0062 (the policy review that parked this), S0067 (the owner's console items; the local network declaration is the owner's, on the day the target moves), S0047 (the first Google Play release), S0033 (the permissions table generator and the purposes file), S0035 (in-app help and the help map).
