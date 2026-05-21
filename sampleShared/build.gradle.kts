plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidLibrary {
        namespace = "io.github.kmpfacelink.sample.shared"
        compileSdk = 36
        minSdk = 24
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "SampleShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(project(":kmp-facelink"))
            implementation(project(":kmp-facelink-vmc"))
            implementation(project(":kmp-facelink-ifm"))
            api(project(":kmp-facelink-interact"))
            api(project(":kmp-facelink-actions"))
        }

        androidMain.dependencies {
            implementation(libs.camerax.view)
        }
    }
}
