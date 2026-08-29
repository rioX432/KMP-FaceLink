import CryptoKit
import Foundation

@main
enum RunAuthorizationVerifierTests {
    static func main() throws {
        let privateKey = P256.Signing.PrivateKey()
        let publicKey = privateKey.publicKey.x963Representation
        let keyId = sha256(publicKey)
        let source = SourceReviewEvidence(
            packetId: "source-packet-v4",
            packetManifestSha256: String(repeating: "a", count: 64),
            sourceManifestSha256: String(repeating: "b", count: 64),
            authorizationSha256: String(repeating: "c", count: 64)
        )
        let binary = InstalledBinaryIdentity(
            platform: DiagnosticRunAuthorization.platformIOSAppBundle,
            sha256: String(repeating: "d", count: 64)
        )
        let valid = try authorization(privateKey: privateKey, keyId: keyId, source: source, binary: binary)

        let evidence = try DiagnosticRunAuthorization.verify(
            data: valid,
            source: source,
            installedBinary: binary,
            publicKeyX963: publicKey,
            expectedSigningKeyId: keyId
        )
        precondition(evidence.binaryPacketId == "binary-packet-v1")
        precondition(evidence.binarySha256 == binary.sha256)
        precondition(evidence.runAuthorizationSha256 == sha256(valid))

        expectFailure("source-only candidate") {
            _ = try DiagnosticRunAuthorization.verify(
                data: Data(),
                source: source,
                installedBinary: binary,
                publicKeyX963: publicKey,
                expectedSigningKeyId: keyId
            )
        }
        expectFailure("wrong app bundle") {
            _ = try DiagnosticRunAuthorization.verify(
                data: valid,
                source: source,
                installedBinary: InstalledBinaryIdentity(
                    platform: binary.platform,
                    sha256: String(repeating: "e", count: 64)
                ),
                publicKeyX963: publicKey,
                expectedSigningKeyId: keyId
            )
        }
        expectFailure("wrong source authorization") {
            _ = try DiagnosticRunAuthorization.verify(
                data: valid,
                source: SourceReviewEvidence(
                    packetId: source.packetId,
                    packetManifestSha256: source.packetManifestSha256,
                    sourceManifestSha256: source.sourceManifestSha256,
                    authorizationSha256: String(repeating: "f", count: 64)
                ),
                installedBinary: binary,
                publicKeyX963: publicKey,
                expectedSigningKeyId: keyId
            )
        }
        expectFailure("tampered packet") {
            let tampered = String(decoding: valid, as: UTF8.self)
                .replacingOccurrences(of: "binaryPacketId=binary-packet-v1", with: "binaryPacketId=binary-packet-v2")
            _ = try DiagnosticRunAuthorization.verify(
                data: Data(tampered.utf8),
                source: source,
                installedBinary: binary,
                publicKeyX963: publicKey,
                expectedSigningKeyId: keyId
            )
        }
        expectFailure("missing terminal LF") {
            _ = try DiagnosticRunAuthorization.verify(
                data: valid.dropLast(),
                source: source,
                installedBinary: binary,
                publicKeyX963: publicKey,
                expectedSigningKeyId: keyId
            )
        }
        expectFailure("extra key") {
            let extra = String(decoding: valid, as: UTF8.self)
                .replacingOccurrences(of: "signatureDerHex=", with: "extra=forbidden\nsignatureDerHex=")
            _ = try DiagnosticRunAuthorization.verify(
                data: Data(extra.utf8),
                source: source,
                installedBinary: binary,
                publicKeyX963: publicKey,
                expectedSigningKeyId: keyId
            )
        }
        expectFailure("wrong key") {
            _ = try DiagnosticRunAuthorization.verify(
                data: valid,
                source: source,
                installedBinary: binary,
                publicKeyX963: P256.Signing.PrivateKey().publicKey.x963Representation,
                expectedSigningKeyId: keyId
            )
        }

        let bundle = FileManager.default.temporaryDirectory
            .appendingPathComponent("facelink-bundle-digest-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(
            at: bundle.appendingPathComponent("Sub", isDirectory: true),
            withIntermediateDirectories: true
        )
        defer { try? FileManager.default.removeItem(at: bundle) }
        try Data("launcher".utf8).write(to: bundle.appendingPathComponent("App"))
        try Data("plist\n".utf8).write(to: bundle.appendingPathComponent("Info.plist"))
        try Data([0, 1, 2, 3]).write(to: bundle.appendingPathComponent("Sub/code.bin"))
        let bundleDigest = try DiagnosticRunAuthorization.bundleContentSha256(at: bundle)
        precondition(
            bundleDigest ==
                "d12b9a050e22251ce1fc108cf9fc1acb34b5306c9babfc4969e8d5141507a454"
        )

        try Data("added after authorization".utf8).write(
            to: bundle.appendingPathComponent("Sub/AddedAfterAuthorization")
        )
        let withAddedFile = try DiagnosticRunAuthorization.bundleContentSha256(at: bundle)
        precondition(withAddedFile != bundleDigest, "adding a bundled file must change the digest")
        try FileManager.default.removeItem(at: bundle.appendingPathComponent("Sub/AddedAfterAuthorization"))
        let afterRemoval = try DiagnosticRunAuthorization.bundleContentSha256(at: bundle)
        precondition(
            afterRemoval == bundleDigest,
            "removing an added file must restore the exact prior digest"
        )

        let symlink = bundle.appendingPathComponent("Sub/ForbiddenLink")
        try FileManager.default.createSymbolicLink(at: symlink, withDestinationURL: bundle)
        expectFailure("bundle symlink") {
            _ = try DiagnosticRunAuthorization.bundleContentSha256(at: bundle)
        }
        try FileManager.default.removeItem(at: symlink)

        print("PASS: signed run-authorization and bundle-digest controls")
    }

    private static func authorization(
        privateKey: P256.Signing.PrivateKey,
        keyId: String,
        source: SourceReviewEvidence,
        binary: InstalledBinaryIdentity
    ) throws -> Data {
        let payload = """
        schema=facelink-run-authorization-v1
        platform=ios-app-bundle
        binaryPacketId=binary-packet-v1
        binaryPacketManifestSha256=1111111111111111111111111111111111111111111111111111111111111111
        binarySha256=\(binary.sha256)
        sourceAuthorizationSha256=\(source.authorizationSha256)
        signingKeyId=\(keyId)
        """ + "\n"
        let signature = try privateKey.signature(for: Data(payload.utf8)).derRepresentation
        let signatureHex = signature.map { String(format: "%02x", $0) }.joined()
        return Data((payload + "signatureDerHex=\(signatureHex)\n").utf8)
    }

    private static func expectFailure(_ label: String, _ operation: () throws -> Void) {
        do {
            try operation()
            fatalError("Expected failure: \(label)")
        } catch {
            // Expected.
        }
    }

    private static func sha256(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}
