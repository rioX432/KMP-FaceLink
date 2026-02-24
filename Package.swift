// swift-tools-version: 5.9
import PackageDescription

let version = "0.1.0"
let repoUrl = "https://github.com/rioX432/KMP-FaceLink/releases/download/v\(version)"

let package = Package(
    name: "KMPFaceLink",
    platforms: [
        .iOS(.v17),
    ],
    products: [
        .library(name: "KMPFaceLink", targets: ["KMPFaceLink"]),
        .library(name: "KMPFaceLinkAvatar", targets: ["KMPFaceLinkAvatar"]),
        .library(name: "KMPFaceLinkActions", targets: ["KMPFaceLinkActions"]),
        .library(name: "KMPFaceLinkEffects", targets: ["KMPFaceLinkEffects"]),
        .library(name: "KMPFaceLinkStream", targets: ["KMPFaceLinkStream"]),
        .library(name: "KMPFaceLinkVoice", targets: ["KMPFaceLinkVoice"]),
    ],
    targets: [
        .binaryTarget(
            name: "KMPFaceLink",
            url: "\(repoUrl)/KMPFaceLink.xcframework.zip",
            checksum: "CHECKSUM_PLACEHOLDER"
        ),
        .binaryTarget(
            name: "KMPFaceLinkAvatar",
            url: "\(repoUrl)/KMPFaceLinkAvatar.xcframework.zip",
            checksum: "CHECKSUM_PLACEHOLDER"
        ),
        .binaryTarget(
            name: "KMPFaceLinkActions",
            url: "\(repoUrl)/KMPFaceLinkActions.xcframework.zip",
            checksum: "CHECKSUM_PLACEHOLDER"
        ),
        .binaryTarget(
            name: "KMPFaceLinkEffects",
            url: "\(repoUrl)/KMPFaceLinkEffects.xcframework.zip",
            checksum: "CHECKSUM_PLACEHOLDER"
        ),
        .binaryTarget(
            name: "KMPFaceLinkStream",
            url: "\(repoUrl)/KMPFaceLinkStream.xcframework.zip",
            checksum: "CHECKSUM_PLACEHOLDER"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVoice",
            url: "\(repoUrl)/KMPFaceLinkVoice.xcframework.zip",
            checksum: "CHECKSUM_PLACEHOLDER"
        ),
    ]
)
