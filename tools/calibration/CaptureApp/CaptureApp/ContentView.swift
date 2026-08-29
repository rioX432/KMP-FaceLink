import SwiftUI
import ARKit

/// Main capture interface with recording controls, session info, and export.
struct ContentView: View {
    @StateObject private var captureSession = CaptureSession()

    @State private var subjectName = ""
    @State private var lightingNotes = ""
    @State private var isExporting = false
    @State private var exportResult: ExportResult?
    @State private var showExportAlert = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                cameraPreview
                controlPanel
            }
            .navigationTitle("Calibration Capture")
            .navigationBarTitleDisplayMode(.inline)
            .alert(
                exportAlertTitle,
                isPresented: $showExportAlert,
                presenting: exportResult
            ) { _ in
                Button("OK", role: .cancel) {}
            } message: { result in
                Text(result.message)
            }
        }
    }

    // MARK: - Camera Preview

    private var cameraPreview: some View {
        ZStack(alignment: .topLeading) {
            ARCameraPreview(session: captureSession.arSession)
                .ignoresSafeArea(edges: .horizontal)

            trackingOverlay
        }
        .frame(maxHeight: .infinity)
    }

    private var trackingOverlay: some View {
        VStack(alignment: .leading, spacing: 4) {
            statusBadge
            if captureSession.isRecording {
                recordingStats
            }
        }
        .padding(12)
    }

    private var statusBadge: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(statusColor)
                .frame(width: 8, height: 8)
            Text(captureSession.trackingState)
                .font(.caption)
                .fontWeight(.medium)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(.ultraThinMaterial, in: Capsule())
    }

    private var recordingStats: some View {
        HStack(spacing: 12) {
            Label("\(captureSession.frameCount)", systemImage: "photo.stack")
            Label(formattedElapsed, systemImage: "timer")
        }
        .font(.caption)
        .fontWeight(.medium)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(.ultraThinMaterial, in: Capsule())
    }

    // MARK: - Control Panel

    private var controlPanel: some View {
        VStack(spacing: 12) {
            metadataFields
            actionButtons
        }
        .padding(16)
        .background(.regularMaterial)
    }

    private var metadataFields: some View {
        VStack(spacing: 8) {
            TextField("Participant pseudonym (never a real name)", text: $subjectName)
                .textFieldStyle(.roundedBorder)
                .autocorrectionDisabled()

            TextField("Lighting / session notes", text: $lightingNotes)
                .textFieldStyle(.roundedBorder)
                .autocorrectionDisabled()
        }
    }

    private var actionButtons: some View {
        HStack(spacing: 12) {
            trackingButton
            recordButton
            exportButton
        }
    }

    private var trackingButton: some View {
        Button {
            if captureSession.isTracking {
                captureSession.stopTracking()
            } else {
                captureSession.startTracking()
            }
        } label: {
            Label(
                captureSession.isTracking ? "Stop" : "Track",
                systemImage: captureSession.isTracking ? "stop.fill" : "face.smiling"
            )
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .disabled(captureSession.isRecording || !captureSession.isFaceTrackingSupported)
    }

    private var recordButton: some View {
        Button {
            if captureSession.isRecording {
                captureSession.stopRecording()
            } else {
                captureSession.startRecording()
            }
        } label: {
            Label(
                captureSession.isRecording ? "Stop" : "Record",
                systemImage: captureSession.isRecording ? "stop.circle.fill" : "record.circle"
            )
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .tint(captureSession.isRecording ? .red : .blue)
        .disabled(!captureSession.isTracking)
    }

    private var exportButton: some View {
        Button {
            exportSession()
        } label: {
            Label("Export", systemImage: "square.and.arrow.up")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .disabled(
            captureSession.capturedFrames.isEmpty
            || captureSession.isRecording
            || isExporting
        )
    }

    // MARK: - Helpers

    private var statusColor: Color {
        if captureSession.isRecording {
            return .red
        } else if captureSession.isTracking {
            return .green
        }
        return .gray
    }

    private var formattedElapsed: String {
        let total = Int(captureSession.elapsedTime)
        let minutes = total / 60
        let seconds = total % 60
        return String(format: "%d:%02d", minutes, seconds)
    }

    private var exportAlertTitle: String {
        switch exportResult {
        case .success:
            return "Export Complete"
        case .failure, .none:
            return "Export Failed"
        }
    }

    // MARK: - Export

    private func exportSession() {
        isExporting = true

        let frames = captureSession.capturedFrames
        let subject = subjectName.isEmpty ? "unknown" : subjectName
        let notes = lightingNotes

        // Export on a background thread to avoid blocking the UI during file I/O.
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                let documentsDir = FileManager.default.urls(
                    for: .documentDirectory,
                    in: .userDomainMask
                ).first!.appendingPathComponent("captures")

                let sessionDir = try SessionExporter.export(
                    frames: frames,
                    subjectName: subject,
                    notes: notes,
                    to: documentsDir
                )

                DispatchQueue.main.async {
                    exportResult = .success(
                        "Saved \(frames.count) frames to:\n\(sessionDir.lastPathComponent)"
                    )
                    isExporting = false
                    showExportAlert = true
                }
            } catch {
                DispatchQueue.main.async {
                    exportResult = .failure(
                        "Export failed: \(error.localizedDescription)"
                    )
                    isExporting = false
                    showExportAlert = true
                }
            }
        }
    }
}

// MARK: - Export Result

private enum ExportResult: Identifiable {
    case success(String)
    case failure(String)

    var id: String { message }

    var message: String {
        switch self {
        case .success(let msg), .failure(let msg):
            return msg
        }
    }
}

// MARK: - AR Camera Preview (UIViewRepresentable)

/// Lightweight ARSCNView wrapper that shares the CaptureSession's ARSession.
private struct ARCameraPreview: UIViewRepresentable {
    let session: ARSession

    func makeUIView(context: Context) -> ARSCNView {
        let view = ARSCNView()
        view.session = session
        view.automaticallyUpdatesLighting = true
        view.rendersCameraGrain = false
        return view
    }

    func updateUIView(_ uiView: ARSCNView, context: Context) {
        // Session is managed externally by CaptureSession
    }

    static func dismantleUIView(_ uiView: ARSCNView, coordinator: ()) {
        // CaptureSession owns the session lifecycle
    }
}
