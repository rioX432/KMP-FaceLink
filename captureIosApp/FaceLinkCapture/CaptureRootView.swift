import AVFoundation
import CryptoKit
import KMPFaceLinkCapture
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

    func start() async {
        guard session == nil else { return }
        let granted = await AVCaptureDevice.requestAccess(for: .video)
        guard granted else {
            state = .failed("Camera permission is required to record.")
            return
        }
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        do {
            let created = try IosCaptureSessionFactory.shared.create(
                rootDirectory: documents.path,
                provenance: sourceProvenanceFromBundle(),
                captureWorkflow: .guidedGateAutostartV1
            )
            try created.start()
            session = created
            state = .ready(created)
        } catch {
            state = .failed(error.localizedDescription)
        }
    }

    deinit {
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
            fail("Run authorization rejected: \(error.localizedDescription)")
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
            fail(error.localizedDescription)
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
            fail(error.localizedDescription)
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
