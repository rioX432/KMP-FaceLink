// swift-tools-version: 5.9
import PackageDescription

let version = "0.3.202610180714"

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
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/652844797.zip",
            checksum: "69241092ae2218b9bb1f4e38a15c7ae145f48d11a50cf8a3a0743e118e1df18a"
        ),
        .binaryTarget(
            name: "KMPFaceLinkAvatar",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/652844794.zip",
            checksum: "a69656636c1f666ecc303e60fb857a648f2cc5e0a9c84cec3b5cbc2b036ae603"
        ),
        .binaryTarget(
            name: "KMPFaceLinkActions",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/652844796.zip",
            checksum: "b0f78a4d5072560e2ba09cf35d3e2938449b21866fc3e747a7c8ac33d2f65117"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVoice",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/652844854.zip",
            checksum: "7b2cefba6bd5108ff4af07e4513353f9865f8a7e5b6ae6718b5b8ba5b2ac1d33"
        ),
        .binaryTarget(
            name: "KMPFaceLinkLlm",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/652844820.zip",
            checksum: "4f5e090a9b3bea9839e878953a865311922b5f44a7f1f1edad7c35111e290a5b"
        ),
        .binaryTarget(
            name: "KMPFaceLinkPipeline",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/652844842.zip",
            checksum: "2394c39fc97b62f210815ad5e64ffbae00695efa9c5cf63f167abae0a7bb4a2f"
        ),
        .binaryTarget(
            name: "KMPFaceLinkNetworking",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/652844839.zip",
            checksum: "03aa454ca04259756f8441d248c8fa544f4c780b8b8a45cbd0cbed2030eabd01"
        ),
        .binaryTarget(
            name: "KMPFaceLinkVMC",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/652844853.zip",
            checksum: "6b013fa8e93ee61de38508cd28ac3555b5a422c24a0cdec7871df979b72434f6"
        ),
        .binaryTarget(
            name: "KMPFaceLinkIFM",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/652844791.zip",
            checksum: "8bd18088eb5c9137efb32ee16689981dc9b4cdef19f9cd57423672d9ba72fbaf"
        ),
        .binaryTarget(
            name: "KMPFaceLinkInteract",
            url: "https://api.github.com/repos/rioX432/KMP-FaceLink/releases/assets/652844795.zip",
            checksum: "871a3175aea2453516b77da8721b1cc0baeabd60b49db201e6eb54467eb08e9e"
        ),
    ]
)
