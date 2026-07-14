// swift-tools-version: 5.9
import PackageDescription

let version = "0.3.202607140230"

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
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476293740.zip",
            checksum: "16020ace1e517621b2bd337ef9e85568bb43cc034653bded58b7b56bbbe32031"
        ),
        .binaryTarget(
            name: "KMPFaceLinkAvatar",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476293742.zip",
            checksum: "8888735181747088d738336f84f2b29ca37c0ee3255eeea1b36c9fc7d9ea2a37"
        ),
        .binaryTarget(
            name: "KMPFaceLinkActions",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476293743.zip",
            checksum: "61fa45a57dcfd07e435657d5ead80a7e6d74f9deb52e37f65840bdc8e69dfd2e"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVoice",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476293799.zip",
            checksum: "ac52aad1bfcbaa10f78052b46d23af19b0b40b9a09ec5870450d12fdc413848a"
        ),
        .binaryTarget(
            name: "KMPFaceLinkLlm",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476293788.zip",
            checksum: "338f376bc292253bab3e5dcff6f26a2381c44218bb929ed3d45900b3a5f045b6"
        ),
        .binaryTarget(
            name: "KMPFaceLinkPipeline",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476293796.zip",
            checksum: "c082a2b1a1a267039d2699b27bb9796f1e2250e09d4a61462576baae2ec7e894"
        ),
        .binaryTarget(
            name: "KMPFaceLinkNetworking",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476293792.zip",
            checksum: "85f686de69b4427f42a3a115a2c34b735a8732fc360685ed09496b6cf218b45c"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVMC",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476293797.zip",
            checksum: "e33c0550579ea523965644ecd9dc24af6959d170c838edbe57010b3148ccbcab"
        ),
        .binaryTarget(
            name: "KMPFaceLinkIFM",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476293744.zip",
            checksum: "d7127389eee1a886b20cea291ff33064bba8a18382e44ee940a1f793c0c0609b"
        ),
        .binaryTarget(
            name: "KMPFaceLinkInteract",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476293741.zip",
            checksum: "aec588d24abbfa90780bd23a02c4a9127dc79a32969ba23b8d425d505d950f68"
        ),
    ]
)
