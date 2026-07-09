// swift-tools-version: 5.9
import PackageDescription

let version = "0.3.202607090932"

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
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471234198.zip",
            checksum: "eedbac109bcb76fe85d9384cd12860ed6fd0aff7742895ec20c5d7540b62c4b4"
        ),
        .binaryTarget(
            name: "KMPFaceLinkAvatar",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471234197.zip",
            checksum: "ef9adb83ed9cb94b263ef0b3d45ac0d26d29db8d92a31a8f3d2b95c473894cc2"
        ),
        .binaryTarget(
            name: "KMPFaceLinkActions",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471234192.zip",
            checksum: "e2139c8ed73a686f6fbff1008d36209f4b829164b4f821725b4664bbfbf4b72e"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVoice",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471234226.zip",
            checksum: "214087eb1fc2563a285c783a6f8909b2ac61f34ed45d23195cd51c85db31690f"
        ),
        .binaryTarget(
            name: "KMPFaceLinkLlm",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471234213.zip",
            checksum: "2242f260e55946a1702bdc3c4af7aa8e71cccc7b52142af83373055d0d21fb63"
        ),
        .binaryTarget(
            name: "KMPFaceLinkPipeline",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471234216.zip",
            checksum: "d86360d74cf117adacaa8709972549f2c27659a93fe4c9daf1f285ae5cddddef"
        ),
        .binaryTarget(
            name: "KMPFaceLinkNetworking",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471234215.zip",
            checksum: "1d750fa3398cc4b77d049c717a395bba7347497966f8ecc9dec93a02635c1ead"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVMC",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471234218.zip",
            checksum: "35d5bf44307b0510e65e9f2e6221866b93801ef4e8de8a8249952f9a00027add"
        ),
        .binaryTarget(
            name: "KMPFaceLinkIFM",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471234195.zip",
            checksum: "b75e505a55faa8feae29319d3de6e50afb01602a4f0e25903258510a70d44610"
        ),
        .binaryTarget(
            name: "KMPFaceLinkInteract",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471234194.zip",
            checksum: "d2d6a8768e303ddc635e74b2507338fd2bb66b13258ca207728599923e20c3a0"
        ),
    ]
)
