# WebRTC DataChannel Transport Research

> Research summary for issue #700

## Status

Research complete (2026-04-09)

---

## Background

KMP-FaceLink's primary use case is **phone-as-tracker**: a mobile device runs face/hand tracking and streams structured data (blend shapes, head pose, hand landmarks) to a desktop VTuber application. The current transport is WebSocket over TCP (`kmp-facelink-stream`), which works well under ideal conditions but suffers under WiFi packet loss due to TCP's head-of-line blocking.

WebRTC DataChannel addresses two key problems:

1. **Tail latency under packet loss** — WiFi at 1-5% loss causes TCP retransmits that stall all subsequent frames for 50-200ms. WebRTC's unreliable/unordered mode drops stale frames instead, keeping latency at 1-3ms.
2. **NAT traversal** — Built-in ICE/STUN/TURN support enables phone-to-desktop connections across different subnets without port forwarding, enabling a future browser-to-desktop use case.

This research builds on the earlier investigation in [`docs/research/webrtc-streaming.md`](webrtc-streaming.md) (issue #304) and the transport abstraction implemented in issue #370.

---

## KMP WebRTC Library Landscape

| Library | Status | Targets | DataChannel | Binary Size | Verdict |
|---|---|---|---|---|---|
| **shepeliev/webrtc-kmp** | Active (v0.125.x) | Android, iOS, JS, WasmJS | Yes — binary fix PR #191 (Feb 2026) | ~8-15MB (bundles libwebrtc) | **Recommended — evaluate first** |
| **Ktor WebRTC Client** (3.3.0+) | Experimental (Jan 2026) | JS/Wasm, Android, iOS | Yes (`WebRtcDataChannel`) | Bundled with Ktor | **Too early for production** |
| **Google libwebrtc** (direct) | Stable, actively maintained | Android (AAR), iOS (CocoaPods/SPM) | Full support | ~8-15MB/platform | **Fallback — 2-3x more work** |
| **AnyConnect/WebRTC-KMP** | Archived (2025), stale at M104 | Android, iOS, JS | Yes | — | **Not recommended** |
| **libdatachannel** (C library) | Active, lightweight | C/C++ with bindings | DataChannel-focused | ~1-2MB | **Future binary size optimization** |

### Key Findings

**shepeliev/webrtc-kmp** (v0.125.x) is a community-maintained KMP wrapper around Google libwebrtc. It targets Android, iOS, JS, and WasmJS and exposes a common Kotlin API for `PeerConnection` and `DataChannel`. A DataChannel binary handling bug was fixed in PR #191 (February 2026) — verify the fix is included in the release used before starting implementation.

**Ktor WebRTC** (3.3.0) introduced experimental WebRTC support, but it launched in January 2026 and is not yet production-ready. The `DataChannelTransport` abstraction already in the codebase makes switching to Ktor WebRTC trivial once it matures.

**Direct libwebrtc** (expect/actual) remains the fallback if webrtc-kmp proves insufficient. It requires writing Android (`org.webrtc:google-webrtc` Java API) and iOS (ObjC API via Swift/SKIE bridge) wrappers separately — roughly 2-3x the implementation effort.

**libdatachannel** is a lightweight C library (~1-2MB) focused on data channels. It is worth evaluating in a later phase to reduce the binary size cost of bundling full libwebrtc.

---

## Existing Infrastructure

Issues #304 and #370 already implemented the transport abstraction layer. The following components exist in `kmp-facelink-stream` `commonMain`:

| Component | File | Description |
|---|---|---|
| `DataChannelTransport` | `webrtc/DataChannelTransport.kt` | Interface: `connect()`, `send(ByteArray)`, `incoming: Flow<ByteArray>`, `disconnect()`, `connectionState: StateFlow<ConnectionState>` |
| `WebSocketTransport` | `webrtc/WebSocketTransport.kt` | Production `DataChannelTransport` backed by Ktor WebSocket; reference implementation |
| `SignalingMessage` | `webrtc/SignalingMessage.kt` | Sealed class: `Offer`, `Answer`, `IceCandidate`, `IceGatheringComplete`, `Error` (JSON-serializable) |
| `WebRtcSignalingClient` | `webrtc/WebRtcSignalingClient.kt` | WebSocket-based signaling client for SDP offer/answer and ICE candidate exchange |
| `WebRtcTransportConfig` | `webrtc/WebRtcTransportConfig.kt` | Config with `signalingUrl`, `iceServers: List<IceServer>`, `dataChannelConfig: DataChannelConfig` |
| `DataChannelMessage` | `webrtc/DataChannelMessage.kt` | 14-byte binary envelope (magic, version, sequence number, send timestamp) wrapping `BinaryFrameCodec` payloads |

**What remains:** A concrete `WebRtcDataChannelTransport` implementation that wires up an actual WebRTC library, plus a `/signaling` endpoint in `StreamServer`.

---

## Architecture Recommendation

### Option A — shepeliev/webrtc-kmp (Recommended)

```
commonMain:
  DataChannelTransport (existing interface)
    └── WebRtcDataChannelTransport (new, wraps webrtc-kmp PeerConnection + DataChannel)
  WebRtcSignalingClient (existing) → exchanges SDP/ICE via WebSocket
  DataChannelMessage (existing) → binary envelope with sequence tracking

No androidMain/iosMain expect/actual needed — webrtc-kmp handles platform abstraction.
```

### Option B — Direct libwebrtc + expect/actual (Fallback)

```
commonMain:
  DataChannelTransport (existing interface)
    └── expect class WebRtcDataChannelTransport
androidMain:
  actual class WebRtcDataChannelTransport (org.webrtc:google-webrtc Java API)
iosMain:
  actual class WebRtcDataChannelTransport (GoogleWebRTC ObjC via Swift/SKIE)
```

Option A is preferred because the `DataChannelTransport` abstraction makes the underlying library swappable at any time, and webrtc-kmp eliminates the need for platform-specific wrappers.

---

## Signaling Server Design

Reuse the existing `StreamServer` WebSocket infrastructure as the signaling channel:

1. Add `/signaling` WebSocket endpoint to `StreamServer` (Ktor CIO, already running)
2. Phone (sender) creates `RTCPeerConnection`, generates SDP offer
3. PC (receiver) connects to `/signaling`, receives offer, sends SDP answer back
4. ICE candidates exchanged bidirectionally over the same WebSocket
5. DataChannel opens — tracking data flows over SCTP/DTLS/UDP
6. Signaling WebSocket stays open for reconnect/ICE restart triggers

**DataChannel config (recommended):**

```kotlin
DataChannelConfig(
    ordered = false,          // No ordering guarantee
    maxRetransmits = 0,       // Unreliable — no retransmission
    label = "tracking",
)
```

Each `StreamFrame` is self-contained full state (not deltas), so dropped frames cause smooth degradation with no state corruption.

**ICE / NAT traversal:**

- **LAN (same subnet):** Host ICE candidates suffice; no STUN needed.
- **Different subnets / mobile hotspot:** Include `stun:stun.l.google.com:19302` as a default STUN server.
- **TURN:** Only required for symmetric NAT, which is rare in this use case. Self-hosted Coturn recommended if TURN support is needed.

---

## Performance Analysis

### Transport Protocol Comparison

| Property | WebSocket (TCP) | WebRTC DataChannel (unreliable) | Raw UDP |
|---|---|---|---|
| Transport | TCP | SCTP/DTLS/UDP | UDP |
| Encryption | TLS (optional) | DTLS (mandatory) | None |
| Head-of-line blocking | Yes | No | No |
| NAT traversal | Requires port forwarding | Built-in (ICE/STUN/TURN) | Requires port forwarding |
| Browser compatibility | Yes | Yes | No |
| Setup complexity | Low | High (ICE + DTLS + SCTP) | None |

### Latency Under Packet Loss (LAN, Same Subnet)

| Scenario | WebSocket (TCP) | WebRTC DataChannel (unreliable) | Improvement |
|---|---|---|---|
| Normal (0% loss) | 3-8ms | 1-3ms | 3-5ms |
| 1% packet loss (typical WiFi) | 15-50ms | 1-3ms | **12-47ms** |
| 5% packet loss (interference) | 30-100ms | 1-3ms | **27-97ms** |
| WiFi burst interference | 100-500ms | 5-20ms | **95-480ms** |

### Key Insight

The primary value of WebRTC DataChannel over WebSocket is **not** the normal-case latency improvement (3-5ms). It is the **tail latency under packet loss**.

WiFi networks commonly experience 1-5% packet loss from interference. TCP's head-of-line blocking causes a single dropped packet to stall all subsequent frames for 50-200ms — a retransmission window of 2-6 dropped frames at 30 FPS, visible as avatar "freezing." WebRTC's unreliable/unordered mode simply discards the stale frame and delivers the next one immediately, maintaining consistent 1-3ms transport latency.

---

## Implementation Plan

### Phase 1: WebRTC PoC with webrtc-kmp (5-7 days)

| Task | Estimate | Notes |
|---|---|---|
| Evaluate webrtc-kmp DataChannel API | 1d | Verify binary send/receive, unreliable mode, build compatibility; confirm PR #191 fix is released |
| `WebRtcDataChannelTransport` implementation | 2d | Wrap webrtc-kmp `PeerConnection` + `DataChannel` behind existing `DataChannelTransport` interface |
| `/signaling` WebSocket endpoint in `StreamServer` | 1d | Reuse Ktor CIO server; exchange `SignalingMessage` JSON with existing `WebRtcSignalingClient` |
| Integration test: Android → desktop end-to-end | 1-2d | Send/receive `DataChannelMessage`-wrapped `StreamFrame` with latency measurement |
| WebSocket fallback on WebRTC failure | 0.5d | Auto-detect DataChannel failure; reconnect via `WebSocketTransport` |

**Phase 1 success criteria:**
- WebRTC DataChannel sends/receives `StreamFrame` on Android
- Measured latency improvement under 1% simulated packet loss
- Graceful fallback to WebSocket when WebRTC connection fails

### Phase 2: Production Hardening + iOS Verification (4-6 days)

| Task | Estimate | Notes |
|---|---|---|
| iOS DataChannel verification | 1d | webrtc-kmp supports iOS; verify unreliable DataChannel works end-to-end |
| Auto-reconnect + ICE restart | 1-2d | Handle WiFi roaming, network changes, ICE failure recovery |
| Adaptive transport selection | 1d | Probe WebRTC capability at startup; fall back to WebSocket if unavailable |
| Sample app integration (Android + iOS) | 1d | Toggle WebSocket / WebRTC in sample UI |
| Latency benchmarking tool | 1d | Round-trip measurement comparing WebSocket vs WebRTC vs VMC (UDP) |

### Phase 3: Binary Size Optimization (Optional, 2-4 days)

| Task | Estimate | Notes |
|---|---|---|
| Extract `kmp-facelink-stream-webrtc` Gradle module | 1d | Make WebRTC dependency opt-in; `kmp-facelink-stream` stays lightweight |
| Evaluate libdatachannel via JNI/cinterop | 2-3d | ~1-2MB vs ~8-15MB for libwebrtc; assess API coverage |

**Total estimated effort:** 10-14 days (Phase 1+2), 13-17 days including Phase 3.

---

## Risks & Open Questions

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| webrtc-kmp PR #191 binary fix not yet released | Medium | High | Verify fix is in release before starting; fallback to direct libwebrtc |
| webrtc-kmp does not support `maxRetransmits: 0` (unreliable mode) on both platforms | Medium | High | Verify DataChannel config API; fallback to `maxPacketLifeTimeMs` approach |
| webrtc-kmp adds ~8-15MB binary size unconditionally | Certain | Medium | Phase 3: extract to optional `kmp-facelink-stream-webrtc` Gradle module |
| Ktor WebRTC Client matures and becomes better option | Medium | Low | `DataChannelTransport` abstraction makes library swaps trivial |
| ICE negotiation fails on restrictive networks | Low | Medium | WebSocket fallback always available; include STUN server by default |
| webrtc-kmp maintenance slows or stops | Low-Medium | Medium | Abstraction layer isolates codebase; migrate to direct libwebrtc or Ktor WebRTC |

**Open questions:**

1. Has webrtc-kmp PR #191 (DataChannel binary handling fix) been merged and released in v0.125.x? Verify before starting Phase 1.
2. Does webrtc-kmp expose `DataChannelInit` with `maxRetransmits` on both Android and iOS? Confirm unreliable mode is fully supported.
3. Should browser receivers (JS/Wasm) be supported in scope? webrtc-kmp's JS target would cover this; direct libwebrtc would not.
4. Has the `DataChannelTransport` abstraction from issue #370 been merged to master, or is it still in-flight?

---

## References

- Issue #304 — Initial WebRTC research and transport abstraction design
- Issue #370 — Transport abstraction implementation (`DataChannelTransport`, `WebSocketTransport`, `SignalingMessage`, etc.)
- [`docs/research/webrtc-streaming.md`](webrtc-streaming.md) — Comprehensive earlier research document
- [shepeliev/webrtc-kmp](https://github.com/shepeliev/webrtc-kmp) — KMP WebRTC library (v0.125.x)
- [Ktor WebRTC (3.3.0 changelog)](https://ktor.io/changelog/) — Experimental WebRTC DataChannel support
- [WebRTC Data Channels (MDN)](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel)
- [SCTP-based Data Channels (RFC 8831)](https://datatracker.ietf.org/doc/html/rfc8831)
- [libdatachannel](https://github.com/nicedcm/libdatachannel) — Lightweight C DataChannel library
- [WebRTC Latency Analysis (nanocosmos)](https://www.nanocosmos.net/blog/webrtc-latency/)
