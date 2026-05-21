plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
}

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
            id = "io.github.rioX432.facelink"
            implementationClass = "io.github.kmpfacelink.gradle.FaceLinkPlugin"
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
}
