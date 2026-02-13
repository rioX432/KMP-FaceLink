pluginManagement {
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
include(":kmp-facelink-effects")
include(":androidApp")
