# WebRTC Streaming Research

**Issue:** #304
**Status:** Research complete
**Date:** 2026-03-24

## Executive Summary

This document evaluates adding WebRTC as an alternative transport to the existing WebSocket-based `kmp-facelink-stream` module for phone-to-PC tracking data streaming. The focus is on **WebRTC data channels** for structured tracking data (blend shapes, head pose, hand landmarks), not video/audio media streams.

**Key finding:** WebRTC data channels can reduce end-to-end latency by 30-50% compared to WebSocket in typical local network scenarios (from ~15-30ms to ~5-15ms) by using UDP transport with optional unreliable/unordered delivery. However, the signaling complexity and limited KMP library support make a phased approach advisable: start with platform-native WebRTC libraries (Google libwebrtc) wrapped behind a common Kotlin interface, with a simplified local-network signaling mechanism.

---

## 1. Current WebSocket Architecture

### How It Works

The existing `kmp-facelink-stream` module uses Ktor CIO WebSocket:

- **Server:** `StreamServer` runs a Ktor CIO HTTP server with a WebSocket endpoint at `/tracking`
- **Sender:** `StreamSender` collects from face/hand tracking flows, rate-limits to target FPS, and broadcasts `StreamFrame` to all connected clients
- **Encoding:** JSON (human-readable) or compact binary (242 bytes for face-only frame)
- **Transport:** TCP via WebSocket (Ktor CIO engine)
- **Discovery:** Manual IP:port configuration (port 21412 default); mDNS discovery added in #302

### Current Latency Profile

| Component | Estimated Latency |
|---|---|
| Tracking computation (MediaPipe/ARKit) | 5-15ms |
| Frame serialization (binary codec) | <0.1ms |
| WebSocket send (TCP) | 1-5ms (LAN) |
| TCP acknowledgment + head-of-line blocking | 2-10ms |
| Client deserialization | <0.1ms |
| **Total transport (send to receive)** | **3-15ms** |
| **Total glass-to-glass** | **10-30ms** |

### Strengths

- Simple implementation (~170 lines in `StreamServer`)
- Ktor is already a project dependency
- Reliable delivery (TCP guarantees)
- Works across all network types (LAN, WAN, cellular)
- JSON format is easy to debug

### Weaknesses

- **Head-of-line blocking:** A single dropped TCP packet stalls all subsequent frames until retransmission completes (~10-50ms penalty)
- **TCP overhead:** Three-way handshake, Nagle's algorithm (mitigatable with TCP_NODELAY), congestion window ramp-up
- **No built-in congestion control for real-time data:** TCP's congestion control optimizes throughput, not latency
- **Stale data delivery:** Old frames are always delivered even when newer data is available; for real-time tracking, dropping stale frames is preferable

---

## 2. WebRTC Data Channels for Structured Data

### Why Data Channels (Not Media Tracks)

WebRTC media tracks (audio/video) are designed for encoded media streams with their own codec pipelines. KMP-FaceLink sends **structured numeric data** (52 floats + 6 floats per frame = 242 bytes binary), which maps naturally to WebRTC data channels:

- **Data channels** use SCTP over DTLS over UDP
- Support **unreliable + unordered** delivery mode (equivalent to UDP with encryption)
- Support **reliable + ordered** delivery mode (equivalent to TCP with encryption)
- Message-oriented (not stream-oriented), aligning with our frame-per-message design
- No codec overhead, no jitter buffer, no media pipeline

### Delivery Mode Options

| Mode | Ordered | Reliable | Use Case |
|---|---|---|---|
| Reliable + Ordered | Yes | Yes | Same as TCP/WebSocket; no latency benefit |
| Unreliable + Unordered | No | No | Best for real-time tracking; stale frames dropped automatically |
| Unreliable + Ordered | Yes | No | Frames arrive in order but can be dropped |
| Reliable + Unordered | No | Yes | All frames delivered, order doesn't matter |

**Recommendation:** Use **unreliable + unordered** for tracking data. Each `StreamFrame` is self-contained (contains full blend shape state, not deltas), so dropped frames cause no state corruption. The receiver always has the latest data.

### Data Channel Configuration

