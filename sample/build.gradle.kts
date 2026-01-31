plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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

tasks.named("preBuild") {
    dependsOn(downloadMediaPipeModel)
}

dependencies {
    implementation(project(":kmp-facelink"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
