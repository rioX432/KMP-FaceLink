// swift-tools-version: 5.9
import PackageDescription

let version = "0.3.202607100104"

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
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471988347.zip",
            checksum: "fa7608d8647e4f5bf0ff47a6172effc1b4786a7fc212ee16cabcd4d5a2c4054a"
        ),
        .binaryTarget(
            name: "KMPFaceLinkAvatar",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471988345.zip",
            checksum: "a4d805606582d464cfaf6b054e9ce738aa5af49f68549171b95c3fa2fdd730b6"
        ),
        .binaryTarget(
            name: "KMPFaceLinkActions",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471988348.zip",
            checksum: "e48ffea77caaf23ffed7f106d2afe2046c7c60b1d6547bdd4a15dafb95cc1d1d"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVoice",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471988396.zip",
            checksum: "4bbfb30e5f740598c68089301756965a33403413551cb69bcd72cce2dc921c9d"
        ),
        .binaryTarget(
            name: "KMPFaceLinkLlm",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471988356.zip",
            checksum: "5d3860ceda327bc3ccfbc81749d9e6bea7006ac1d80903e030c7c23ddc2bf8c7"
        ),
        .binaryTarget(
            name: "KMPFaceLinkPipeline",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471988376.zip",
            checksum: "5a9bb098fbcff52dfb8088e78f81010671594de8d969ce376f3f2122329ea1cd"
        ),
        .binaryTarget(
            name: "KMPFaceLinkNetworking",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471988372.zip",
            checksum: "13fae8f43d3c26691eb03f67100d6c1d6d535b28269e3e427f76d7c5c04570d2"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVMC",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471988394.zip",
            checksum: "5e2a5c52f9b6c33c811391bc47ff3aaa25a3d8996f85e78705f8b965d5d84450"
        ),
        .binaryTarget(
            name: "KMPFaceLinkIFM",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471988346.zip",
            checksum: "63dd21cb2c72fb637ed96b449013fdb8abf83ba1337f022dbf84b6e940ac051e"
        ),
        .binaryTarget(
            name: "KMPFaceLinkInteract",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/471988344.zip",
            checksum: "6a88ed60c143346e035175c387ae95bbb8b7b2ace6e7e7210226975992a0ff3c"
        ),
    ]
)
