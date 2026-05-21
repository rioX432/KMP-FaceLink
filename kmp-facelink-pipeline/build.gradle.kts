import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.dokka)
}

dokka {
    moduleName.set("KMP-FaceLink-Pipeline")
}

kotlin {
    explicitApi()

    android {
        namespace = "io.github.kmpfacelink.pipeline"
        compileSdk = 35
        minSdk = 24
        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-proguard-rules.pro")
            }
        }
    }

    val xcf = XCFramework("KMPFaceLinkPipeline")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "KMPFaceLinkPipeline"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kmp-facelink"))
            implementation(project(":kmp-facelink-voice"))
            implementation(project(":kmp-facelink-llm"))
            implementation(project(":kmp-facelink-actions"))
            implementation(project(":kmp-facelink-avatar"))
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
