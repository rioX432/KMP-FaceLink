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

// Live2D CubismJavaFramework (optional, downloaded via scripts/setup-live2d.sh)
val live2dFrameworkDir = file("live2d/android/Framework")
if (live2dFrameworkDir.exists()) {
    includeBuild(live2dFrameworkDir) {
        dependencySubstitution {
            substitute(module("com.live2d.sdk.cubism:framework"))
                .using(project(":framework"))
        }
    }
}

include(":kmp-facelink")
include(":kmp-facelink-avatar")
include(":kmp-facelink-actions")
include(":kmp-facelink-effects")
include(":kmp-facelink-live2d")
include(":androidApp")
