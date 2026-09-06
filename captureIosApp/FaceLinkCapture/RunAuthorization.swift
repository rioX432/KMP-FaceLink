import CryptoKit
import Foundation

struct SourceReviewEvidence {
    let packetId: String
    let packetManifestSha256: String
    let sourceManifestSha256: String
    let authorizationSha256: String
}

struct InstalledBinaryIdentity {
    let platform: String
    let sha256: String
}

struct ValidatedRunAuthorization {
    let binaryPacketId: String
    let binaryPacketManifestSha256: String
    let binarySha256: String
    let runAuthorizationSha256: String
    let signingKeyId: String
}

enum DiagnosticRunAuthorization {
    static let platformIOSAppBundle = "ios-app-bundle"
    static let schema = "facelink-run-authorization-v1"
    static let signingKeyId = "e56a022d8e99eb94af5521b207b8dc2e73228cf7ca00eac9d94c911289c14f1c"

    private static let publicKeyX963Base64 =
        "BPtWONREX15XsgS9mphckQyaEhgHIEyOwZYcKior2LxEzf4EcLPSQ2NZgcs4gJ/gGd91ciVSOxt//wkSgGZEE0I="
    private static let expectedKeys = [
        "schema",
        "platform",
        "binaryPacketId",
        "binaryPacketManifestSha256",
        "binarySha256",
        "sourceAuthorizationSha256",
        "signingKeyId",
        "signatureDerHex",
    ]

    static func verify(
        data: Data,
        source: SourceReviewEvidence,
        installedBinary: InstalledBinaryIdentity,
        publicKeyX963: Data = Data(base64Encoded: publicKeyX963Base64)!,
        expectedSigningKeyId: String = signingKeyId
    ) throws -> ValidatedRunAuthorization {
        guard (1...(16 * 1024)).contains(data.count) else {
            throw failure("Run authorization must be non-empty and no larger than 16384 bytes.")
        }
        guard source.packetId.isCanonicalPacketId,
              source.packetManifestSha256.isLowercaseSHA256,
              source.sourceManifestSha256.isLowercaseSHA256,
              source.authorizationSha256.isLowercaseSHA256 else {
            throw failure("Embedded source review evidence is incomplete or noncanonical.")
        }
        guard installedBinary.platform == platformIOSAppBundle,
              installedBinary.sha256.isLowercaseSHA256 else {
            throw failure("Installed binary identity is not a canonical iOS app-bundle digest.")
        }
        guard let content = String(data: data, encoding: .utf8), Data(content.utf8) == data,
              !content.contains("\r"), content.hasSuffix("\n") else {
            throw failure("Run authorization must be canonical UTF-8, use LF, and end with one LF.")
        }
        let lines = content.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        guard lines.count == expectedKeys.count + 1, lines.last?.isEmpty == true else {
            throw failure("Run authorization must contain exactly eight LF-terminated lines.")
        }

        var values: [String: String] = [:]
        for (index, expectedKey) in expectedKeys.enumerated() {
            let line = lines[index]
            guard let separator = line.firstIndex(of: "="),
                  line[separator...].dropFirst().allSatisfy({ $0 != "=" }) else {
                throw failure("Run authorization line \(index + 1) must contain exactly one '='.")
            }
            let key = String(line[..<separator])
            let value = String(line[line.index(after: separator)...])
            guard key == expectedKey else {
                throw failure("Run authorization keys/order do not match the canonical schema.")
            }
            values[key] = value
        }

        guard values["schema"] == schema else { throw failure("Unsupported run authorization schema.") }
        guard values["platform"] == installedBinary.platform else {
            throw failure("Run authorization platform does not match this app bundle.")
        }
        guard let binaryPacketId = values["binaryPacketId"], binaryPacketId.isCanonicalPacketId else {
            throw failure("Binary review packet ID is not canonical.")
        }
        guard let binaryPacketManifestSha = values["binaryPacketManifestSha256"],
              binaryPacketManifestSha.isLowercaseSHA256 else {
            throw failure("Binary review packet manifest must be a lowercase SHA-256.")
        }
        guard values["binarySha256"] == installedBinary.sha256 else {
            throw failure("Run authorization does not name the installed app bundle.")
        }
        guard values["sourceAuthorizationSha256"] == source.authorizationSha256 else {
            throw failure("Run authorization does not name the embedded source authorization.")
        }
        guard expectedSigningKeyId.isLowercaseSHA256,
              values["signingKeyId"] == expectedSigningKeyId,
              sha256(publicKeyX963) == expectedSigningKeyId else {
            throw failure("Run authorization signing key is not trusted.")
        }
        guard let signatureText = values["signatureDerHex"],
              signatureText.count.isMultiple(of: 2),
              signatureText.allSatisfy({ $0.isLowercaseHexDigit }),
              let signatureData = Data(lowercaseHex: signatureText) else {
            throw failure("Run authorization signature must use lowercase DER hex.")
        }
        let payload = Data((lines.prefix(7).joined(separator: "\n") + "\n").utf8)
        let publicKey = try P256.Signing.PublicKey(x963Representation: publicKeyX963)
        let signature = try P256.Signing.ECDSASignature(derRepresentation: signatureData)
        guard publicKey.isValidSignature(signature, for: payload) else {
            throw failure("Run authorization signature is invalid.")
        }

        return ValidatedRunAuthorization(
            binaryPacketId: binaryPacketId,
            binaryPacketManifestSha256: binaryPacketManifestSha,
            binarySha256: installedBinary.sha256,
            runAuthorizationSha256: sha256(data),
            signingKeyId: expectedSigningKeyId
        )
    }

