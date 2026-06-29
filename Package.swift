// swift-tools-version: 5.9
import PackageDescription

let version = "0.3.202606290701"

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
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460889300.zip",
            checksum: "4af086a611595d3ca06b64f1f2b2440a9d16219dd9df34cf2a6c8c288e32ff0f"
        ),
        .binaryTarget(
            name: "KMPFaceLinkAvatar",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460889301.zip",
            checksum: "8eeac145a84630886622e3717b97407d655db75170ce1a708ee8b88a48d99d08"
        ),
        .binaryTarget(
            name: "KMPFaceLinkActions",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460889302.zip",
            checksum: "d5b216145187c65438420ebf9768d55f57257a11b4d38a3783788e45e7426f1b"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVoice",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460889347.zip",
            checksum: "ea50818e23177bc0d1ae712920f8a5622e811314b17cca9e7593603d4fb09d0b"
        ),
        .binaryTarget(
            name: "KMPFaceLinkLlm",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460889324.zip",
            checksum: "e160fefb6d371c12984eb3339c0e0c6e6d89a04a6d0a74385bc1f63fc21178e5"
        ),
        .binaryTarget(
            name: "KMPFaceLinkPipeline",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460889341.zip",
            checksum: "e1f98b6f5e0ddc68a17abec283aefce33c5f669a8adfa36d0eebcbc683f0dfe4"
        ),
        .binaryTarget(
            name: "KMPFaceLinkNetworking",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460889336.zip",
            checksum: "cf850b6886066178fba1779ab242292346169144efe98af95eb4c9e047fcddbf"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVMC",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460889343.zip",
            checksum: "c2df08afe4ecffa681c627b88fd266a73ad12ac7b21f48d909c9db6c4d052b8c"
        ),
        .binaryTarget(
            name: "KMPFaceLinkIFM",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460889304.zip",
            checksum: "465346277a000996f7de0b4ce761e78771b837b0463e04360f581355fbe66ef8"
        ),
        .binaryTarget(
            name: "KMPFaceLinkInteract",
            url: "https://api.github.com/repos/AnotherBall/KMP-FaceLink/releases/assets/460889303.zip",
            checksum: "d8c1919898f3e600d99ad44198c36f0c7d508a6cda042cef7d01e66e550a783c"
        ),
    ]
)
