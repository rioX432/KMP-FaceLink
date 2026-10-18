// swift-tools-version: 5.9
import PackageDescription

let version = "0.3.202609061042"

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
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/547143757.zip",
            checksum: "d4e88831e9655f94129992b46176314b6a61946b131a56df0b7c5a2b9a294f13"
        ),
        .binaryTarget(
            name: "KMPFaceLinkAvatar",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/547143756.zip",
            checksum: "0ccd5833079dbe3beb6fc35842a35bb3ef472090f5682182784617d12b394f62"
        ),
        .binaryTarget(
            name: "KMPFaceLinkActions",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/547143763.zip",
            checksum: "c63ead09826a62d0c128e38d1721fefa130ba207a9bfef6b155861a8e22ccb4a"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVoice",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/547143849.zip",
            checksum: "2509f2f6363e8f23386973add53fd3c50286d17f62d4c001265689ff80f1b8e5"
        ),
        .binaryTarget(
            name: "KMPFaceLinkLlm",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/547143779.zip",
            checksum: "2f49f6f39568b02eceb164b21c723c28622c3421ac13f79736fb8b1eb257f856"
        ),
        .binaryTarget(
            name: "KMPFaceLinkPipeline",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/547143806.zip",
            checksum: "4b16b925ca53fdf4b73d83cf6fa3a7e51d86c75568227813bf013e7615546319"
        ),
        .binaryTarget(
            name: "KMPFaceLinkNetworking",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/547143797.zip",
            checksum: "ff61bb838c97ba0e5878e529ddb32954ae919e9e283a2e844311d6a93a2afec4"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVMC",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/547143820.zip",
            checksum: "66d943aeebcba2bdb3711db04c88a4f9620b642d3dac989b8570121f1ad9579c"
        ),
        .binaryTarget(
            name: "KMPFaceLinkIFM",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/547143758.zip",
            checksum: "5dc16e937216fcee2f87ade9dd5501fc39d8d1be7c62ef262701525d4e730a63"
        ),
        .binaryTarget(
            name: "KMPFaceLinkInteract",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/547143759.zip",
            checksum: "b06be85bfb9cc17ed50ae45151b0877a9730e771a59b698d8a5a193c1c2d5f42"
        ),
    ]
)
