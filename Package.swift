// swift-tools-version: 5.9
import PackageDescription

let version = "0.3.202607140655"

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
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476429244.zip",
            checksum: "0c681bb392ca719029513c836ef94067e53ef439de971e2e8b992826e708a1f4"
        ),
        .binaryTarget(
            name: "KMPFaceLinkAvatar",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476429245.zip",
            checksum: "dc151b14a3ee1acd94e76799bbd47d4ddcbbb4ae557620cf5cd113f02b8cfcfc"
        ),
        .binaryTarget(
            name: "KMPFaceLinkActions",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476429250.zip",
            checksum: "56dbfe93e1ebf9ad53ceca80aa79b134a77a277076abfeed6e68f9e4f4a1c9d1"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVoice",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476429277.zip",
            checksum: "2bbd9846fc29183af2bf4ee2c0e78638a7e18073f0726af0f9c34255d9b6c72e"
        ),
        .binaryTarget(
            name: "KMPFaceLinkLlm",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476429261.zip",
            checksum: "570b73f764fb601dfb070030888ac0348680b2ca5f2f9b38ad6d97ee3aa020c2"
        ),
        .binaryTarget(
            name: "KMPFaceLinkPipeline",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476429273.zip",
            checksum: "5ce9866c89ef3dd6140aeac621751359013bd4b4153f950fce50c7caa9e0c1f4"
        ),
        .binaryTarget(
            name: "KMPFaceLinkNetworking",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476429271.zip",
            checksum: "734512684ecf760d39891fa4d03d831855c5b1bd7d0cbc59fb6356bb001125da"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVMC",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476429275.zip",
            checksum: "bee3259ce789204dbe9b311d8586f0fecf0747dfd52735ded44f7ed2cff6cf06"
        ),
        .binaryTarget(
            name: "KMPFaceLinkIFM",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476429246.zip",
            checksum: "02d90cfee69d61b5011d5df3ea3cbf4dcbb03c6478abe07d8b005830452b441b"
        ),
        .binaryTarget(
            name: "KMPFaceLinkInteract",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/476429249.zip",
            checksum: "c97cf02e8b49bb3838e32129b2c84e07c6a0d8cd6be5410f7324bf1c70135437"
        ),
    ]
)