```
maxRetransmits: 0        // No retransmission (unreliable)
ordered: false           // No ordering guarantee
maxPacketLifeTime: 50ms  // Drop frames older than 50ms (optional alternative to maxRetransmits)
```

### Frame Size Considerations

- Current binary frame size: 242 bytes (face-only), ~500 bytes (face + 2 hands)
- SCTP maximum message size: 256KB (configurable)
- UDP MTU on LAN: ~1500 bytes (Ethernet), ~1200 bytes (safe minimum)
- All KMP-FaceLink frames fit within a single UDP packet -- no fragmentation needed

---

## 3. Available WebRTC Libraries

### 3.1 Google libwebrtc (Recommended)

| Property | Value |
|---|---|
| Maintainer | Google (WebRTC.org) |
| Language | C++ core with Java/ObjC bindings |
| Android | Official AAR via Maven (`org.webrtc:google-webrtc`) |
| iOS | Official framework via CocoaPods/SPM (`GoogleWebRTC`) |
| Data channels | Full support |
| Binary size | ~8-15MB per platform |
| License | BSD-3-Clause |

**Android integration:**
```kotlin
// androidMain
implementation("org.webrtc:google-webrtc:1.0.32006") // or latest M-release
```

**iOS integration:**
```ruby
# Podfile or SPM
pod 'GoogleWebRTC', '~> 1.1'
```

**KMP integration approach:**
- `expect`/`actual` pattern: common interface in `commonMain`, platform wrappers in `androidMain`/`iosMain`
- Android: Use Java API directly (`PeerConnection`, `DataChannel`)
- iOS: Use ObjC API via Kotlin/Native cinterop or Swift wrapper (SKIE bridges)

### 3.2 WebRTC KMP (webrtc-kmp)

