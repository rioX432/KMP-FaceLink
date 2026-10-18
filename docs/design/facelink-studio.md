# FaceLink Studio: Phone-as-Tracker App for VTubers

**Issue:** #655
**Status:** Design
**Date:** 2026-04-06

## Executive Summary

FaceLink Studio is a phone-as-tracker app that sends pro-grade face tracking data from a smartphone to PC VTuber software (VTube Studio, VSeeFace, VNyan, Unity, etc.) over WiFi. It leverages the entire KMP-FaceLink module ecosystem -- face tracking, protocol encoding (VMC/iFacialMocap/VTS), and zero-config networking -- packaged as a polished consumer app.

**Value proposition:** "Place your phone, and pro-grade tracking data flows to your PC's VTube Studio/Unity/VRM app."

**Market timing:** The Android phone-tracker segment is vacant. MeowFace (the only Android option) was unpublished from Google Play in January 2025, stopped receiving updates in March 2023, and its backend tracking library (Google ML Kit Face Detection) is deprecated. On iOS, iFacialMocap ($7.99) is the only dedicated tracker app. VTube Studio's mobile app can act as a tracker but is primarily a full VTuber app, not a dedicated tracker. FaceLink Studio targets this gap on both platforms.

---

## 1. Market Analysis

### 1.1 Competitive Landscape

| App | Platform | Price | Status | Tracking Tech | Protocols | Notes |
|---|---|---|---|---|---|---|
| **iFacialMocap** | iOS only | $7.99 | Active (v1.5.0, Jan 2026) | ARKit (52 blendshapes) | iFacialMocap, VMC | iOS-only; gold standard for iPhone tracking |
| **MeowFace** | Android only | Free | Abandoned (last update Mar 2023, removed from Play Store Jan 2025) | Google ML Kit (deprecated) | iFacialMocap | Tracking library deprecated; crashes unfixable |
| **VTube Studio Mobile** | iOS, Android | Free (Pro $14.99) | Active | ARKit (iOS), ARCore (Android) | VTS Plugin API | Full VTuber app, not a dedicated tracker; Android tracking quality notably worse than iPhone |
| **FaceMotion3D** | iOS only | $5.99 | Active | ARKit | iFacialMocap | Niche; less popular than iFacialMocap |
| **VSeeFace** | PC (webcam) | Free | Active | OpenSeeFace (MediaPipe-based) | VMC | Webcam-only; lower quality than phone tracking |
| **VBridger** | PC (webcam) | $24.99 (Steam) | Active | MediaPipe, NVIDIA Broadcast | Multiple | PC plugin, not a phone app; requires webcam |

**Key gaps identified:**

1. **Android has zero maintained phone-tracker apps.** MeowFace is dead, VTube Studio Mobile is a full app with inferior Android tracking.
2. **No cross-platform tracker exists.** Every solution is platform-locked.
3. **No dedicated tracker supports all three major protocols** (VMC + iFacialMocap + VTS Plugin API) in a single app.
4. **MediaPipe on Android is underexploited.** Google's MediaPipe Face Landmarker (478 landmarks, 52 blendshapes) provides ARKit-quality output on Android but no consumer app uses it well.

### 1.2 Target User Persona

**Primary: "PC VTuber with a smartphone"**

- Streams on PC using OBS + VTube Studio / VSeeFace / VNyan
- Wants high-quality face tracking without buying a dedicated tracker (e.g., iPhone + iFacialMocap)
- Currently using webcam tracking (low quality) or MeowFace (broken)
- Price-sensitive indie VTuber, willing to pay $10-15 for a good tool
- Technical comfort: can follow a setup guide, not a developer

**Secondary: "iPhone VTuber wanting more protocols"**

- Already uses iFacialMocap but wants VTS Plugin API or VMC output
- Interested in hand tracking addon for upper-body capture

**Tertiary: "Mocap professional / Unity developer"**

- Needs reliable phone-to-PC tracking for production
- Wants commercial license and API access

### 1.3 Market Size Estimation

