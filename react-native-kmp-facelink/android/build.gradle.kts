plugins {
    id("com.android.library")
}

android {
    namespace = "com.kmpfacelink.rn"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].java.srcDirs("src/main/java")
}

dependencies {
    // React Native
    implementation("com.facebook.react:react-android:+")

    // KMP-FaceLink AAR — in production this comes from Maven Central.
    // For local development, use the project dependency or a local AAR.
    // implementation(files("libs/kmp-facelink-release.aar"))
    implementation("io.github.kmpfacelink:kmp-facelink:+")

    // Coroutines for Flow collection
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // AndroidX Lifecycle for NoOpLifecycleOwner
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
}
