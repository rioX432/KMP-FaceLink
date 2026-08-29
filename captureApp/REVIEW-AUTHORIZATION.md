# Source-build and exact-binary run authorization

Performance diagnostics have two independent gates. Ordinary guided capture and stored-take
verification do not require either gate.

## 1. Source-build authorization

After one exact source-readiness packet receives CTO, Face Tracking, Android, and iOS PASS with
blocker zero, one canonical source authorization may be created. It permits building a candidate;
it does **not** permit starting a device diagnostic.

The UTF-8 file uses LF, ends with one LF, and contains exactly:

```properties
packetId=<approved source packet ID>
packetManifestSha256=<approved source packet manifest SHA-256>
sourceManifestSha256=<approved source manifest SHA-256>
```

No comments, blank/extra/duplicate keys, escaping, CRLF, missing terminal LF, uppercase digest, or
whitespace around `=` are accepted. Android consumes it only through:

```text
-Pfacelink.reviewAuthorizationFile=/absolute/path/to/source-authorization.properties
```

iOS consumes the exact same bytes only through `KMP_REVIEW_AUTHORIZATION_FILE`. Both build systems
reject a missing terminal LF before creating a candidate. A normal build leaves Android fields
blank and omits the iOS resource.

## 2. Post-binary run authorization

Freeze the actual APK or complete iOS app bundle, its embedded source authorization, signatures, source
hashes, and build commands in a separate exact-binary packet. The same four roles must review that
one manifest hash. Each role emits a machine-readable status with this exact schema:

```json
{
  "schema_version": 1,
  "reviewer_role": "android",
  "manifest_sha256": "<same exact-binary manifest SHA-256>",
  "verdict": "PASS",
  "blocker_count": 0
}
```

The only accepted roles are `cto`, `face`, `android`, and `ios`. The local issuer
`scripts/issue_facelink_run_authorization.py` verifies every frozen packet component and all four
statuses before using the private P-256 key. Private signing material stays outside Git and review
packets. Android and iOS contain only the uncompressed public key, whose SHA-256 key ID is:

```text
e56a022d8e99eb94af5521b207b8dc2e73228cf7ca00eac9d94c911289c14f1c
```

The issued UTF-8 file contains eight LF-terminated lines in fixed order:

```properties
schema=facelink-run-authorization-v1
platform=android-apk
binaryPacketId=<approved exact-binary packet ID>
binaryPacketManifestSha256=<approved exact-binary manifest SHA-256>
binarySha256=<installed APK SHA-256 or canonical iOS app-bundle content SHA-256>
sourceAuthorizationSha256=<embedded source authorization SHA-256>
signingKeyId=<reviewed public-key SHA-256>
signatureDerHex=<lowercase DER ECDSA signature over the preceding seven lines including LF>
```

`platform` is `android-apk` or `ios-app-bundle`. The app accepts the file only when its canonical
bytes and signature pass, its source-authorization hash equals the embedded build value, and its
binary hash equals the APK/app bundle currently running. Android rejects split-APK installations
because a single reviewed APK digest would not identify the full installed code set.

The performance button starts disabled. The app validates after file selection and again immediately
before camera/offline diagnostic work. A source-authorized candidate with no signed post-binary file
therefore remains non-executable for performance measurement.