| Metric | Estimate | Source |
|---|---|---|
| Global VTuber market | $3.13B (2026) | Mordor Intelligence |
| VTuber market CAGR | 9.5-20% through 2031-2035 | Multiple analysts |
| Active VTuber streamers (Twitch + YouTube) | ~60,000+ active channels | Streams Charts Q2 2025 |
| Estimated % using phone trackers | ~15-25% | Based on iFacialMocap rankings, community surveys |
| **Addressable phone-tracker users** | **~10,000-15,000** | Conservative estimate |
| Average willingness to pay | $8-15 one-time | Based on iFacialMocap ($7.99) and VTS Pro ($14.99) pricing |
| **TAM for phone-tracker segment** | **$80K-$225K/year** | Steady state, excluding growth |

The market is small in absolute terms but has near-zero competition on Android. The real value is in the ecosystem play: FaceLink Studio drives adoption of the KMP-FaceLink SDK, which has broader applications (game dev, accessibility, avatar apps).

---

## 2. Technical Architecture

### 2.1 Module Composition

FaceLink Studio is assembled almost entirely from existing KMP-FaceLink modules. Minimal new app-level code is needed.

```
┌─────────────────────────────────────────────────────┐
│                  FaceLink Studio App                 │
│  (Compose Multiplatform UI + app-level logic)       │
├─────────────────────────────────────────────────────┤
│                   sampleShared                       │
│  (Camera preview, BlendShape visualization, UI)     │
├──────────┬──────────┬───────────┬───────────────────┤
│ kmp-     │ kmp-     │ kmp-      │ kmp-facelink-     │
│ facelink │ facelink │ facelink  │ networking         │
│ -vmc     │ -ifm     │ -vts      │ (mDNS zero-config)│
│ (VMC     │ (iFM     │ (VTS      │                   │
│  proto)  │  proto)  │  Plugin)  │                   │
├──────────┴──────────┴───────────┴───────────────────┤
│              kmp-facelink (core)                     │
│  Face: MediaPipe (Android) / ARKit (iOS)            │
│  Hand: MediaPipe (Android) / Vision (iOS)           │
│  52 ARKit blendshapes + head pose + landmarks       │
├─────────────────────────────────────────────────────┤
│         Platform (Android / iOS)                     │
│  Camera: CameraX (Android) / AVCaptureSession (iOS) │
│  Network: UDP sockets, Ktor                          │
└─────────────────────────────────────────────────────┘
```

### 2.2 Module Dependency Diagram

```
                    FaceLink Studio App
                           │
                    ┌──────┼──────┐
                    ▼      ▼      ▼
              ┌─────────┐  │  ┌─────────┐
              │   vmc   │  │  │   vts   │
              └────┬────┘  │  └────┬────┘
                   │       │       │
              ┌────┘  ┌────┘  ┌────┘
              ▼       ▼       ▼
         ┌─────────────────────────┐
         │   kmp-facelink (core)   │
         └────────────┬────────────┘
                      │
         ┌────────────┼────────────┐
         ▼            ▼            ▼
    ┌─────────┐ ┌──────────┐ ┌─────────┐
    │   ifm   │ │networking│ │  stream  │
    └────┬────┘ └──────────┘ └─────────┘
         │            ▲
         └────────────┘

Legend:
  vmc  = kmp-facelink-vmc (VMC protocol, UDP/OSC)
  ifm  = kmp-facelink-ifm (iFacialMocap protocol, UDP)
  vts  = kmp-facelink-vts (VTube Studio Plugin API, WebSocket)
  stream = kmp-facelink-stream (binary streaming, WebSocket/WebRTC)
  networking = kmp-facelink-networking (mDNS, UDP transport)
```

### 2.3 Data Flow

