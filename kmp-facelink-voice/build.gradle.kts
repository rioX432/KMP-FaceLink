import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
}

dokka {
    moduleName.set("KMP-FaceLink-Voice")
}

kotlin {
    explicitApi()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidLibrary {
        namespace = "io.github.kmpfacelink.voice"
        compileSdk = 35
        minSdk = 24
    }

    val whisperDir = rootProject.file("whisper/ios")
    val whisperInclude = whisperDir.resolve("include")

    val xcf = XCFramework("KMPFaceLinkVoice")
    listOf(
        iosArm64() to "device",
        iosSimulatorArm64() to "simulator",
    ).forEach { (target, platform) ->
        target.binaries.framework {
            baseName = "KMPFaceLinkVoice"
            isStatic = true
            xcf.add(this)
        }
        target.compilations.getByName("main") {
            val whisperLib = whisperDir.resolve(platform)
            if (whisperLib.resolve("libwhisper_all.a").exists()) {
                cinterops.create("whisper") {
                    definitionFile.set(project.file("src/iosMain/interop/whisper.def"))
                    includeDirs(whisperInclude)
                    extraOpts("-libraryPath", whisperLib.absolutePath)
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kmp-facelink"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
