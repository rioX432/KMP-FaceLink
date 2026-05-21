import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
}

dokka {
    moduleName.set("KMP-FaceLink-LLM")
}

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()

    android {
        namespace = "io.github.kmpfacelink.llm"
        compileSdk = 35
        minSdk = 24
        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-proguard-rules.pro")
            }
        }
    }

    val xcf = XCFramework("KMPFaceLinkLlm")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "KMPFaceLinkLlm"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kmp-facelink"))
            implementation(project(":kmp-facelink-networking"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
