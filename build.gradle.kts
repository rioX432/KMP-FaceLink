plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

apiValidation {
    // Exclude sample apps (no public API to track)
    ignoredProjects += listOf("androidApp", "sampleShared", "captureApp")
    // The capture module is the study's measurement harness, not a published artifact (it is absent
    // from publishableModules). Tracking its ABI would only add churn: it has no consumers outside
    // this repository, and its surface moves with the experiment.
    ignoredProjects += listOf("kmp-facelink-capture")
    // Exclude BOM module (no code, only dependency constraints)
    ignoredProjects += listOf("kmp-facelink-bom")

    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}

val publishableModules = setOf(
    "kmp-facelink",
    "kmp-facelink-avatar",
    "kmp-facelink-actions",
    "kmp-facelink-voice",
    "kmp-facelink-llm",
    "kmp-facelink-pipeline",
    "kmp-facelink-networking",
    "kmp-facelink-vmc",
    "kmp-facelink-ifm",
    "kmp-facelink-stream",
    "kmp-facelink-vts",
    "kmp-facelink-interact",
    "kmp-facelink-compose",
    "kmp-facelink-bom",
)

val pomDescriptions = mapOf(
    "kmp-facelink" to "Kotlin Multiplatform face, hand, and body tracking library",
    "kmp-facelink-avatar" to "Live2D parameter mapping for KMP-FaceLink",
    "kmp-facelink-actions" to "Gesture and expression trigger system for KMP-FaceLink",
    "kmp-facelink-voice" to "ASR, TTS, and lip sync for KMP-FaceLink",
    "kmp-facelink-llm" to "LLM streaming API for KMP-FaceLink",
    "kmp-facelink-pipeline" to "AI avatar pipeline orchestrating voice, LLM, and animation for KMP-FaceLink",
    "kmp-facelink-networking" to "Shared UDP socket and time utilities for KMP-FaceLink protocol modules",
    "kmp-facelink-vmc" to "VMC Protocol (OSC over UDP) sender and receiver for KMP-FaceLink",
    "kmp-facelink-ifm" to "iFacialMocap protocol output for KMP-FaceLink",
    "kmp-facelink-stream" to "WebSocket streaming server for KMP-FaceLink phone-as-tracker use case",
    "kmp-facelink-vts" to "VTube Studio Plugin API integration for KMP-FaceLink",
    "kmp-facelink-interact" to "Stream interaction events (chat, gifts, raids) for KMP-FaceLink",
    "kmp-facelink-compose" to "Compose Multiplatform remember helpers for KMP-FaceLink trackers",
    "kmp-facelink-bom" to "Bill of Materials for KMP-FaceLink multi-module version alignment",
)

val detektFormatting = libs.detekt.formatting

detekt {
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    config.setFrom(rootProject.files("detekt.yml"))
}

dependencies {
    detektPlugins(detektFormatting)
    dokka(project(":kmp-facelink"))
    dokka(project(":kmp-facelink-avatar"))
    dokka(project(":kmp-facelink-actions"))
    dokka(project(":kmp-facelink-voice"))
    dokka(project(":kmp-facelink-llm"))
    dokka(project(":kmp-facelink-pipeline"))
    dokka(project(":kmp-facelink-networking"))
    dokka(project(":kmp-facelink-vmc"))
    dokka(project(":kmp-facelink-ifm"))
    dokka(project(":kmp-facelink-stream"))
    dokka(project(":kmp-facelink-vts"))
    dokka(project(":kmp-facelink-interact"))
    dokka(project(":kmp-facelink-compose"))
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    if (name in publishableModules) {
        apply(plugin = "org.jetbrains.kotlinx.kover")
    }

    detekt {
        buildUponDefaultConfig = true
        allRules = false
        parallel = true
        config.setFrom(rootProject.files("detekt.yml"))
    }

    dependencies {
        detektPlugins(detektFormatting)
    }

    if (name in publishableModules) {
        apply(plugin = "maven-publish")

        group = property("GROUP").toString()
        version = property("VERSION_NAME").toString()

        val javadocJar by tasks.registering(Jar::class) {
            archiveClassifier.set("javadoc")
        }

        afterEvaluate {
            extensions.configure<PublishingExtension> {
                repositories {
                    maven {
                        name = "GitHubPackages"
                        url = uri("https://maven.pkg.github.com/anotherball/KMP-FaceLink")
                        credentials {
                            username = (findProperty("gpr.user") as String?)
                                ?: System.getenv("GITHUB_ACTOR")
                            password = (findProperty("gpr.key") as String?)
                                ?: System.getenv("GITHUB_TOKEN")
                        }
                    }
                }
                publications.withType<MavenPublication>().configureEach {
                    artifact(javadocJar)
                    pom {
                        name.set(this@subprojects.name)
                        description.set(pomDescriptions[this@subprojects.name] ?: this@subprojects.name)
                        url.set(property("POM_URL").toString())
                        licenses {
                            license {
                                name.set(property("POM_LICENCE_NAME").toString())
                                url.set(property("POM_LICENCE_URL").toString())
                            }
                        }
                        developers {
                            developer {
                                id.set(property("POM_DEVELOPER_ID").toString())
                                name.set(property("POM_DEVELOPER_NAME").toString())
                            }
                        }
                        scm {
                            url.set(property("POM_SCM_URL").toString())
                            connection.set(property("POM_SCM_CONNECTION").toString())
                            developerConnection.set(property("POM_SCM_DEV_CONNECTION").toString())
                        }
                    }
                }
            }
        }
    }
}

