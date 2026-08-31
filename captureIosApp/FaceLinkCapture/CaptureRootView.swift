import AVFoundation
import ARKit
import CoreImage
import CoreVideo
import CryptoKit
import KMPFaceLinkCapture
import SceneKit
import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct CaptureRootView: View {
    var body: some View {
        if ProcessInfo.processInfo.arguments.contains("--performance-diagnostic") {
            PerformanceDiagnosticView()
        } else {
            GuidedCaptureRootView(
                previewCheck: ProcessInfo.processInfo.arguments.contains("--preview-check")
            )
        }
    }
}

private struct GuidedCaptureRootView: View {
    @StateObject private var model = CaptureModel()
    let previewCheck: Bool

    var body: some View {
        Group {
            switch model.state {
            case .starting:
                ProgressView("Starting ARKit…")
            case .ready(let session):
                CaptureController(session: session, previewCheck: previewCheck)
                    .ignoresSafeArea()
            case .failed(let message):
                Text(message)
                    .foregroundStyle(.red)
                    .padding()
            }
        }
        .task {
            await model.start()
        }
    }
}

@MainActor
final class CaptureModel: ObservableObject {
    enum State {
        case starting
        case ready(IosCaptureSession)
        case failed(String)
    }

    @Published private(set) var state: State = .starting
    private var session: IosCaptureSession?
    private var arRuntime: GuidedCaptureArRuntime?

    func start() async {
        guard session == nil else { return }
        let granted = await AVCaptureDevice.requestAccess(for: .video)
        guard granted else {
            state = .failed("Camera permission is required to record.")
            return
        }
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        var created: IosCaptureSession?
        var runtime: GuidedCaptureArRuntime?
        do {
            let opened = IosCaptureSessionFactory.shared.openRegisteredCoefficient17(
                rootDirectory: documents.path,
                provenance: sourceProvenanceFromBundle(),
                captureWorkflow: .guidedGateAutostartV1
            )
            guard let candidate = opened.session else {
                state = .failed(
                    opened.failure?.participantMessageJa
                        ?? "原因を特定できないエラーが発生しました。撮影を中止してください。"
                )
                return
            }
            created = candidate
            let nativeRuntime = try GuidedCaptureArRuntime(captureSession: candidate)
            runtime = nativeRuntime
            candidate.attachPreviewView(view: nativeRuntime.view)
            try candidate.start()
            try nativeRuntime.start()
            session = candidate
            arRuntime = nativeRuntime
            state = .ready(candidate)
        } catch {
            runtime?.stop()
            created?.release()
            state = .failed(
                "カメラ入力を受け取れませんでした。撮影画面を開き直し、再発する場合は中止してください。"
            )
        }
    }

    deinit {
        arRuntime?.stop()
        session?.release()
    }
}

private struct PerformanceDiagnosticView: View {
    @StateObject private var model = PerformanceDiagnosticModel()