    static func installedAppBundle() throws -> InstalledBinaryIdentity {
        return InstalledBinaryIdentity(
            platform: platformIOSAppBundle,
            sha256: try bundleContentSha256(at: Bundle.main.bundleURL)
        )
    }

    /**
     * Hash every regular app-bundle file in sorted relative-path order.
     *
     * A Debug app's main executable can be only a stable launcher while its Swift/KMP code lives in
     * a debug dylib. Binding only `Bundle.executableURL` would therefore miss the code under review.
     * Each record is: 4-byte big-endian UTF-8 path length, path bytes, 8-byte big-endian file size,
     * then file bytes. Symlinks are rejected so two filesystem layouts cannot share one digest.
     */
    static func bundleContentSha256(at root: URL) throws -> String {
        let fileManager = FileManager.default
        let rootURL = root.standardizedFileURL
        let rootPath = rootURL.path
        var enumerationError: Error?
        guard let enumerator = fileManager.enumerator(
            at: rootURL,
            includingPropertiesForKeys: [.isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey],
            options: [],
            errorHandler: { _, error in
                enumerationError = error
                return false
            }
        ) else {
            throw failure("App bundle cannot be enumerated.")
        }
        var files: [(relative: String, url: URL, size: UInt64)] = []
        while let url = enumerator.nextObject() as? URL {
            let values = try url.resourceValues(
                forKeys: [.isRegularFileKey, .isSymbolicLinkKey, .fileSizeKey]
            )
            if values.isSymbolicLink == true {
                throw failure("App bundle symlinks are not accepted by the canonical digest.")
            }
            guard values.isRegularFile == true else { continue }
            let path = url.standardizedFileURL.path
            guard path.hasPrefix(rootPath + "/"), let fileSize = values.fileSize, fileSize >= 0 else {
                throw failure("App bundle file identity is not canonical.")
            }
            files.append((
                relative: String(path.dropFirst(rootPath.count + 1)),
                url: url,
                size: UInt64(fileSize)
            ))
        }
        if let enumerationError { throw enumerationError }
        guard !files.isEmpty else { throw failure("App bundle contains no regular files.") }

        var hash = SHA256()
        for file in files.sorted(by: { $0.relative < $1.relative }) {
            let pathData = Data(file.relative.utf8)
            guard pathData.count <= Int(UInt32.max) else {
                throw failure("App bundle relative path is too long.")
            }
            update(UInt32(pathData.count), in: &hash)
            hash.update(data: pathData)
            update(file.size, in: &hash)
            try update(file: file.url, in: &hash)
        }
        return hash.finalize().map { String(format: "%02x", $0) }.joined()
    }

    private static func sha256(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private static func update(file url: URL, in hash: inout SHA256) throws {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        while true {
            let chunk = try handle.read(upToCount: 64 * 1024) ?? Data()
            if chunk.isEmpty { break }
            hash.update(data: chunk)
        }
    }

    private static func update<T: FixedWidthInteger>(_ value: T, in hash: inout SHA256) {
        var bigEndian = value.bigEndian
        withUnsafeBytes(of: &bigEndian) { hash.update(data: Data($0)) }
    }

    private static func failure(_ message: String) -> NSError {
        NSError(
            domain: "io.github.kmpfacelink.run-authorization",
            code: 1,
            userInfo: [NSLocalizedDescriptionKey: message]
        )
    }
}

extension String {
    var isLowercaseSHA256: Bool {
        count == 64 && unicodeScalars.allSatisfy { scalar in
            scalar.value >= 48 && scalar.value <= 57 || scalar.value >= 97 && scalar.value <= 102
        }
    }

    var isCanonicalPacketId: Bool {
        guard count <= 128, let first = unicodeScalars.first else { return false }
        let isLowercaseLetterOrDigit = { (scalar: Unicode.Scalar) in
            scalar.value >= 48 && scalar.value <= 57 || scalar.value >= 97 && scalar.value <= 122
        }
        guard isLowercaseLetterOrDigit(first) else { return false }
        return unicodeScalars.allSatisfy { scalar in
            isLowercaseLetterOrDigit(scalar) || scalar == "." || scalar == "_" || scalar == "-"
        }
    }
}

private extension Character {
    var isLowercaseHexDigit: Bool {
        ("0"..."9").contains(self) || ("a"..."f").contains(self)
    }
}

private extension Data {
    init?(lowercaseHex text: String) {
        guard text.count.isMultiple(of: 2) else { return nil }
        var bytes: [UInt8] = []
        bytes.reserveCapacity(text.count / 2)
        var index = text.startIndex
        while index < text.endIndex {
            let next = text.index(index, offsetBy: 2)
            guard let byte = UInt8(text[index..<next], radix: 16) else { return nil }
            bytes.append(byte)
            index = next
        }
        self = Data(bytes)
    }
}
