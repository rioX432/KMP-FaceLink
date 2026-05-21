plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.anotherball.facelink")
}

android {
    namespace = "io.github.kmpfacelink.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.kmpfacelink.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=io.github.kmpfacelink.ExperimentalFaceLinkApi")
    }
}

faceLink {
    faceEnabled.set(true)
    handEnabled.set(true)
    bodyEnabled.set(true)
}

dependencies {
    implementation(project(":sampleShared"))
    implementation(project(":kmp-facelink"))
    implementation(project(":kmp-facelink-avatar"))
    implementation(project(":kmp-facelink-actions"))
    implementation(project(":kmp-facelink-voice"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.kotlinx.coroutines.android)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Instrumented tests
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.mediapipe.tasks.vision)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