```
Camera Frame (30-60 FPS)
  │
  ▼
Face Detection Engine
  ├── Android: MediaPipe Face Landmarker (478 landmarks → 52 blendshapes)
  └── iOS: ARKit ARFaceTrackingConfiguration (native 52 blendshapes)
  │
  ▼
FaceTrackingResult
  ├── blendShapes: Map<ARKitBlendShape, Float>  (52 values)
  ├── headRotation: Rotation (pitch, yaw, roll)
  ├── headPosition: Position (x, y, z)
  └── landmarks: List<Landmark3D> (478 points)
  │
  ▼
Protocol Encoder (user-selected, can run multiple simultaneously)
  ├── VMC Encoder → OSC bundles → UDP broadcast → PC (VSeeFace, VNyan, etc.)
  ├── iFM Encoder → iFacialMocap format → UDP → PC (VSeeFace, VNyan, VBridger)
  └── VTS Encoder → JSON → WebSocket → PC (VTube Studio)
  │
  ▼
Network Transport
  ├── UDP (VMC, iFM): kmp-facelink-networking
  ├── WebSocket (VTS): Ktor client
  └── Discovery: mDNS zero-config (kmp-facelink-networking)
```

### 2.4 New Code Required

The app itself needs relatively little new code since it composes existing modules:

| Component | LOC Estimate | Description |
|---|---|---|
| App shell (Android Activity / iOS App) | ~200 | Entry point, DI setup, permissions |
| Settings screen | ~400 | Protocol selection, target IP, FPS, quality |
| Connection screen | ~300 | mDNS discovery, manual IP entry, connection status |
| Tracking session screen | ~300 | Camera preview, stats overlay, protocol status |
| Onboarding flow | ~200 | First-launch setup wizard |
| Session management | ~150 | Timer (free tier), background keep-alive |
| IAP integration | ~300 | Play Billing / StoreKit |
| **Total new code** | **~1,850** | Excluding existing module code |

---

## 3. MVP Scope (v0.1)

### 3.1 Feature List

| Priority | Feature | Description |
|---|---|---|
| **P0** | Face tracking | 52 ARKit blendshapes + head pose via MediaPipe (Android) / ARKit (iOS) |
| **P0** | VMC protocol output | Send tracking data via VMC protocol over UDP |
| **P0** | iFacialMocap protocol output | Send tracking data via iFacialMocap protocol over UDP |
| **P0** | Manual IP connection | Enter PC IP address manually to connect |
| **P0** | Camera preview | Show front camera feed with face mesh overlay |
| **P0** | FPS counter | Real-time tracking FPS display |
| **P1** | mDNS auto-discovery | Zero-config: automatically find PC receivers on LAN |
| **P1** | VTS Plugin API output | Send tracking data to VTube Studio via WebSocket |
| **P1** | Free tier session limit | 10-minute sessions with watermark for free users |
| **P1** | Pro IAP | Unlock unlimited sessions + remove watermark |
| **P1** | Background tracking | Continue tracking when app is minimized (screen off) |
| **P1** | Tracking quality settings | FPS target (30/60), tracking smoothing level |
| **P2** | Onboarding wizard | Step-by-step first-time setup guide |
| **P2** | Connection history | Remember previously connected PCs |
| **P2** | Battery optimization | Reduce CPU usage when phone is stationary |
| **P2** | Dark/light theme | Follow system theme |

### 3.2 Screen Flow

```
┌──────────────────┐
│   Splash/Logo    │
│   (1 second)     │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐    First launch    ┌──────────────────┐
│   Home Screen    │ ──────────────────▶│   Onboarding     │
│                  │                    │   (3 steps)      │
│  [Start Track]   │◀───────────────── └──────────────────┘
│  [Settings]      │
│  [Pro]           │
└────────┬─────────┘
         │ Start Tracking
         ▼
┌──────────────────┐
│  Connection      │
│                  │
│  Discovered:     │
│  ┌────────────┐  │
│  │ PC-Gaming  │  │    mDNS auto-discovery
│  │ 192.168.x  │  │
│  └────────────┘  │
│                  │
│  [Manual IP]     │
│  [___________]   │
│                  │
│  Protocol:       │
│  (x) VMC         │
│  ( ) iFacialMocap│
│  ( ) VTube Studio│
│                  │
│  [Connect]       │
└────────┬─────────┘
         │ Connected
         ▼
┌──────────────────┐
│  Tracking        │
│  Session         │
│                  │
│  ┌────────────┐  │
│  │  Camera     │  │
│  │  Preview    │  │
│  │  + Face     │  │
│  │  Mesh       │  │
│  └────────────┘  │
│                  │
│  FPS: 30 │ Sent: │
│  1,204 frames    │
│  Latency: 8ms    │
│                  │
│  ● VMC → PC-Game │
│  Status: Streaming│
│                  │
│  [Stop]          │
│  [Hide Preview]  │
└──────────────────┘
```

