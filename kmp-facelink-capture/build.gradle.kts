import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
}

dokka {
    moduleName.set("KMP-FaceLink-Capture")
}

kotlin {
    explicitApi()

    compilerOptions {
        // The offline inference API is opt-in because the product never uses it. This module is the
        // measurement harness it was added for.
        freeCompilerArgs.add("-opt-in=io.github.kmpfacelink.ExperimentalFaceLinkApi")
    }

    android {
        namespace = "io.github.kmpfacelink.capture"
        compileSdk = 35
        minSdk = 24
        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-proguard-rules.pro")
            }
        }
        // The raw-frame sink is JVM I/O, so its round-trip test runs on the host rather than
        // needing a device.
        withHostTestBuilder {}
    }

    val xcf = XCFramework("KMPFaceLinkCapture")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "KMPFaceLinkCapture"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kmp-facelink"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            // The take manifest is one declaration shared by the writer, the reader and the offline
            // tooling, so it is parsed rather than pattern-matched: a manifest missing a field, or
            // carrying one this build does not know, has to be refused rather than half-read.
            api(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            // Its own MediaPipe instance rather than the library's live tracker: that one applies
            // smoothing, the blendshape enhancer and the expression correlator before anything sees
            // a result, and a measurement of the estimator taken after those measures the
            // correction. The options used here are recorded in every take's CaptureCondition.
            implementation(libs.mediapipe.tasks.vision)
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)
            implementation(libs.androidx.core)
            implementation(libs.androidx.lifecycle.runtime)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        iosTest.dependencies {
            implementation(libs.kotlin.test)
        }

        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
