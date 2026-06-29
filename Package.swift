// swift-tools-version: 5.9
import PackageDescription

let version = "0.3.202606290348"

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
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460762048.zip",
            checksum: "51f7db0e89aadc1f4091f39e33be0d4b720d9a452817e4e04f09a22883a6823b"
        ),
        .binaryTarget(
            name: "KMPFaceLinkAvatar",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460762050.zip",
            checksum: "2ed978ddee36150ae7ce4671c9a675137b7c6595b4dd6bd5dd419d39c2ed502c"
        ),
        .binaryTarget(
            name: "KMPFaceLinkActions",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460762047.zip",
            checksum: "50b83e98d4ace6bae3d05fe01dc53ed80b2aeabd5c825e70bfbe9f9e0078d45d"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVoice",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460762059.zip",
            checksum: "c890b93bef24b8e375504e786f0a74caefb3c43ca12457f127fd88f39908401c"
        ),
        .binaryTarget(
            name: "KMPFaceLinkLlm",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460762054.zip",
            checksum: "1ce4521e0440bc49b3cda71b87a2e4375931354e0afd549e3972b9a2491ccdff"
        ),
        .binaryTarget(
            name: "KMPFaceLinkPipeline",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460762058.zip",
            checksum: "b4b7b6e5ea73191f09da149b54708cf3e464bdf771209d37877b77603d96ffb4"
        ),
        .binaryTarget(
            name: "KMPFaceLinkNetworking",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460762057.zip",
            checksum: "733a194101b1b3330719fd39aaac3299a5e3a4338dd5a1a6e07b45ea2a3a7fc5"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVMC",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460762060.zip",
            checksum: "27bba35381891cbc15da1a2c4c7b5a2853aeda838dba780d01d99799f5cb316e"
        ),
        .binaryTarget(
            name: "KMPFaceLinkIFM",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460762051.zip",
            checksum: "d9cfe682daadd419fa2049ad53b01f5d9b122c8d9b7a08ab43883d2d82715cc7"
        ),
        .binaryTarget(
            name: "KMPFaceLinkInteract",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460762049.zip",
            checksum: "037642c78550edd70e41380204f46bfc8ea4484acb523dd5f7a434438f64b1b0"
        ),
    ]
)
