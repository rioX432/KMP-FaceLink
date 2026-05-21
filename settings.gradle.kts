pluginManagement {
    includeBuild("gradle-plugin")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KMP-FaceLink"

include(":kmp-facelink")
include(":kmp-facelink-avatar")
include(":kmp-facelink-actions")
include(":kmp-facelink-voice")
include(":kmp-facelink-llm")
include(":kmp-facelink-pipeline")
include(":kmp-facelink-networking")
include(":kmp-facelink-vmc")
include(":kmp-facelink-ifm")
include(":kmp-facelink-stream")
include(":kmp-facelink-vts")
include(":kmp-facelink-interact")
include(":kmp-facelink-compose")
include(":kmp-facelink-bom")
include(":sampleShared")
include(":androidApp")
