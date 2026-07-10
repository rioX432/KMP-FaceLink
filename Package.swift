// swift-tools-version: 5.9
import PackageDescription

let version = "0.3.202607100235"

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
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/472060240.zip",
            checksum: "e3866f683d787a6dc6bc834d8c816fb40747a1319fadbfc87a831aedc804e560"
        ),
        .binaryTarget(
            name: "KMPFaceLinkAvatar",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/472060237.zip",
            checksum: "034b9370c274000161e5ef2e78c42758f01f4ea67a4599cee08ae7f3839a0253"
        ),
        .binaryTarget(
            name: "KMPFaceLinkActions",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/472060238.zip",
            checksum: "d1cc3defca1d5e0af42d6f08e0b68d557d1266247d74aa103156b32fca581ce1"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVoice",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/472060266.zip",
            checksum: "78cb89710bca2b6686d8a23a0b240573602ba4934c45cdc3e3d6161eb3b12559"
        ),
        .binaryTarget(
            name: "KMPFaceLinkLlm",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/472060245.zip",
            checksum: "58c553c7515bfdb491781fc714d4740e76ce4a1c6b13e3682d5cefbc5167ea26"
        ),
        .binaryTarget(
            name: "KMPFaceLinkPipeline",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/472060253.zip",
            checksum: "fb6912b8ac7b61bec87567fa8522714fb40e1413d5573b8f2d8d9a73d6d8cbe1"
        ),
        .binaryTarget(
            name: "KMPFaceLinkNetworking",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/472060247.zip",
            checksum: "066b56ac9ebf761ca7003e646c165a128073687de106a2e97d59d4e57024f83e"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVMC",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/472060252.zip",
            checksum: "ac96fea20473354314f75488376fcc38289dd88a7fb66f0635d413d5e15377e6"
        ),
        .binaryTarget(
            name: "KMPFaceLinkIFM",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/472060236.zip",
            checksum: "984d6bedc0a14d5576652ebd7a2362d22a02f0199537311a33704a6bd7b8a9ef"
        ),
        .binaryTarget(
            name: "KMPFaceLinkInteract",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/472060239.zip",
            checksum: "f3d8a38421bfe4b36bf433171aef4099ff300eb155e10a90435e301a878214b3"
        ),
    ]
)
