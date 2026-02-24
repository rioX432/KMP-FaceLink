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

    androidLibrary {
        namespace = "io.github.kmpfacelink.rive"
        compileSdk = 35
        minSdk = 24
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
