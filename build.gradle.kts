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
    alias(libs.plugins.nexus.publish)
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(providers.environmentVariable("OSSRH_USERNAME"))
            password.set(providers.environmentVariable("OSSRH_PASSWORD"))
        }
    }
}

apiValidation {
    // Exclude sample apps (no public API to track)
    ignoredProjects += listOf("androidApp", "sampleShared")
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
        apply(plugin = "signing")

        group = property("GROUP").toString()
        version = property("VERSION_NAME").toString()

        val javadocJar by tasks.registering(Jar::class) {
            archiveClassifier.set("javadoc")
        }

        afterEvaluate {
            extensions.configure<PublishingExtension> {
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

            extensions.configure<SigningExtension> {
                val signingKeyId = providers.environmentVariable("GPG_KEY_ID").orNull
                val signingKey = providers.environmentVariable("GPG_KEY").orNull
                val signingPassword = providers.environmentVariable("GPG_PASSPHRASE").orNull
                if (signingKeyId != null) {
                    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
                    sign(extensions.getByType<PublishingExtension>().publications)
                }
            }
        }
    }
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

// Module dependency graph validation
// Allowed dependencies per module, based on the architecture spec in AGENTS.md
tasks.register("validateModuleDependencies") {
    group = "verification"
    description = "Validate module dependency graph against architecture rules"

    doLast {
        val core = ":kmp-facelink"
        val networking = ":kmp-facelink-networking"

        // Allowed project dependencies for each library module
        val allowedDependencies = mapOf(
            ":kmp-facelink" to emptySet(),
            ":kmp-facelink-avatar" to setOf(core),
            ":kmp-facelink-actions" to setOf(core),
            ":kmp-facelink-voice" to setOf(core),
            ":kmp-facelink-llm" to setOf(core),
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
        )

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

        logger.lifecycle("Module dependency validation passed. All ${allowedDependencies.size} modules comply.")
    }
}