### 3.3 ASCII Wireframes

**Home Screen:**
```
┌─────────────────────────┐
│ FaceLink Studio         │
│ Phone-as-tracker        │
├─────────────────────────┤
│                         │
│  ┌───────────────────┐  │
│  │ 😊 Start Tracking │  │
│  │  Tap to connect   │  │
│  │  to your PC       │  │
│  └───────────────────┘  │
│                         │
│  ┌───────────────────┐  │
│  │ ⚙️  Settings      │  │
│  │  Protocol, FPS,   │  │
│  │  quality           │  │
│  └───────────────────┘  │
│                         │
│  ┌───────────────────┐  │
│  │ ⭐ Go Pro         │  │
│  │  Remove limits    │  │
│  │  $14.99 one-time  │  │
│  └───────────────────┘  │
│                         │
├─────────────────────────┤
│ Free: 10 min sessions   │
└─────────────────────────┘
```

**Tracking Session Screen:**
```
┌─────────────────────────┐
│ ● REC  00:03:24   [Stop]│
├─────────────────────────┤
│                         │
│   ┌─────────────────┐   │
│   │                 │   │
│   │   Camera Feed   │   │
│   │   + Face Mesh   │   │
│   │   Overlay       │   │
│   │                 │   │
│   └─────────────────┘   │
│                         │
├─────────────────────────┤
│ FPS    Frames   Latency │
│  30     1,204     8ms   │
├─────────────────────────┤
│ ● VMC → PC-Gaming       │
│   192.168.1.42:39539    │
│   Status: Streaming     │
├─────────────────────────┤
│ [Hide Preview] [Minimize│
│  to notification]       │
└─────────────────────────┘
```

---

## 4. Platform Strategy

### 4.1 Recommendation: Android First, iOS Fast-Follow

| Factor | Android First | iOS First | Simultaneous |
|---|---|---|---|
| Market gap | **Vacant** (MeowFace dead) | Competitive (iFacialMocap active) | Both covered |
| Development effort | Lower (MediaPipe simpler to integrate) | Medium (ARKit already works) | ~1.5x single platform |
| KMP advantage | High (shared code reduces iOS follow-up to ~30% effort) | High | Maximized |
| Revenue potential | Lower (Android users more price-sensitive) | Higher (iOS users pay more) | Highest |
| Brand impact | **Highest** (first-to-market on vacant platform) | Low (yet-another iOS tracker) | High |

**Recommended sequence:**

1. **v0.1 Android** (4 weeks) -- Capture the vacant Android market. No competition.
2. **v0.2 iOS** (2 weeks) -- Fast-follow with iOS, leveraging ~70% shared code from KMP.
3. **v0.3+ Both platforms** -- Simultaneous updates from this point.

### 4.2 KMP Advantage

The KMP-FaceLink architecture provides a decisive advantage for cross-platform development:

| Layer | Shared (commonMain) | Platform-specific |
|---|---|---|
| Protocol encoding (VMC, iFM, VTS) | 100% | 0% |
| Networking (mDNS, UDP) | ~90% | ~10% (socket impl) |
| UI (Compose Multiplatform) | ~80% | ~20% (camera preview) |
| Face tracking API surface | 100% (interface) | 100% (implementation) |
| Face tracking engine | 0% | 100% (MediaPipe vs ARKit) |

Estimated code sharing: **~65-70%** between Android and iOS.

---

## 5. Release Roadmap

### v0.1 MVP -- Android (Target: 5 weeks from start)

**Week 1-2: Core integration**
- Wire up `kmp-facelink` face tracking in new app shell
- Implement VMC and iFacialMocap protocol output using existing modules
- Manual IP connection screen

