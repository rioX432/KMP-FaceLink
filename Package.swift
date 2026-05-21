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
        .library(name: "KMPFaceLinkVoice", targets: ["KMPFaceLinkVoice"]),
        .library(name: "KMPFaceLinkLlm", targets: ["KMPFaceLinkLlm"]),
        .library(name: "KMPFaceLinkPipeline", targets: ["KMPFaceLinkPipeline"]),
        .library(name: "KMPFaceLinkNetworking", targets: ["KMPFaceLinkNetworking"]),
        .library(name: "KMPFaceLinkVMC", targets: ["KMPFaceLinkVMC"]),
        .library(name: "KMPFaceLinkIFM", targets: ["KMPFaceLinkIFM"]),
        .library(name: "KMPFaceLinkInteract", targets: ["KMPFaceLinkInteract"]),
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
            name: "KMPFaceLinkVoice",
            url: "\(repoUrl)/KMPFaceLinkVoice.xcframework.zip",
            checksum: "CHECKSUM_PLACEHOLDER"
        ),
        .binaryTarget(
            name: "KMPFaceLinkLlm",
            url: "\(repoUrl)/KMPFaceLinkLlm.xcframework.zip",
            checksum: "CHECKSUM_PLACEHOLDER"
        ),
        .binaryTarget(
            name: "KMPFaceLinkPipeline",
            url: "\(repoUrl)/KMPFaceLinkPipeline.xcframework.zip",
            checksum: "CHECKSUM_PLACEHOLDER"
        ),
        .binaryTarget(
            name: "KMPFaceLinkNetworking",
            url: "\(repoUrl)/KMPFaceLinkNetworking.xcframework.zip",
            checksum: "CHECKSUM_PLACEHOLDER"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVMC",
            url: "\(repoUrl)/KMPFaceLinkVMC.xcframework.zip",
            checksum: "CHECKSUM_PLACEHOLDER"
        ),
        .binaryTarget(
            name: "KMPFaceLinkIFM",
            url: "\(repoUrl)/KMPFaceLinkIFM.xcframework.zip",
            checksum: "CHECKSUM_PLACEHOLDER"
        ),
        .binaryTarget(
            name: "KMPFaceLinkInteract",
            url: "\(repoUrl)/KMPFaceLinkInteract.xcframework.zip",
            checksum: "CHECKSUM_PLACEHOLDER"
        ),
    ]
)
