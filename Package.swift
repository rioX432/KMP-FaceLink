// swift-tools-version: 5.9
import PackageDescription

let version = "0.3.202607140517"

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
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476380675.zip",
            checksum: "1698c9d3bcd7056d76af540ccafbb3b48406dd25ceb9dcd18eb05205f4f2ba91"
        ),
        .binaryTarget(
            name: "KMPFaceLinkAvatar",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476380669.zip",
            checksum: "99a6eb6c82cfb7c97da920108e3fd3b1cf9efbd002a42e32de73a0ac2b6c0be6"
        ),
        .binaryTarget(
            name: "KMPFaceLinkActions",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476380676.zip",
            checksum: "420154938b102474f1d441511164599d5b0b5bef0ea78a69ae815f03f22d8674"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVoice",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476380709.zip",
            checksum: "5a5d841a3acc295c633b3bc6078e35fb381779bff3813040edd8d80e8feed688"
        ),
        .binaryTarget(
            name: "KMPFaceLinkLlm",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476380694.zip",
            checksum: "3e938324de0d3b78500991768c53d950a968249a25014e8c92f668eab46cf629"
        ),
        .binaryTarget(
            name: "KMPFaceLinkPipeline",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476380704.zip",
            checksum: "dd63e667e8239ce85392a9f4c2078d6664fa9b0daa07a722f381a215c3f0eff2"
        ),
        .binaryTarget(
            name: "KMPFaceLinkNetworking",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476380703.zip",
            checksum: "55b0140f824fc5d638799e09cc59091ae939ddfb64c2b1c0173171750a414739"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVMC",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476380706.zip",
            checksum: "a64aac68edf4cf0718365f846d4dcf0c79b49aee0153874f9ba574c7718e6f49"
        ),
        .binaryTarget(
            name: "KMPFaceLinkIFM",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476380670.zip",
            checksum: "b341085f8404c03a6f65f4935c71a72e6fa360e13294505e9734a2822aa0b1ed"
        ),
        .binaryTarget(
            name: "KMPFaceLinkInteract",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476380674.zip",
            checksum: "0e8b2ddf890a0a774ce959768375df016a7e0609fddb16e9d3e264d6e56fa5c1"
        ),
    ]
)
