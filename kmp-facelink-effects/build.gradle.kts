import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.dokka)
}

dokka {
    moduleName.set("KMP-FaceLink-Effects")
}

kotlin {
    explicitApi()

    androidLibrary {
        namespace = "io.github.kmpfacelink.effects"
        compileSdk = 35
        minSdk = 24
    }

    val xcf = XCFramework("KMPFaceLinkEffects")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "KMPFaceLinkEffects"
            isStatic = true
            xcf.add(this)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-opt-in=io.github.kmpfacelink.ExperimentalFaceLinkApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kmp-facelink"))
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
