import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dokka)
}

dokka {
    moduleName.set("KMP-FaceLink-Compose")
}

kotlin {
    explicitApi()

    jvm()

    android {
        namespace = "io.github.kmpfacelink.compose"
        compileSdk = 35
        minSdk = 24
    }

    val xcf = XCFramework("KMPFaceLinkCompose")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "KMPFaceLinkCompose"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kmp-facelink"))
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(libs.lifecycle.runtime.compose.multiplatform)
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