// Runtime provenance records the MediaPipe Tasks version. Keep those literals bound to the
// dependency catalog so a library update cannot silently leave measurement manifests claiming the
// previous engine version.
val verifyMediaPipeVersionProvenance by tasks.registering {
    val catalog = layout.projectDirectory.file("gradle/libs.versions.toml")
    val runtimeSources = listOf(
        layout.projectDirectory.file(
            "kmp-facelink/src/androidMain/kotlin/io/github/kmpfacelink/internal/AndroidTrackingContext.kt",
        ),
        layout.projectDirectory.file(
            "kmp-facelink-capture/src/androidMain/kotlin/io/github/kmpfacelink/capture/AndroidCaptureSession.kt",
        ),
    )
    inputs.file(catalog)
    inputs.files(runtimeSources)
    doLast {
        val version = Regex("(?m)^mediapipe\\s*=\\s*\"([^\"]+)\"")
            .find(catalog.asFile.readText())
            ?.groupValues
            ?.get(1)
            ?: error("mediapipe version is absent from ${catalog.asFile}")
        runtimeSources.forEach { source ->
            check("\"$version\"" in source.asFile.readText()) {
                "${source.asFile} does not record catalog MediaPipe version $version"
            }
        }
    }
}

tasks.named("detekt") {
    dependsOn(verifyMediaPipeVersionProvenance)
}

// Configure detekt source paths for KMP modules
project(":kmp-facelink") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/androidMain/kotlin",
                "src/iosMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-avatar") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-actions") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-voice") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/androidMain/kotlin",
                "src/iosMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-llm") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/androidMain/kotlin",
                "src/iosMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-pipeline") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-networking") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/androidMain/kotlin",
                "src/iosMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-vmc") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/androidMain/kotlin",
                "src/iosMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-ifm") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/androidMain/kotlin",
                "src/iosMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-stream") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-vts") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/commonTest/kotlin",
                "src/jvmTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-interact") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

project(":kmp-facelink-compose") {
    afterEvaluate {
        detekt {
            source.setFrom(
                "src/commonMain/kotlin",
                "src/androidMain/kotlin",
                "src/iosMain/kotlin",
                "src/commonTest/kotlin",
            )
        }
    }
}