**Week 3: UX polish**
- Camera preview with face mesh overlay (reuse sampleShared components)
- FPS counter, latency display, connection status
- Settings screen (protocol selection, FPS target)

**Week 4: Monetization + session management**
- Free tier 10-minute session timer
- Watermark overlay
- Google Play Billing integration for Pro IAP
- Background tracking (foreground service)

**Week 5: Testing + release prep**
- Compatibility testing across Android devices (min SDK 24)
- Play Store listing, screenshots, description
- Beta release to VTuber community for feedback

**Dependencies:**
- None. All required modules (vmc, ifm, networking) are Stable (Issue #656).

### v0.2 iOS + VTS -- (Target: +2 weeks after v0.1)

- iOS app shell using SwiftUI / Compose Multiplatform
- ARKit face tracking (already implemented in `kmp-facelink`)
- VTube Studio Plugin API output using `kmp-facelink-vts`
- StoreKit IAP integration
- App Store submission

**Dependencies:**
- `kmp-facelink-vts` currently targets JVM only (WebSocket via Ktor). Need to add iOS targets. (Low effort -- Ktor client already supports iOS.)

### v1.0 Full Release -- (Target: +4 weeks after v0.2)

- mDNS zero-config auto-discovery
- Onboarding wizard
- Connection history / favorites
- Battery optimization (adaptive FPS)
- Hand tracking DLC (reuse existing hand tracking from `kmp-facelink`)
- Crash reporting (Firebase Crashlytics)
- Analytics (usage patterns, protocol popularity)

### v1.1 Advanced Features -- (Target: +6 weeks after v1.0)

- WebRTC data channel transport (from `kmp-facelink-stream-webrtc`, Issue #370)
- Holistic tracking mode (face + hands combined)
- Custom blendshape mapping / calibration
- Multi-receiver support (stream to multiple PCs simultaneously)
- Widget / quick-settings tile for instant start
- Gemma 4 on-device LLM for voice-reactive expressions (Issue #653)

### Timeline Summary

```
                    2026
            May         Jun         Jul         Aug
Week:  1  2  3  4  5  6  7  8  9  10 11 12 13 14 15 16
       ├──────────────┤
       v0.1 Android MVP
                      ├─────┤
                      v0.2 iOS
                            ├──────────────┤
                            v1.0 Full Release
                                           ├───────────┤
                                           v1.1 Advanced
```

---

## 6. Risk Assessment

### 6.1 Technical Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **Latency too high for real-time use** | Low | Critical | Existing modules achieve 8-23ms glass-to-glass (WebRTC research doc confirms). UDP transport for VMC/iFM is inherently low-latency. |
| **Battery drain makes phone unusable as tracker** | Medium | High | Adaptive FPS (drop to 30 FPS when little face movement). Allow screen-off tracking. Profile and optimize camera pipeline. Target <15% battery/hour. |
| **MediaPipe Android tracking quality inferior to ARKit** | Medium | Medium | MediaPipe Face Landmarker v2 outputs 52 ARKit-compatible blendshapes. Existing `BlendShapeEnhancer` and `CheekBrowSolvers` in kmp-facelink improve quality. Benchmark and iterate. |
| **VTS Plugin API authentication complexity** | Low | Low | kmp-facelink-vts module already handles VTS auth handshake. |
| **WiFi network reliability** | Medium | Medium | Show connection quality indicator. Support reconnection. Consider WebRTC unreliable data channels for v1.1. |
| **Phone overheating during long sessions** | Medium | Medium | Thermal throttling monitoring. Reduce FPS automatically when thermal state is elevated. Allow hiding camera preview to reduce GPU load. |
| **Android device fragmentation** | Medium | Medium | MediaPipe handles most of the ML abstraction. Test on popular budget devices (Samsung A-series, Pixel). Min SDK 24 covers 98%+ of active devices. |

### 6.2 Market Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **MeowFace revived or forked** | Low | Medium | MeowFace's tracking library is deprecated; a revival would require a full rewrite. Our head start and KMP-FaceLink's superior architecture provide a moat. |
| **VTube Studio improves Android tracking** | Medium | High | VTS uses ARCore which has inherent limitations vs MediaPipe. Differentiate on: dedicated tracker UX, multi-protocol support, and tracking quality. Stay ahead with features. |
| **New competitor enters Android tracker market** | Medium | Medium | First-mover advantage. Build community early. KMP cross-platform means faster iteration than a single-platform competitor. |
| **VTuber market contraction** | Low | High | Diversify: position FaceLink Studio for broader mocap/avatar use cases beyond VTubing. |
| **Platform policy changes (Play Store / App Store)** | Low | Medium | Standard app; no unusual permissions. Comply with all store policies from day one. |
| **Webcam tracking quality catches up** | Medium | Low | NVIDIA Broadcast tracker already provides good webcam tracking on RTX GPUs. Phone tracking advantage: no GPU cost on PC, works with any PC, higher quality on budget setups. These advantages persist. |

### 6.3 Business Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **Low conversion rate (free → Pro)** | Medium | Medium | Free tier is deliberately limited (10 min, watermark). VTubers who stream regularly will convert. Monitor and adjust limits. |
| **Support burden from diverse Android devices** | High | Medium | Automated crash reporting. Community Discord for peer support. FAQ/troubleshooting in-app. |
| **Revenue insufficient to justify maintenance** | Medium | Low | Strategic value (SDK adoption) exceeds direct revenue. Maintenance cost is low thanks to shared KMP-FaceLink modules. |

---

## 7. Success Metrics

### 7.1 Launch Metrics (v0.1, first 30 days)

| Metric | Target | Measurement |
|---|---|---|
| Downloads | 1,000+ | Play Store analytics |
| DAU | 200+ | Firebase Analytics |
| Crash-free rate | >99% | Firebase Crashlytics |
| Average session length | >15 min | Firebase Analytics |
| Pro conversion rate | >5% | Play Billing analytics |
| Play Store rating | >4.0 | Play Store |

### 7.2 Growth Metrics (v1.0, first 90 days)

| Metric | Target | Measurement |
|---|---|---|
| Total downloads | 5,000+ | Store analytics |
| MAU | 1,500+ | Firebase Analytics |
| Pro conversions | 500+ | Billing analytics |
| Protocol usage split | Track VMC vs iFM vs VTS popularity | In-app analytics |
| Average latency | <15ms | In-app telemetry |

---

## 8. Open Questions

1. **App name:** "FaceLink Studio" vs "FaceLink Tracker" vs another name? "Studio" implies creation; "Tracker" is more descriptive of the phone-as-tracker use case.
2. **Free tier limits:** 10 minutes per session vs 5 minutes (VTube Studio's limit) vs time-unlimited with watermark only?
3. **Simultaneous multi-protocol:** Should free tier allow VMC + iFM simultaneously, or require Pro for multi-protocol?
4. **VTS module iOS target:** `kmp-facelink-vts` currently only targets JVM. Adding iOS/Android targets is straightforward but needs to be scheduled before v0.2.
5. **Commercial license enforcement:** How to technically distinguish commercial use? Honor system vs license key?

---

## References

- [VMC Protocol Specification](https://protocol.vmc.info/english.html)
- [iFacialMocap - App Store](https://apps.apple.com/us/app/ifacialmocap/id1489470545)
- [MeowFace - itch.io (archived)](https://suvidriel.itch.io/meowface)
- [VTube Studio Wiki - Android vs iPhone vs Webcam](https://github.com/DenchiSoft/VTubeStudio/wiki/Android-vs.-iPhone-vs.-Webcam)
- [VSeeFace](https://www.vseeface.icu/)
- [VTuber Market Size - Mordor Intelligence](https://www.mordorintelligence.com/industry-reports/vtuber-market)
- [WebRTC Streaming Research (internal)](../research/webrtc-streaming.md)
- [Best VTuber Software 2026 - Kudos.tv](https://kudos.tv/blogs/stream-blog/the-best-vtuber-software)
