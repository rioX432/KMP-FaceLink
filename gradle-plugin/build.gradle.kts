plugins {
    `java-gradle-plugin`
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
}

group = "com.anotherball"
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
    plugins {
        create("facelink") {
            id = "com.anotherball.facelink"
            implementationClass = "io.github.kmpfacelink.gradle.FaceLinkPlugin"
            displayName = "KMP-FaceLink Gradle Plugin"
            description = "Automates MediaPipe model downloads for KMP-FaceLink face, hand, and body tracking"
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
}
