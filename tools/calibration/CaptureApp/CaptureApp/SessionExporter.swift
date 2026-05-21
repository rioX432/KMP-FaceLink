import Foundation
import UIKit
import ARKit
import CoreVideo

/// Exports a capture session's data to disk in the calibration dataset format.
///
/// Output directory structure:
/// ```
/// session_{timestamp}/
/// ├── metadata.json
/// ├── arkit_blendshapes.csv
/// ├── frames/
/// │   ├── 000000.jpg
/// │   ├── 000001.jpg
/// │   └── ...
/// └── timestamps.csv
/// ```
enum SessionExporter {

    // MARK: - Constants

    private static let jpegQuality: CGFloat = 0.92
    private static let framePadding = 6 // "000000" format

    // MARK: - Public API

    /// Export captured frames to a session directory.
    ///
    /// - Parameters:
    ///   - frames: Array of captured frames from `CaptureSession`.
    ///   - subjectName: Name or identifier for the subject.
    ///   - notes: Free-form notes (lighting conditions, etc.).
    ///   - baseDirectory: Parent directory for the session folder.
    /// - Returns: URL of the created session directory.
    /// - Throws: File I/O errors.
    @discardableResult
    static func export(
        frames: [CapturedFrame],
        subjectName: String,
        notes: String,
        to baseDirectory: URL
    ) throws -> URL {
        let sessionName = makeSessionName()
        let sessionDir = baseDirectory.appendingPathComponent(sessionName)
        let framesDir = sessionDir.appendingPathComponent("frames")

        // Create directories
        let fileManager = FileManager.default
        try fileManager.createDirectory(at: framesDir, withIntermediateDirectories: true)

        // Export all components
        try writeMetadata(
            to: sessionDir,
            subjectName: subjectName,
            notes: notes,
            frameCount: frames.count
        )
        try writeBlendShapeCSV(frames: frames, to: sessionDir)
        try writeTimestampsCSV(frames: frames, to: sessionDir)
        try writeFrameImages(frames: frames, to: framesDir)

        return sessionDir
    }

    // MARK: - Session Naming

    private static func makeSessionName() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd_HHmmss"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return "session_\(formatter.string(from: Date()))"
    }

    // MARK: - Metadata

    private static func writeMetadata(
        to sessionDir: URL,
        subjectName: String,
        notes: String,
        frameCount: Int
    ) throws {
        let device = UIDevice.current
        let metadata: [String: Any] = [
            "device_model": deviceModelIdentifier(),
            "device_name": device.name,
            "ios_version": device.systemVersion,
            "subject_name": subjectName,
            "notes": notes,
            "frame_count": frameCount,
            "capture_date": ISO8601DateFormatter().string(from: Date()),
            "app_version": "1.0.0"
        ]

        let data = try JSONSerialization.data(
            withJSONObject: metadata,
            options: [.prettyPrinted, .sortedKeys]
        )
        let url = sessionDir.appendingPathComponent("metadata.json")
        try data.write(to: url)
    }

    /// Get the hardware model identifier (e.g. "iPhone15,2").
    private static func deviceModelIdentifier() -> String {
        var systemInfo = utsname()
        uname(&systemInfo)
        return withUnsafePointer(to: &systemInfo.machine) {
            $0.withMemoryRebound(
                to: CChar.self,
                capacity: 1
            ) { ptr in
                String(cString: ptr)
            }
        }
    }

    // MARK: - Blendshape CSV

    private static func writeBlendShapeCSV(
        frames: [CapturedFrame],
        to sessionDir: URL
    ) throws {
        var lines = [BlendShapeColumns.csvHeader]

        for frame in frames {
            let values = BlendShapeColumns.allLocations.map { location -> String in
                let value = frame.blendShapes[location]?.floatValue ?? 0.0
                return String(format: "%.6f", value)
            }
            let line = ([String(format: "%.6f", frame.timestamp)] + values)
                .joined(separator: ",")
            lines.append(line)
        }

        let csv = lines.joined(separator: "\n") + "\n"
        let url = sessionDir.appendingPathComponent("arkit_blendshapes.csv")
        try csv.write(to: url, atomically: true, encoding: .utf8)
    }

    // MARK: - Timestamps CSV

    private static func writeTimestampsCSV(
        frames: [CapturedFrame],
        to sessionDir: URL
    ) throws {
        var lines = ["frame_index,timestamp"]

        for (index, frame) in frames.enumerated() {
            lines.append("\(index),\(String(format: "%.6f", frame.timestamp))")
        }

        let csv = lines.joined(separator: "\n") + "\n"
        let url = sessionDir.appendingPathComponent("timestamps.csv")
        try csv.write(to: url, atomically: true, encoding: .utf8)
    }

    // MARK: - Frame Images

    private static func writeFrameImages(
        frames: [CapturedFrame],
        to framesDir: URL
    ) throws {
        let ciContext = CIContext()

        for (index, frame) in frames.enumerated() {
            let filename = String(format: "%0\(framePadding)d.jpg", index)
            let url = framesDir.appendingPathComponent(filename)

            guard let jpegData = convertToJPEG(
                pixelBuffer: frame.pixelBuffer,
                context: ciContext
            ) else {
                continue
            }
            try jpegData.write(to: url)
        }
    }

    /// Convert a CVPixelBuffer to JPEG data.
    private static func convertToJPEG(
        pixelBuffer: CVPixelBuffer,
        context: CIContext
    ) -> Data? {
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)

        // Rotate to portrait orientation (front camera is landscape-left)
        let oriented = ciImage.oriented(.right)

        guard let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) else {
            return nil
        }

        return context.jpegRepresentation(
            of: oriented,
            colorSpace: colorSpace,
            options: [kCGImageDestinationLossyCompressionQuality: jpegQuality]
        )
    }
}