// Module dependency graph validation
// Allowed dependencies per module, based on the architecture spec in AGENTS.md
tasks.register("validateModuleDependencies") {
    group = "verification"
    description = "Validate documented, allowed, and actual module dependency graphs"

    val architectureContractFile = providers.gradleProperty("architectureContractFile")
        .map { file(it) }
        .orElse(file("AGENTS.md"))
    inputs.file(architectureContractFile)

    doLast {
        val core = ":kmp-facelink"
        val networking = ":kmp-facelink-networking"

        // Allowed project dependencies for each library module
        val allowedDependencies = mapOf(
            ":kmp-facelink" to emptySet(),
            ":kmp-facelink-avatar" to setOf(core),
            ":kmp-facelink-actions" to setOf(core, ":kmp-facelink-interact"),
            ":kmp-facelink-voice" to setOf(core, networking),
            ":kmp-facelink-llm" to setOf(core, networking),
            ":kmp-facelink-pipeline" to setOf(
                core,
                ":kmp-facelink-voice",
                ":kmp-facelink-llm",
                ":kmp-facelink-actions",
                ":kmp-facelink-avatar",
            ),
            ":kmp-facelink-networking" to emptySet(),
            ":kmp-facelink-vmc" to setOf(core, networking),
            ":kmp-facelink-ifm" to setOf(core, networking),
            ":kmp-facelink-stream" to setOf(core),
            ":kmp-facelink-vts" to setOf(core),
            ":kmp-facelink-interact" to setOf(core),
            ":kmp-facelink-compose" to setOf(core),
            ":kmp-facelink-capture" to setOf(core),
        )

        val architectureText = architectureContractFile.get().readText()
        val dependencyTree = Regex("(?s)### Dependency Tree\\s*```(.*?)```")
            .find(architectureText)
            ?.groupValues
            ?.get(1)
            ?: throw GradleException("AGENTS.md does not contain a Dependency Tree code block")
        val dependencyLine = Regex(
            "^\\s*[├└]──\\s+(kmp-facelink(?:-[a-z]+)?)\\s+→\\s+(.+)$",
        )
        if (!Regex("(?m)^kmp-facelink \\(core\\)$").containsMatchIn(dependencyTree)) {
            throw GradleException("AGENTS.md dependency tree does not declare kmp-facelink (core)")
        }
        val documentedDependencies = mapOf(core to emptySet<String>()) + dependencyTree.lineSequence()
            .mapNotNull { line ->
                val match = dependencyLine.matchEntire(line) ?: return@mapNotNull null
                val modulePath = ":${match.groupValues[1]}"
                val descriptor = match.groupValues[2].substringBefore(" (")
                val dependencies = when {
                    descriptor.startsWith("standalone") -> emptySet()
                    descriptor.startsWith("depends on ") -> descriptor
                        .removePrefix("depends on ")
                        .split(", ")
                        .map { dependency ->
                            if (dependency == "core") core else ":kmp-facelink-$dependency"
                        }
                        .toSet()
                    else -> throw GradleException(
                        "Unrecognized dependency descriptor for $modulePath: $descriptor",
                    )
                }
                modulePath to dependencies
            }
            .toMap()

        if (documentedDependencies != allowedDependencies) {
            val missing = allowedDependencies.keys - documentedDependencies.keys
            val extra = documentedDependencies.keys - allowedDependencies.keys
            val mismatched = allowedDependencies.keys.intersect(documentedDependencies.keys)
                .filter { documentedDependencies[it] != allowedDependencies[it] }
                .associateWith { module ->
                    "documented=${documentedDependencies[module]}, allowed=${allowedDependencies[module]}"
                }
            throw GradleException(
                "AGENTS.md dependency tree differs from the executable allowlist: " +
                    "missing=$missing, extra=$extra, mismatched=$mismatched",
            )
        }

        val violations = mutableListOf<String>()

        allowedDependencies.forEach { (modulePath, allowed) ->
            val moduleProject = project(modulePath)
            val actualDeps = moduleProject.configurations
                .filter { it.name.contains("MainApi") || it.name.contains("MainImplementation") }
                .flatMap { config ->
                    config.dependencies.filterIsInstance<ProjectDependency>().map { ":${it.name}" }
                }
                .toSet()

            val disallowed = actualDeps - allowed
            if (disallowed.isNotEmpty()) {
                violations.add("$modulePath has disallowed dependencies: $disallowed (allowed: $allowed)")
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Module dependency violations found:\n${violations.joinToString("\n") { "  - $it" }}"
            )
        }

        logger.lifecycle(
            "Module dependency validation passed. Documentation, allowlist, and all " +
                "${allowedDependencies.size} modules comply.",
        )
    }
}

val verifySdkPublicSafety by tasks.registering(Exec::class) {
    group = "verification"
    description = "Reject tracked biometric capture payloads and private signing material"
    commandLine("python3", "scripts/verify-sdk-public-safety.py")
}

val testSdkPublicSafety by tasks.registering(Exec::class) {
    group = "verification"
    description = "Mutation controls for the tracked-file privacy gate"
    commandLine("python3", "-m", "unittest", "scripts/test_verify_sdk_public_safety.py")
}

val verifySdkDocumentation by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verify current SDK documentation against source configuration and claim boundaries"
    commandLine("python3", "scripts/verify-sdk-documentation.py")
}

