// swift-tools-version: 5.9
import PackageDescription

let version = "0.3.202609060545"

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
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/546995604.zip",
            checksum: "1bd95800f3f090aa536079b2fdc66888fbb25dfbf56ab4ae67a35ec51021d915"
        ),
        .binaryTarget(
            name: "KMPFaceLinkAvatar",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/546995605.zip",
            checksum: "ee7c778b6ad1cba1c9e916885d8bbc7b35c4cb677aac66e34574da1ebdfa4dee"
        ),
        .binaryTarget(
            name: "KMPFaceLinkActions",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/546995607.zip",
            checksum: "1bfb00a69155b4316a6e54c24f4d84b879f7184d6a1c41a35e73128c4e05f29a"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVoice",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/546995671.zip",
            checksum: "299eac31201d5d4d360edeedb0a0dfa8cde1fffb1f2831a91e61acbbd74b37e1"
        ),
        .binaryTarget(
            name: "KMPFaceLinkLlm",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/546995656.zip",
            checksum: "47779fc0c728743d2e73f2e40ec2e86a0020e7425223b2bed37940d7cc07b4b8"
        ),
        .binaryTarget(
            name: "KMPFaceLinkPipeline",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/546995669.zip",
            checksum: "ec107280e5e555326e6ee4c839c7aeb9402a3ba128850947f56543b8c1c54992"
        ),
        .binaryTarget(
            name: "KMPFaceLinkNetworking",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/546995662.zip",
            checksum: "b2c776d2188d1d7b321871b137c3b320ed4b3d875e241463534885144fca8ba5"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVMC",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/546995667.zip",
            checksum: "f79e471c01db63f64d8f658dad02640b6343227b4b759efe44e71120da80377b"
        ),
        .binaryTarget(
            name: "KMPFaceLinkIFM",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/546995609.zip",
            checksum: "6b8e690d8f98ff3dd2ddc6082f1d64d3facf0623af09d8d72c6e6a13241bc177"
        ),
        .binaryTarget(
            name: "KMPFaceLinkInteract",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/546995606.zip",
            checksum: "e709d2f7e8c6e12bd1c7d4dd6efa6dca8a220fab73abf0fa0d070f8406e54b5c"
        ),
    ]
)