    var body: some View {
        VStack(spacing: 20) {
            Text("FaceLink callback performance")
                .font(.title2.bold())
            Text(model.status)
                .multilineTextAlignment(.center)
                .foregroundStyle(model.failed ? .red : .primary)
            Button("Select reviewed run authorization") {
                model.showingAuthorizationImporter = true
            }
            .buttonStyle(.bordered)
            .disabled(model.running || model.starting)
            if model.running {
                Button("Stop and write aggregate") {
                    Task { await model.stop() }
                }
                .buttonStyle(.borderedProminent)
            } else if model.canStart {
                Button("Start diagnostic") {
                    Task { await model.start() }
                }
                .buttonStyle(.borderedProminent)
                .disabled(model.starting)
            }
            Text("No frame, coefficient, transform, or per-callback row is written.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(28)
        .fileImporter(
            isPresented: $model.showingAuthorizationImporter,
            allowedContentTypes: [.plainText, .data]
        ) { result in
            Task { await model.importAuthorization(result) }
        }
    }
}

@MainActor
private final class PerformanceDiagnosticModel: ObservableObject {
    @Published private(set) var status =
        "Performance remains locked until a signed exact-binary authorization is selected."
    @Published private(set) var running = false
    @Published private(set) var starting = false
    @Published private(set) var canStart = false
    @Published private(set) var failed = false
    @Published var showingAuthorizationImporter = false

    private var session: IosProcessedPerformanceSession?
    private var runAuthorizationData: Data?
    private var authorizationGeneration: UInt = 0

    func importAuthorization(_ result: Result<URL, Error>) async {
        guard !running, !starting else { return }
        authorizationGeneration &+= 1
        let importGeneration = authorizationGeneration
        canStart = false
        failed = false
        runAuthorizationData = nil
        status = "Checking signature and installed app bundle…"
        do {
            let url = try result.get()
            let accessing = url.startAccessingSecurityScopedResource()
            defer { if accessing { url.stopAccessingSecurityScopedResource() } }
            let data = try Data(contentsOf: url)
            guard let source = sourceReviewEvidenceFromBundle() else {
                throw NSError(
                    domain: "io.github.kmpfacelink.run-authorization",
                    code: 2,
                    userInfo: [NSLocalizedDescriptionKey: "This build has no valid source-review binding."]
                )
            }
            let validated = try await Task.detached(priority: .userInitiated) {
                try DiagnosticRunAuthorization.verify(
                    data: data,
                    source: source,
                    installedBinary: try DiagnosticRunAuthorization.installedAppBundle()
                )
            }.value
            guard authorizationGeneration == importGeneration, !running, !starting else { return }
            runAuthorizationData = data
            canStart = true
            status = "PASS: \(validated.binaryPacketId) / app bundle \(validated.binarySha256.prefix(12))…"
        } catch {
            fail("Run authorization rejected: configuration")
        }
    }

    func start() async {
        guard canStart, !running, let authorizationData = runAuthorizationData else { return }
        let expectedGeneration = authorizationGeneration
        starting = true
        canStart = false
        failed = false
        status = "Waiting for camera permission…"
        let granted = await AVCaptureDevice.requestAccess(for: .video)
        guard authorizationGeneration == expectedGeneration, runAuthorizationData == authorizationData else {
            starting = false
            fail("Run authorization changed while camera permission was pending.")
            return
        }
        guard granted else {
            starting = false
            fail("Camera permission is required for the ARKit diagnostic.")
            return
        }

        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        do {
            // The Kotlin/Native factory re-reads source authorization and the complete installed
            // app bundle, verifies P-256, then creates and starts under its private permit. This
            // is deliberately after the permission await and immediately at start, not a cached
            // Swift/UI decision.
            status = "Verifying exact authorization and starting…"
            let diagnostic = try await IosProcessedPerformanceSessionFactory.shared.createAndStartAuthorized(
                rootDirectory: documents.path,
                provenance: sourceProvenanceFromBundle(),
                runAuthorization: authorizationData
            )
            session = diagnostic
            starting = false
            running = true
            status = "Collecting the instrumented callback path."
        } catch {
            starting = false
            session?.release()
            session = nil
            fail("Diagnostic session could not start: unknown")
        }
    }

    func stop() async {
        guard let diagnostic = session, running else { return }
        running = false
        status = "Writing aggregate-only artifact…"
        do {
            let artifact = try await diagnostic.stopAndWrite()
            session = nil
            canStart = false
            status = "Saved \(artifact.manifestPath)\nSHA-256 \(artifact.manifestSha256)"
        } catch {
            diagnostic.release()
            session = nil
            fail("Diagnostic output could not be saved: stream_write")
        }
    }

    private func fail(_ message: String) {
        starting = false
        running = false
        canStart = false
        failed = true
        status = message
    }

    deinit {
        session?.release()
    }
}

private func sourceProvenanceFromBundle() -> SourceProvenance {
    guard let entries = canonicalPropertiesResource(
        name: "FaceLinkSourceProvenance",
        expectedKeys: ["repo", "branch", "gitSha", "commitTimeIso", "dirty"]
    ),
    entries["repo"] == "KMP-FaceLink",
    let branch = entries["branch"], !branch.isEmpty,
    let gitSha = entries["gitSha"], !gitSha.isEmpty,
    let commitTime = entries["commitTimeIso"], !commitTime.isEmpty,
    let dirtyText = entries["dirty"],
    dirtyText == "true" || dirtyText == "false"
    else {
        return SourceProvenance(
            repo: "KMP-FaceLink",
            branch: "",
            gitSha: "",
            commitTimeIso: "",
            dirty: true
        )
    }
    return SourceProvenance(
        repo: "KMP-FaceLink",
        branch: branch,
        gitSha: gitSha,
        commitTimeIso: commitTime,
        dirty: dirtyText == "true"
    )
}

private func sourceReviewEvidenceFromBundle() -> SourceReviewEvidence? {
    guard
        let url = Bundle.main.url(
            forResource: "FaceLinkReviewAuthorization",
            withExtension: "properties"
        ),
        let data = try? Data(contentsOf: url),
        let entries = canonicalProperties(
            data: data,
            expectedKeys: ["packetId", "packetManifestSha256", "sourceManifestSha256"]
        )
    else {
        return nil
    }
    guard
        let packetId = entries["packetId"],
        packetId.isCanonicalPacketId,
        let packetHash = entries["packetManifestSha256"],
        packetHash.isLowercaseSHA256,
        let sourceHash = entries["sourceManifestSha256"],
        sourceHash.isLowercaseSHA256
    else {
        return nil
    }
    let authorizationHash = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    return SourceReviewEvidence(
        packetId: packetId,
        packetManifestSha256: packetHash,
        sourceManifestSha256: sourceHash,
        authorizationSha256: authorizationHash
    )
}

private func canonicalPropertiesResource(
    name: String,
    expectedKeys: [String]
) -> [String: String]? {
    guard
        let url = Bundle.main.url(forResource: name, withExtension: "properties"),
        let data = try? Data(contentsOf: url)
    else {
        return nil
    }
    return canonicalProperties(data: data, expectedKeys: expectedKeys)
}

private func canonicalProperties(
    data: Data,
    expectedKeys: [String]
) -> [String: String]? {
    guard
        let content = String(data: data, encoding: .utf8),
        !content.contains("\r"),
        content.hasSuffix("\n")
    else {
        return nil
    }
    let lines = content.split(separator: "\n", omittingEmptySubsequences: false)
    guard lines.count == expectedKeys.count + 1, lines.last?.isEmpty == true else {
        return nil
    }
    var result: [String: String] = [:]
    for (index, key) in expectedKeys.enumerated() {
        let line = lines[index]
        guard let separator = line.firstIndex(of: "=") else { return nil }
        let valueStart = line.index(after: separator)
        let foundKey = String(line[..<separator])
        let value = String(line[valueStart...])
        guard foundKey == key, !value.contains("=") else { return nil }
        result[foundKey] = value
    }
    return result
}

/**
 * The sole owner of ARKit objects for guided capture.
 *
 * `ARSCNView` owns the one ARSession integration, and this retained Swift object owns its weak
 * renderer delegate. Every callback reads an ARFrame only inside this autorelease scope, copies
 * the scalar/collection snapshot synchronously, and gives KMP no ARKit/CoreVideo/SceneKit object.
 */
private final class GuidedCaptureArRuntime: NSObject, ARSCNViewDelegate {
    let view: ARSCNView

    private let arSession: ARSession
    private let captureSession: IosCaptureSession
    private let callbackLock = NSLock()
    private let colorContext = CIContext(options: nil)
    private let colorSpace = CGColorSpaceCreateDeviceRGB()
    private var lastFrameTimestamp = -Double.infinity
    private var running = false

    init(captureSession: IosCaptureSession) throws {
        guard ARFaceTrackingConfiguration.isSupported else {
            throw GuidedCaptureArRuntimeError.unsupported
        }
        let sceneView = ARSCNView()
        self.view = sceneView
        // ARSCNView creates this session. Retaining that single instance avoids a second ARSession
        // while keeping session ownership entirely under Swift/ARC.
        self.arSession = sceneView.session
        self.captureSession = captureSession
        super.init()
        sceneView.delegate = self
        sceneView.automaticallyUpdatesLighting = true
        sceneView.rendersCameraGrain = false
        sceneView.showsStatistics = false
    }

    func start() throws {
        callbackLock.lock()
        guard !running else {
            callbackLock.unlock()
            return
        }
        // Make observer callbacks eligible before ARKit can enable frame delivery. Do not hold the
        // non-recursive lock across the external framework call; observer/stop may win afterward.
        running = true
        callbackLock.unlock()
        do {
            guard ARFaceTrackingConfiguration.isSupported else {
                throw GuidedCaptureArRuntimeError.unsupported
            }
            let configuration = ARFaceTrackingConfiguration()
            configuration.isLightEstimationEnabled = true
            configuration.maximumNumberOfTrackedFaces = 1
            let runSession: () throws -> Void = { [arSession] in
                arSession.run(configuration)
            }
            try runSession()

            callbackLock.lock()
            let remainedActive = running
            if (!remainedActive) {
                arSession.pause()
            }
            callbackLock.unlock()
            if (!remainedActive) {
                throw GuidedCaptureArRuntimeError.stoppedDuringStart
            }
        } catch {
            // Callback eligibility is already active, so setup/run failures use the same stable
            // report-before-stop path as asynchronous observer failures.
            callbackLock.lock()
            if (running) {
                failSourceLocked()
            } else {
                arSession.pause()
            }
            callbackLock.unlock()
            throw error
        }
    }

    func stop() {
        callbackLock.lock()
        stopLocked()
        callbackLock.unlock()
    }

    func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
        callbackLock.lock()
        defer { callbackLock.unlock() }
        guard running, let frame = arSession.currentFrame, frame.timestamp > lastFrameTimestamp else {
            return
        }
        lastFrameTimestamp = frame.timestamp
        autoreleasepool {
            let demand: IosCaptureFrameDemand
            do {
                demand = try captureSession.prepareFrame()
            } catch {
                failSource(error)
                return
            }
            do {
                let snapshot = try copiedSnapshot(frame: frame, rawPixelsRequired: demand.rawPixelsRequired)
                try captureSession.acceptFrame(
                    demand: demand,
                    width: Int32(snapshot.width),
                    height: Int32(snapshot.height),
                    captureTimestampNs: snapshot.captureTimestampNs,
                    luma: snapshot.luma,
                    transform: snapshot.transform,
                    blendshapes: snapshot.blendshapes,
                    rgba: snapshot.rgba
                )
            } catch {
                captureSession.rejectFrame(
                    demand: demand,
                    message: "frame_source"
                )
                // This renderer callback still owns callbackLock.
                stopLocked()
            }
        }
    }

    func session(_ session: ARSession, didFailWithError error: Error) {
        failSourceFromObserver()
    }

    func sessionWasInterrupted(_ session: ARSession) {
        // ARKit documents that no frame updates arrive during interruption. Treating it as a
        // terminal source loss prevents the last passed gate from starting or continuing a take.
        failSourceFromObserver()
    }

    func renderer(_ renderer: SCNSceneRenderer, nodeFor anchor: ARAnchor) -> SCNNode? {
        guard
            let faceAnchor = anchor as? ARFaceAnchor,
            let device = view.device,
            let geometry = ARSCNFaceGeometry(device: device, fillMesh: true)
        else {
            return nil
        }
        geometry.firstMaterial?.fillMode = .lines
        geometry.firstMaterial?.diffuse.contents = UIColor.cyan.withAlphaComponent(0.8)
        geometry.firstMaterial?.isDoubleSided = true
        geometry.update(from: faceAnchor.geometry)
        return SCNNode(geometry: geometry)
    }

    func renderer(_ renderer: SCNSceneRenderer, didUpdate node: SCNNode, for anchor: ARAnchor) {
        guard
            let faceAnchor = anchor as? ARFaceAnchor,
            let geometry = node.geometry as? ARSCNFaceGeometry
        else {
            return
        }
        geometry.update(from: faceAnchor.geometry)
        node.isHidden = false
    }

    private func failSource(_: Error) {
        failSourceLocked()
    }

    private func failSourceFromObserver() {
        callbackLock.lock()
        defer { callbackLock.unlock() }
        guard running else { return }
        failSourceLocked()
    }

    private func failSourceLocked() {
        captureSession.reportFrameSourceFailure(
            message: "frame_source"
        )
        // Renderer failures already hold callbackLock; observer failures enter through
        // failSourceFromObserver. Both report the stable category before stopping ARKit.
        stopLocked()
    }

    private func stopLocked() {
        running = false
        arSession.pause()
    }

    private func copiedSnapshot(
        frame: ARFrame,
        rawPixelsRequired: Bool
    ) throws -> OwnedFrameSnapshot {
        let image = frame.capturedImage
        let width = CVPixelBufferGetWidth(image)
        let height = CVPixelBufferGetHeight(image)
        guard width > 0, height > 0 else {
            throw GuidedCaptureArRuntimeError.invalidGeometry
        }
        let timestamp = frame.timestamp * 1_000_000_000
        guard timestamp.isFinite, timestamp >= 0, timestamp <= Double(Int64.max) else {
            throw GuidedCaptureArRuntimeError.invalidTimestamp
        }
        let face = frame.anchors.lazy.compactMap { $0 as? ARFaceAnchor }.first(where: \.isTracked)
        let transform = face.map(Self.copyTransform)
        let blendshapes = face.map(Self.copyBlendshapes)
        return OwnedFrameSnapshot(
            width: width,
            height: height,
            captureTimestampNs: Int64(timestamp.rounded(.towardZero)),
            luma: try averageLuma(image),
            transform: transform,
            blendshapes: blendshapes,
            rgba: rawPixelsRequired ? try copyRGBA(image, width: width, height: height) : nil
        )
    }

    private static func copyTransform(_ face: ARFaceAnchor) -> [NSNumber] {
        let columns = face.transform.columns
        return [
            columns.0.x, columns.0.y, columns.0.z, columns.0.w,
            columns.1.x, columns.1.y, columns.1.z, columns.1.w,
            columns.2.x, columns.2.y, columns.2.z, columns.2.w,
            columns.3.x, columns.3.y, columns.3.z, columns.3.w
        ].map { NSNumber(value: $0) }
    }

    private static func copyBlendshapes(_ face: ARFaceAnchor) -> [String: NSNumber] {
        var copied: [String: NSNumber] = [:]
        copied.reserveCapacity(face.blendShapes.count)
        for (name, value) in face.blendShapes {
            copied[name.rawValue] = NSNumber(value: value.floatValue)
        }
        return copied
    }

    private func averageLuma(_ pixelBuffer: CVPixelBuffer) throws -> Float {
        guard CVPixelBufferGetPlaneCount(pixelBuffer) > 0 else {
            throw GuidedCaptureArRuntimeError.missingLumaPlane
        }
        guard CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly) == kCVReturnSuccess else {
            throw GuidedCaptureArRuntimeError.couldNotLockCameraImage
        }
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly) }
        guard let base = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 0) else {
            throw GuidedCaptureArRuntimeError.missingLumaPlane
        }
        let bytes = base.assumingMemoryBound(to: UInt8.self)
        let width = CVPixelBufferGetWidthOfPlane(pixelBuffer, 0)
        let height = CVPixelBufferGetHeightOfPlane(pixelBuffer, 0)
        let rowBytes = CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 0)
        var total: UInt64 = 0
        var samples: UInt64 = 0
        stride(from: 0, to: height, by: 16).forEach { y in
            stride(from: 0, to: width, by: 16).forEach { x in
                total += UInt64(bytes[y * rowBytes + x])
                samples += 1
            }
        }
        guard samples > 0 else { throw GuidedCaptureArRuntimeError.missingLumaPlane }
        return Float(total) / Float(samples)
    }

    private func copyRGBA(_ pixelBuffer: CVPixelBuffer, width: Int, height: Int) throws -> Data {
        let byteCount = width * height * 4
        guard byteCount > 0 else { throw GuidedCaptureArRuntimeError.invalidGeometry }
        let image = CIImage(cvPixelBuffer: pixelBuffer)
        var copied = Data(count: byteCount)
        try copied.withUnsafeMutableBytes { buffer in
            guard let address = buffer.baseAddress else {
                throw GuidedCaptureArRuntimeError.invalidGeometry
            }
            colorContext.render(
                image,
                toBitmap: address,
                rowBytes: width * 4,
                bounds: image.extent,
                format: .RGBA8,
                colorSpace: colorSpace
            )
        }
        for offset in stride(from: 3, to: copied.count, by: 4) {
            copied[offset] = 0xFF
        }
        return copied
    }
}

