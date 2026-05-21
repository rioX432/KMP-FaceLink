plugins {
    `java-gradle-plugin`
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
    id("com.gradle.plugin-publish") version "2.1.1"
}

group = "io.github.rioX432"
version = providers.gradleProperty("VERSION_NAME").getOrElse("0.2.0-SNAPSHOT")

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    explicitApi()
}

gradlePlugin {
    website.set("https://github.com/rioX432/KMP-FaceLink")
    vcsUrl.set("https://github.com/rioX432/KMP-FaceLink")
    plugins {
        create("facelink") {
            id = "io.github.rioX432.facelink"
            implementationClass = "io.github.kmpfacelink.gradle.FaceLinkPlugin"
            displayName = "KMP-FaceLink Gradle Plugin"
            description = "Automates MediaPipe model downloads for KMP-FaceLink face, hand, and body tracking"
            tags.set(listOf("mediapipe", "face-tracking", "vtuber", "kmp", "kotlin-multiplatform"))
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
}
