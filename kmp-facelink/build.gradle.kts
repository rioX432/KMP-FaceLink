import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.skie)
    alias(libs.plugins.dokka)
}

dokka {
    moduleName.set("KMP-FaceLink")
}

kotlin {
    explicitApi()
    compilerOptions {
        // expect/actual classes are an intentional public KMP boundary in this module.
        freeCompilerArgs.add("-Xexpect-actual-classes")
        // Keep compatibility accessors available, but prevent new source warnings in CI.
        allWarningsAsErrors.set(true)
    }

    jvm {
        compilerOptions {
            // Keep the public JVM artifact readable by the ABI validator even when Gradle runs on a newer JDK.
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        compilerOptions {
            // This target's MediaPipe bridge is implemented through Kotlin/Wasm JS interop.
            optIn.add("kotlin.js.ExperimentalWasmJsInterop")
        }
        browser()
    }

    android {
        namespace = "io.github.kmpfacelink"
        compileSdk = 35
        minSdk = 24
        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-proguard-rules.pro")
            }
        }
        withHostTestBuilder {}.configure {
            // android.util.Log is called from HolisticTrackerConfig's init path
            // (FaceLinkLogger); host-JVM unit tests need no-op stubs for it.
            isReturnDefaultValues = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    val xcf = XCFramework("KMPFaceLink")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "KMPFaceLink"
            isStatic = true
            xcf.add(this)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val nonMobileMain by creating {
            dependsOn(commonMain.get())
        }

        jvmMain {
            dependsOn(nonMobileMain)
        }

        val wasmJsMain by getting {
            dependsOn(nonMobileMain)
        }

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.mediapipe.tasks.vision)
            implementation(libs.litert)
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.androidx.core)
            implementation(libs.androidx.lifecycle.runtime)
        }

        jvmMain.dependencies {
            implementation(libs.onnxruntime)
            implementation(libs.javacv)
            implementation(libs.openblas)
            implementation(libs.opencv)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmTest.dependencies {
            // Parses the shared cbridge conformance fixture (JsonElement API only, no serialization plugin needed).
            implementation(libs.kotlinx.serialization.json)
        }

        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit)
            implementation(libs.robolectric)
        }

        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.junit)
            implementation(libs.androidx.test.core)
        }
    }
}

// Expose the shared cross-repo conformance fixture (kmp-facelink-cbridge/conformance/) to
// LiveLinkConformanceFixtureTest so the golden file and LiveLinkBlendShapes cannot drift.
// The fixture is declared as a task input so editing it re-runs the test (no stale cache hit).
val standardJvmTest = tasks.named<Test>("jvmTest") {
    val conformanceFixture = rootProject.layout.projectDirectory
        .file("kmp-facelink-cbridge/conformance/live_link_conformance.json")
    inputs.file(conformanceFixture)
        .withPropertyName("liveLinkConformanceFixture")
        .withPathSensitivity(PathSensitivity.NONE)
    systemProperty("kmpfacelink.conformance.fixture", conformanceFixture.asFile.absolutePath)
    // Private biometric replay is a separately authorized local quality lane. It must not appear
    // as a green test case in the source-only PR gate.
    filter.excludeTestsMatching("*CheekPuffContourFrozenRuleTest*")
}

tasks.register<Test>("privateCheekPuffQualityCheck") {
    group = "verification"
    description = "Replay the frozen cheek-puff rule against locally authorized private inputs"
    dependsOn("jvmTestClasses")
    testClassesDirs = standardJvmTest.get().testClassesDirs
    classpath = standardJvmTest.get().classpath
    filter.includeTestsMatching("*CheekPuffContourFrozenRuleTest*")
    shouldRunAfter(standardJvmTest)
}