val testSdkDocumentation by tasks.registering(Exec::class) {
    group = "verification"
    description = "Mutation controls for the SDK documentation contract"
    commandLine("python3", "-m", "unittest", "scripts/test_verify_sdk_documentation.py")
}

val testCalibrationTooling by tasks.registering(Exec::class) {
    group = "verification"
    description = "Run source-only paired-calibration parser and metric contract tests"
    commandLine(
        "python3",
        "-m",
        "unittest",
        "tools/calibration/tests/test_evaluate_cheekpuff_geometry.py",
    )
}

val iosAuthorizationTestBinary = layout.buildDirectory.file("verification/ios-run-authorization-tests")

val compileIosRunAuthorizationTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Compile the iOS signed run-authorization mutation controls"
    inputs.files(
        "captureIosApp/FaceLinkCapture/RunAuthorization.swift",
        "captureIosApp/Tests/RunAuthorizationVerifierTests.swift",
    )
    outputs.file(iosAuthorizationTestBinary)
    doFirst {
        iosAuthorizationTestBinary.get().asFile.parentFile.mkdirs()
    }
    commandLine(
        "xcrun",
        "swiftc",
        "-parse-as-library",
        "captureIosApp/FaceLinkCapture/RunAuthorization.swift",
        "captureIosApp/Tests/RunAuthorizationVerifierTests.swift",
        "-o",
        iosAuthorizationTestBinary.get().asFile.absolutePath,
    )
}

val testIosRunAuthorization by tasks.registering(Exec::class) {
    group = "verification"
    description = "Run the iOS signed run-authorization mutation controls"
    dependsOn(compileIosRunAuthorizationTests)
    commandLine(iosAuthorizationTestBinary.get().asFile.absolutePath)
}

val buildCaptureIosSimulator by tasks.registering(Exec::class) {
    group = "verification"
    description = "Build the guided iOS capture app without signing or a physical device"
    val derivedData = layout.buildDirectory.dir("verification/capture-ios-simulator-derived")
    inputs.dir("captureIosApp/FaceLinkCapture")
    inputs.dir("captureIosApp/FaceLinkCapture.xcodeproj")
    inputs.file("captureIosApp/project.yml")
    outputs.dir(derivedData)
    // Xcode's script phase consumes KMP sources outside the declared captureIosApp inputs. Always
    // execute this external build so an incremental local quality run cannot accept a stale app.
    outputs.upToDateWhen { false }
    commandLine(
        "xcodebuild",
        "-project",
        "captureIosApp/FaceLinkCapture.xcodeproj",
        "-scheme",
        "FaceLinkCapture",
        "-sdk",
        "iphonesimulator",
        "-destination",
        "generic/platform=iOS Simulator",
        "-derivedDataPath",
        derivedData.get().asFile.absolutePath,
        "-configuration",
        "Debug",
        "CODE_SIGNING_ALLOWED=NO",
        "build",
    )
}

/**
 * One CI/local entrypoint for the contracts required of KMP-FaceLink as AnotherBall's tracking
 * foundation. Runtime accuracy and device performance remain separate, explicitly authorized
 * protocols; this task covers deterministic source/API/platform/privacy regressions.
 */
tasks.register("sdkQualityCheck") {
    group = "verification"
    description = "Run SDK API, capability, platform, capture, authorization, and privacy gates"
    dependsOn(
        ":kmp-facelink:apiCheck",
        ":kmp-facelink:detekt",
        ":kmp-facelink:jvmTest",
        ":kmp-facelink:compileAndroidMain",
        ":kmp-facelink:compileKotlinIosSimulatorArm64",
        ":kmp-facelink-capture:detekt",
        ":kmp-facelink-capture:testAndroidHostTest",
        ":kmp-facelink-capture:iosSimulatorArm64Test",
        ":kmp-facelink-capture:compileKotlinIosSimulatorArm64",
        ":captureApp:testDebugUnitTest",
        ":captureApp:assembleDebug",
        ":captureApp:assembleDebugAndroidTest",
        ":captureApp:lintDebug",
        buildCaptureIosSimulator,
        testIosRunAuthorization,
        "validateModuleDependencies",
        verifySdkPublicSafety,
        testSdkPublicSafety,
        verifySdkDocumentation,
        testSdkDocumentation,
        testCalibrationTooling,
    )
}
