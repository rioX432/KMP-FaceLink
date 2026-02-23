plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val live2dAvailable = rootProject.file("live2d/android/Framework").exists()

android {
    namespace = "io.github.kmpfacelink.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.kmpfacelink.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("boolean", "LIVE2D_AVAILABLE", "$live2dAvailable")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=io.github.kmpfacelink.ExperimentalFaceLinkApi"
    }

}

val downloadMediaPipeModel by tasks.registering {
    val modelDir = layout.projectDirectory.dir("src/main/assets/models")
    val modelFile = modelDir.file("face_landmarker_v2_with_blendshapes.task")

    outputs.file(modelFile)
    doLast {
        modelDir.asFile.mkdirs()
        if (!modelFile.asFile.exists()) {
            val url = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"
            logger.lifecycle("Downloading MediaPipe face landmarker model...")
            ant.invokeMethod("get", mapOf("src" to url, "dest" to modelFile.asFile))
            logger.lifecycle("Model downloaded to ${modelFile.asFile.path}")
        }
    }
}

val downloadHandLandmarkerModel by tasks.registering {
    val modelDir = layout.projectDirectory.dir("src/main/assets/models")
    val modelFile = modelDir.file("hand_landmarker.task")

    outputs.file(modelFile)
    doLast {
        modelDir.asFile.mkdirs()
        if (!modelFile.asFile.exists()) {
            val url = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task"
            logger.lifecycle("Downloading MediaPipe hand landmarker model...")
            ant.invokeMethod("get", mapOf("src" to url, "dest" to modelFile.asFile))
            logger.lifecycle("Model downloaded to ${modelFile.asFile.path}")
        }
    }
}

val downloadPoseLandmarkerModel by tasks.registering {
    val modelDir = layout.projectDirectory.dir("src/main/assets/models")
    val modelFile = modelDir.file("pose_landmarker_lite.task")

    outputs.file(modelFile)
    doLast {
        modelDir.asFile.mkdirs()
        if (!modelFile.asFile.exists()) {
            val url = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"
            logger.lifecycle("Downloading MediaPipe pose landmarker model...")
            ant.invokeMethod("get", mapOf("src" to url, "dest" to modelFile.asFile))
            logger.lifecycle("Model downloaded to ${modelFile.asFile.path}")
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadMediaPipeModel, downloadHandLandmarkerModel, downloadPoseLandmarkerModel)
}

// Exclude Live2D wrapper sources when SDK is not installed
if (!live2dAvailable) {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        exclude("**/live2d/**")
    }
}

dependencies {
    implementation(project(":kmp-facelink"))
    implementation(project(":kmp-facelink-avatar"))
    implementation(project(":kmp-facelink-actions"))
    implementation(project(":kmp-facelink-effects"))
    implementation(project(":kmp-facelink-stream"))
    implementation(project(":kmp-facelink-voice"))
    implementation(project(":kmp-facelink-live2d"))
    implementation(project(":kmp-facelink-rive"))
    implementation(libs.rive.android)
    implementation(libs.androidx.startup.runtime)
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

    // Live2D Cubism SDK (optional, downloaded via scripts/setup-live2d.sh)
    if (live2dAvailable) {
        implementation(libs.live2d.framework)
        val cubismCoreAar = rootProject.file("live2d/android/Core/android/Live2DCubismCore.aar")
        if (cubismCoreAar.exists()) {
            implementation(files(cubismCoreAar))
        }
    }
}
