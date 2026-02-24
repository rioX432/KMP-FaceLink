plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.dokka)
}

dokka {
    moduleName.set("KMP-FaceLink-Rive")
}

kotlin {
    explicitApi()

    compilerOptions {
        freeCompilerArgs.add("-opt-in=io.github.kmpfacelink.ExperimentalFaceLinkApi")
    }

    androidLibrary {
        namespace = "io.github.kmpfacelink.rive"
        compileSdk = 35
        minSdk = 24
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "KMPFaceLinkRive"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kmp-facelink"))
            implementation(libs.kotlinx.coroutines.core)
        }

        androidMain.dependencies {
            implementation(libs.rive.android)
            implementation(libs.androidx.startup.runtime)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