| Property | Value |
|---|---|
| Repository | [AnyConnect/WebRTC-KMP](https://AnyConnect.github.io/WebRTC-KMP/) (archived 2025) |
| Approach | KMP wrapper around libwebrtc |
| Targets | Android, iOS, JS |
| Status | Archived; last release targeted WebRTC M104 |

**Assessment:** Not recommended. The project is archived and significantly behind the current libwebrtc releases. Using it would introduce a stale dependency.

### 3.3 Oasis WebRTC KMP

| Property | Value |
|---|---|
| Repository | [aspect-build/oasis-webrtc-kmp](https://github.com/aspect-build/oasis-webrtc-kmp) |
| Approach | Newer KMP wrapper for libwebrtc |
| Status | Active development (as of early 2026) |
| Targets | Android, iOS |

**Assessment:** Worth monitoring but too early for production use. API surface may change.

### 3.4 Pion (Go) / libdatachannel (C)

| Library | Language | Relevance |
|---|---|---|
| Pion WebRTC | Go | Server-side only; useful if PC receiver is Go-based |
| libdatachannel | C | Lightweight C library; could be used via cinterop on iOS, JNI on Android |

**libdatachannel** is notable for being much smaller than full libwebrtc (~1-2MB vs ~8-15MB) and focusing on data channels + media. Could be a future option to reduce binary size.

### Recommendation

Use **Google libwebrtc** directly with `expect`/`actual` wrappers. It is the most mature, best documented, and actively maintained option. The binary size cost (~8-15MB) is acceptable for a tracking SDK that already bundles MediaPipe (~10MB).

---

## 4. Signaling for Local Network

### The Signaling Problem

WebRTC requires a signaling mechanism to exchange SDP offers/answers and ICE candidates between peers before the data channel can be established. On the public internet, this typically involves a signaling server (WebSocket, HTTP, or similar). For KMP-FaceLink's primary use case (phone streaming to PC on the same LAN), simpler approaches are viable.

### 4.1 WebSocket-Based Signaling (Recommended)

Reuse the existing `StreamServer` WebSocket infrastructure for signaling:

```
1. Phone starts StreamServer (already running for WebSocket streaming)
2. PC connects via WebSocket to /signaling endpoint
3. Phone creates RTCPeerConnection, generates SDP offer
4. Phone sends SDP offer over WebSocket
5. PC receives offer, creates answer, sends back over WebSocket
6. ICE candidates exchanged over WebSocket
7. WebRTC data channel opens
8. WebSocket signaling connection can be closed (or kept for control messages)
9. Tracking data flows over WebRTC data channel
```

**Advantages:**
- No additional infrastructure needed
- Ktor WebSocket server already exists
- Signaling is a one-time setup cost (~100-500ms)
- Falls back to WebSocket streaming if WebRTC fails

### 4.2 mDNS + HTTP Signaling

Use the mDNS discovery from #302 combined with simple HTTP POST endpoints:

```
1. Phone advertises via mDNS (already implemented)
2. PC discovers phone, sends HTTP POST to /offer with SDP
3. Phone responds with SDP answer
4. ICE candidates exchanged via HTTP polling or SSE
5. Data channel opens
```

**Advantages:** Stateless, no persistent connection for signaling.
**Disadvantages:** ICE candidate exchange is awkward over HTTP; higher setup latency.

### 4.3 Pre-Shared Connection (Manual)

For local development and testing, a direct connection using known IP addresses:

```
1. User enters phone IP on PC (or vice versa)
2. Minimal SDP exchange via clipboard/QR code
3. ICE candidates for LAN are predictable (host candidates only)
```

**Assessment:** Useful for testing but not production-ready.

### 4.4 STUN/TURN Considerations

For local network use:
- **STUN:** Not needed on LAN (host ICE candidates suffice). Include a public STUN server as fallback for NAT traversal if peers are on different subnets.
- **TURN:** Not needed for local network. Only required for relay when direct connection fails (symmetric NAT, restrictive firewall).
- **ICE Lite:** The phone (acting as "server") can use ICE Lite mode to simplify the ICE process, since it has a known local IP.

### Recommendation

**WebSocket-based signaling** is the clear winner: it reuses existing infrastructure, provides a natural WebSocket fallback, and keeps the architecture simple. The signaling handshake adds ~100-500ms one-time setup cost, which is negligible for a persistent streaming session.

---

## 5. Latency Comparison

### 5.1 Transport Protocol Comparison

| Property | WebSocket (TCP) | WebRTC Data Channel (Unreliable) | Raw UDP |
|---|---|---|---|
| Transport | TCP | SCTP/DTLS/UDP | UDP |
| Encryption | TLS (optional) | DTLS (mandatory) | None |
| Reliability | Guaranteed | Configurable | None |
| Ordering | Guaranteed | Configurable | None |
| Head-of-line blocking | Yes | No (unreliable mode) | No |
| Congestion control | TCP congestion control | SCTP congestion control | None |
| Setup complexity | Low (HTTP upgrade) | High (ICE + DTLS + SCTP) | None |
| NAT traversal | Requires port forwarding | Built-in (ICE/STUN/TURN) | Requires port forwarding |
| Browser compatibility | Yes | Yes | No |

### 5.2 Measured Latency Estimates (LAN, Same Subnet)

| Scenario | WebSocket | WebRTC (unreliable) | Raw UDP |
|---|---|---|---|
| Normal (no packet loss) | 3-8ms | 1-3ms | 0.5-2ms |
| 1% packet loss | 15-50ms (retransmit) | 1-3ms (frame dropped) | 0.5-2ms (frame dropped) |
| 5% packet loss | 30-100ms (retransmit storm) | 1-3ms (frames dropped) | 0.5-2ms (frames dropped) |
| Network congestion | 50-200ms (TCP backoff) | 5-15ms (SCTP adapts) | Unpredictable |
| WiFi interference burst | 100-500ms (TCP stall) | 5-20ms (drops + recovery) | Packet loss |

### 5.3 End-to-End Latency (Glass-to-Glass)

| Component | WebSocket | WebRTC (unreliable) | Raw UDP |
|---|---|---|---|
| Tracking computation | 5-15ms | 5-15ms | 5-15ms |
| Serialization | <0.1ms | <0.1ms | <0.1ms |
| Transport (LAN, normal) | 3-8ms | 1-3ms | 0.5-2ms |
| Deserialization | <0.1ms | <0.1ms | <0.1ms |
| **Total (normal)** | **8-23ms** | **6-18ms** | **6-17ms** |
| **Total (1% loss)** | **20-65ms** | **6-18ms** | **6-17ms** |

### 5.4 Key Insight

The primary latency advantage of WebRTC over WebSocket is not in the normal case (3-5ms improvement), but in the **tail latency under packet loss**. WiFi networks commonly experience 1-5% packet loss from interference, and TCP's retransmission + head-of-line blocking causes latency spikes of 50-200ms. WebRTC's unreliable data channel simply drops stale frames, maintaining consistent low latency.

For VTuber performance capture at 30 FPS (33ms frame interval), a 50-200ms TCP stall means 2-6 dropped frames visible as avatar "freezing." WebRTC's behavior (smooth degradation via frame drops) is perceptually superior.

---

## 6. Implementation Complexity Assessment

### 6.1 Effort Breakdown

| Component | Estimated Effort | Complexity |
|---|---|---|
| Common interface (`WebRtcDataChannel`) | 1-2 days | Low |
| Android `actual` implementation (libwebrtc Java API) | 2-3 days | Medium |
| iOS `actual` implementation (libwebrtc ObjC API) | 2-3 days | Medium |
| WebSocket signaling endpoint in `StreamServer` | 1 day | Low |
| Signaling client (PC side) | 1 day | Low |
| `StreamSender` integration (transport abstraction) | 1-2 days | Medium |
| Testing + benchmarking | 2-3 days | Medium |
| **Total** | **10-15 days** | **Medium** |

### 6.2 Architecture Impact

The existing streaming module needs a transport abstraction layer:

```
Current:
  StreamSender → StreamServer (WebSocket)

Proposed:
  StreamSender → StreamTransport (interface)
                    ├── WebSocketTransport (existing, refactored)
                    └── WebRtcTransport (new)
```

```kotlin
// Proposed common interface
public interface StreamTransport {
    public val connectionCount: SharedFlow<Int>
    public val isConnected: Boolean
    public suspend fun start(scope: CoroutineScope)
    public fun stop()
    public suspend fun sendFrame(frame: StreamFrame)
}
```

The `StreamServer` would be refactored to implement `StreamTransport`, and a new `WebRtcTransport` would provide the WebRTC data channel implementation.

### 6.3 Dependency Cost

| Dependency | Android Size | iOS Size |
|---|---|---|
| Google libwebrtc | ~8-12MB (AAR) | ~10-15MB (framework) |
| Existing Ktor (WebSocket) | Already included | Already included |

**Mitigation:** The WebRTC transport should be a **separate module** (`kmp-facelink-stream-webrtc`) so that apps not using WebRTC don't pay the binary size cost. The core `kmp-facelink-stream` module stays lightweight with WebSocket only.

---

## 7. Integration with Existing Architecture

### 7.1 Module Structure

```
kmp-facelink-stream (existing)
  ├── StreamServer (WebSocket transport)
  ├── StreamSender (high-level API)
  ├── StreamFrame / BinaryFrameCodec (shared)
  └── StreamConfig (shared)

kmp-facelink-stream-webrtc (new module)
  ├── WebRtcTransport (implements StreamTransport)
  ├── WebRtcConfig (ICE servers, data channel options)
  ├── SignalingClient (WebSocket-based signaling)
  └── Platform implementations:
      ├── androidMain: libwebrtc Java API wrapper
      └── iosMain: libwebrtc ObjC API wrapper
```

### 7.2 User-Facing API

```kotlin
// Option A: WebSocket (existing, unchanged)
val server = StreamServer(StreamConfig(port = 21412))
val sender = StreamSender(server, config)
server.start(scope)
sender.start(faceFlow, handFlow, scope)

// Option B: WebRTC (new)
val webrtc = WebRtcTransport(WebRtcConfig(
    signalingUrl = "ws://0.0.0.0:21412/signaling",
    dataChannelConfig = DataChannelConfig(
        ordered = false,
        maxRetransmits = 0,
    ),
))
val sender = StreamSender(webrtc, config) // Same StreamSender, different transport
webrtc.start(scope)
sender.start(faceFlow, handFlow, scope)
```

### 7.3 Compatibility with Existing Protocols

The `StreamFrame` and `BinaryFrameCodec` are transport-agnostic and can be reused as-is for WebRTC. The binary format (242 bytes face-only) fits well within WebRTC data channel message limits and UDP MTU constraints.

The existing VMC (`kmp-facelink-vmc`) and iFacialMocap (`kmp-facelink-ifm`) modules use raw UDP via `kmp-facelink-networking`. WebRTC would be an additional option specifically for the `kmp-facelink-stream` use case, not a replacement for protocol-specific modules.

---

## 8. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| libwebrtc binary size adds 8-15MB per platform | Certain | Medium | Separate module; only apps that need WebRTC include it |
| Platform-specific bugs in libwebrtc wrappers | Medium | Medium | Extensive testing; WebSocket fallback always available |
| Signaling complexity causes connection failures | Medium | Low | WebSocket signaling reuses proven infrastructure; clear error reporting |
| ICE negotiation fails on certain network configs | Low | Medium | Fall back to WebSocket; include STUN server for NAT traversal |
| libwebrtc API changes between releases | Low | Medium | Pin to specific release; use wrapper layer to isolate changes |
| KMP cinterop issues with libwebrtc on iOS | Medium | High | Use Swift wrapper with SKIE bridge instead of direct cinterop |
| Minimal latency improvement on good WiFi networks | Medium | Low | Primary benefit is tail latency; document expected improvement clearly |

---

## 9. Recommendation

**Proceed with implementation in two phases.**

### Phase 1: Transport Abstraction + WebRTC PoC (5-7 days)

1. **Extract `StreamTransport` interface** from existing `StreamServer`
2. **Refactor `StreamServer` to implement `StreamTransport`** (backward-compatible)
3. **Create `kmp-facelink-stream-webrtc` module** with Android-only WebRTC data channel implementation
4. **Add WebSocket signaling endpoint** to `StreamServer` at `/signaling`
5. **Benchmark:** Measure round-trip latency for WebSocket vs WebRTC data channel on LAN with controlled packet loss

**Success criteria:**
- WebRTC data channel sends/receives `StreamFrame` on Android
- Measured latency improvement under 1% packet loss
- WebSocket fallback works when WebRTC connection fails

### Phase 2: iOS + Production Hardening (5-8 days)

1. **iOS implementation** using libwebrtc ObjC API with Swift/SKIE bridge
2. **Connection resilience:** Auto-reconnect, ICE restart on connection failure
3. **Adaptive transport:** Automatically switch from WebRTC to WebSocket if data channel fails
4. **Configuration API:** `WebRtcConfig` with sensible defaults for local network use
5. **Sample app integration:** Add WebRTC toggle to Android and iOS sample apps

### Actionable Next Steps

1. **Create issue: Extract `StreamTransport` interface** -- Refactor `kmp-facelink-stream` to decouple transport from `StreamServer`
2. **Create issue: `kmp-facelink-stream-webrtc` module (Android)** -- Implement WebRTC data channel transport for Android using libwebrtc
3. **Create issue: WebSocket signaling endpoint** -- Add `/signaling` to `StreamServer` for WebRTC SDP/ICE exchange
4. **Create issue: `kmp-facelink-stream-webrtc` iOS implementation** -- Port WebRTC transport to iOS via libwebrtc ObjC API
5. **Create issue: Latency benchmarking tool** -- Measure and compare WebSocket vs WebRTC vs raw UDP transport latency

---

## References

- [WebRTC Data Channels (MDN)](https://developer.mozilla.org/en-US/docs/Web/API/RTCDataChannel)
- [WebRTC Data Channels: Ordered, Reliable, and Otherwise (webrtcHacks)](https://webrtchacks.com/datachannel-unreliable-ordered/)
- [Google libwebrtc Android SDK](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/)
- [Google libwebrtc iOS SDK](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/objc/)
- [WebRTC Low Latency Guide (VideoSDK)](https://www.videosdk.live/developer-hub/webrtc/webrtc-low-latency)
- [SCTP-based Data Channels (RFC 8831)](https://datatracker.ietf.org/doc/html/rfc8831)
- [WebRTC KMP (archived)](https://AnyConnect.github.io/WebRTC-KMP/)
- [libdatachannel](https://github.com/nicedcm/libdatachannel)
- [Pion WebRTC (Go)](https://github.com/pion/webrtc)