private struct OwnedFrameSnapshot {
    let width: Int
    let height: Int
    let captureTimestampNs: Int64
    let luma: Float
    let transform: [NSNumber]?
    let blendshapes: [String: NSNumber]?
    let rgba: Data?
}

private enum GuidedCaptureArRuntimeError: LocalizedError {
    case unsupported
    case stoppedDuringStart
    case missingCameraImage
    case invalidGeometry
    case invalidTimestamp
    case missingLumaPlane
    case couldNotLockCameraImage

    var errorDescription: String? {
        switch self {
        case .unsupported: return "ARKit face tracking is not supported on this device."
        case .stoppedDuringStart: return "ARKit stopped while the capture source was starting."
        case .missingCameraImage: return "ARKit did not supply a camera image."
        case .invalidGeometry: return "ARKit supplied invalid camera geometry."
        case .invalidTimestamp: return "ARKit supplied an invalid frame timestamp."
        case .missingLumaPlane: return "ARKit camera image has no readable luma plane."
        case .couldNotLockCameraImage: return "ARKit camera image could not be locked for copying."
        }
    }
}

private struct CaptureController: UIViewControllerRepresentable {
    let session: CaptureSession
    let previewCheck: Bool

    func makeUIViewController(context: Context) -> UIViewController {
        previewCheck
            ? CaptureViewControllerKt.CapturePreviewCheckViewController(session: session)
            : CaptureViewControllerKt.CaptureViewController(session: session)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Compose owns all mutable UI state.
    }
}
